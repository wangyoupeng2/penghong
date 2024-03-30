package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.Cache;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1是否根据坐标判断
        if(x == null ||y ==null){
            Page<Shop> page = query().eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page);
        }
        String key ="SHOP:TYPE:"+ typeId;
        //2分页查询，计算分页参数
        int from = (current-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end =current*SystemConstants.DEFAULT_PAGE_SIZE;
        //3查询redis，根据距离排序shopID，distance
        //此时没有办法使用GEO的search函数进行分页查询
        GeoResults<RedisGeoCommands.GeoLocation<String>> search = stringRedisTemplate.opsForGeo()
                .search(key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        //4解析ID，查询shop
        if(search == null){
            return Result.ok();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = search.getContent();
        if(list.size() < from){
            //没有下一页,就不会进行下面的skip，否则会出现空语句
            return Result.ok(Collections.emptyList());
        }
        //获取店铺id创建集合
        List<Long > ids = new ArrayList<>();
        //根据返回结构创建map保存id和distance，保存id与distance的关系
        Map<String,Distance> distanceMap = new HashMap<>(list.size());
        //截取from到end

        list.stream().skip(from).forEach(result ->{
            String shopID = result.getContent().getName();
            ids.add(Long.valueOf(shopID));
            Distance distance = result.getDistance();
            distanceMap.put(shopID,distance);
        });
        String Str = StrUtil.join(",", ids);
        //获取按照距离大小排序的店铺列表
        List<Shop> shoplist = query().in("id", ids)
                .last("ORDER BY FIELD(id," + Str + ")")
                .list();
        for (Shop shop : shoplist) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shoplist);
    }
}
