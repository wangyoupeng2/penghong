package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private ShopTypeMapper shopTypeMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public List<ShopType> queryType() {
        //首先获取到缓存中的信息
        String shoptype = stringRedisTemplate.opsForValue().get("cache:shop:" + "shoptype");
        //缓存中如果有就直接返回
        if(StrUtil.isNotBlank(shoptype)){
            List<ShopType> list = JSONUtil.toList(shoptype, ShopType.class);
            return list;
        }
        //如果没有就把数据库里面的都保存到redis里面再返回
        QueryWrapper<ShopType> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("sort");
        List<ShopType> shopTypeList = shopTypeMapper.selectList(queryWrapper);

        if(shopTypeList.isEmpty()){
            return null;
        }
        String shoptypejsonStr = JSONUtil.toJsonStr(shopTypeList);
        stringRedisTemplate.opsForValue().set("cache:shop:" + "shoptype",shoptypejsonStr);
        //判断不在的条件是啥呢。数据库中的数据
        return shopTypeList;
    }
}
