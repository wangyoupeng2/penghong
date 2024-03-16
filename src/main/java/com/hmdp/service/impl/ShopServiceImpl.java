package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

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
    @Override
    public Result CachegetById(Long id) {
        String cacheKey="Cache:code:";
        //从redis中获取信息
        String Cache = stringRedisTemplate.opsForValue().get(cacheKey + id);
        //如果有就直接返回
        if(StrUtil.isNotBlank(Cache)){
            Shop shopCache = JSONUtil.toBean(Cache, Shop.class);
            return Result.ok(shopCache);
        }
        //如果没有就进入数据库中查询，之后保存在缓存
        Shop shop = getById(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        String shopjson = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(cacheKey + id,shopjson,30, TimeUnit.MINUTES);
        //然后返回
        return Result.ok(shop);
    }
}
