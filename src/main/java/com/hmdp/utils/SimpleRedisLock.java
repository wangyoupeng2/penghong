package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import com.fasterxml.jackson.databind.ser.std.StdKeySerializers;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @Author：yep
 * @Project：hm-dianping
 * @name：SimpleRedisLock
 * @Date：2024/3/22 21:00
 * @Filename：SimpleRedisLock
 */
public class SimpleRedisLock implements ILock{
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX ="lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    //将lua脚本默认导入
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程ID
        //因为要对Id进行检查，但是因为每个JVM都会有线程ID初始值，所以就不能用啦,要接上一个UUID
        String id = ID_PREFIX + Thread.currentThread().getId();
        //使用setnx语句
        Boolean absent = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, "thread" + id, timeoutSec, TimeUnit.SECONDS);
        //拆箱Boolean模式，防止空指针问题
        return BooleanUtil.isTrue(absent);
    }

    @Override
    public void unLock() {
        //调用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
               ID_PREFIX + Thread.currentThread().getId());
        //实现原子性，将释放锁的认证与释放锁通过lua脚本实现同步
    }

/*    @Override
    public void unLock() {
        String threadID = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        String id = ID_PREFIX + Thread.currentThread().getId();
        //获取到的锁ID和线程ID相同，才可以释放锁
        if(id.equals(threadID)) stringRedisTemplate.delete(KEY_PREFIX + name);
    }*/


}
