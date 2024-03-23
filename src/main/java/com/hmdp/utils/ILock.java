package com.hmdp.utils;

import org.springframework.stereotype.Component;

/**
 * @Author：yep
 * @Project：hm-dianping
 * @name：ILock
 * @Date：2024/3/22 20:58
 * @Filename：ILock、
 * redis分布式锁简单实现
 */
public interface ILock {
    /**
     * 尝试获取锁，返回true则获取成功，false为失败
     * 使用得到非阻塞模式，因此需要有ttl，时间到了自动释放锁
     * @param timeoutSec
     * @return
     */
    boolean tryLock(long timeoutSec);

    void unLock();
}
