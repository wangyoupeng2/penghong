package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.StringTokenizer;

/**
 * @Author：yep
 * @Project：hm-dianping
 * @name：RedisInWork
 * @Date：2024/3/22 15:07
 * @Filename：RedisInWork
 */
@Component
public class RedisInWork {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int  COUNT_BITS =32;

    public RedisInWork(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate=stringRedisTemplate;
    }
    public long nextID(String keyprefix){
        //获取时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //获取系列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long inr = stringRedisTemplate.opsForValue().increment("irc:" + keyprefix + ":" + date);

        return timestamp<<COUNT_BITS |inr;
    }
}
