package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author：yep
 * @Project：hm-dianping
 * @name：RedissonConfig
 * @Date：2024/3/23 15:14
 * @Filename：RedissonConfig
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setPassword("1224");
        return Redisson.create(config);
    }
}
