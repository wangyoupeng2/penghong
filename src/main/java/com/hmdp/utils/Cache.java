package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * @Author：yep
 * @Project：hm-dianping
 * @name：Cache
 * @Date：2024/3/22 14:04
 * @Filename：Cache
 */
@Component
public class Cache {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE = Executors.newFixedThreadPool(10);

    public Cache(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate=stringRedisTemplate;
    }
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogical(String key,Object value,Long time,TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPass(String Key , ID id , Class<R> type, Function<ID,R> dbquery,Long time,TimeUnit unit){
        //从redis中获取信息0
        String Cache = stringRedisTemplate.opsForValue().get(Key + id);
        //如果有就直接返回
        if(StrUtil.isNotBlank(Cache) ){
            return JSONUtil.toBean(Cache, type);
        }
        //判断命中是否为""
        if (Cache != null) {
            return null;
        }
        //如果没有就进入数据库中查询，之后保存在缓存
        R r = dbquery.apply(id);
        if (r == null) {
            stringRedisTemplate.opsForValue().set(Key + id,"" ,RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        this.set(Key+id,r,time,unit);
        //然后返回
        return r;
    }

    public <R,ID> R queryWithLogicalExpire(String key,ID id,Class<R> type,Function<ID,R> dbquery,Long time,TimeUnit unit){

        //从redis中获取信息
        String code = stringRedisTemplate.opsForValue().get(key + id);
        //如果没有就直接返回
        if(StrUtil.isBlank(code) ){
            return null;
        }
        //如果没有就进入数据库中查询，之后保存在缓存

        RedisData redisData = JSONUtil.toBean(code, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            return r;
        }
        if (tryLock(key  + id)) {
            CACHE.submit(() -> {
                try {
                    R r1 = dbquery.apply(id);
                    Thread.sleep(202);
                    this.setWithLogical(key,r1,time,unit);

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(key + id);
                }
            });
        }
        //然后返回
        return r;
    }

    private boolean tryLock(String key){
        //这个地方时Boolean并不是boolean，如果直接返回可能在拆箱的时候出现空指针
        Boolean absent = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(absent);
    }
    private boolean unLock(String Key){
        Boolean deleted = stringRedisTemplate.delete(Key);
        return BooleanUtil.isTrue(deleted);
    }

}
