package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.Cache;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private Cache cache;

    private static final ExecutorService CACHE = Executors.newFixedThreadPool(10);
    String cacheKey="Cache:code:";
    @Override
    public Result CachegetById(Long id) {
        //解决缓存穿透
        /*   Shop shop = queryWithPass(id);*/
        //互斥锁解决缓存击穿
        /*   Shop shop = queryWithMutex(id);*/
        Shop shop = cache.queryWithPass(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        return Result.ok(shop);
    }


    private void saveShop2Reids(Long id ,Long expireSecond) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(202);
        RedisData redisData =new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecond));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id , JSONUtil.toJsonStr(redisData));

    }
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不存在");
        }
        //更新数据库
        updateById(shop);
        stringRedisTemplate.delete(cacheKey + id);
        return Result.ok();
    }
}
