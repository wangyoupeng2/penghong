package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.*;
import io.netty.channel.ChannelDuplexHandler;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private RedisInWork redisInWork;
    @Resource
    private IVoucherService iVoucherService;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    //将lua脚本默认导入
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //阻塞队列
    private BlockingQueue<VoucherOrder> orderTask =new ArrayBlockingQueue<>(1024 *1024);
    //单线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    //代理对象，需要在使得create函数从this变为拥有事物能力的函数
    private static IVoucherOrderService proxy ;

    //在类初始化后就进行阻塞队列的内容获取进行线程
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    //内部类线程，死循环获取数据创建订单
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while(true){
                try {
                    //获取订单数据
                    VoucherOrder order = orderTask.take();
                    //创建订单
                    HandleVoucherOrder(order);
                } catch (Exception e) {
                    log.error("订单错误",e);
                }
            }
        }
    }


    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //运行lua脚本获取到结果
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        //根据结果进行判断，是否具有资格
        int r =result.intValue();
        if(r != 0){
            return Result.fail( r==1?"库存不足":"不能重复下单");
        }
        //代理对象使用全局变量，因为如果在内部线程中是获取不了的
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        long order = redisInWork.nextID("order");
        voucherOrder.setId(order);
        voucherOrder.setUserId(UserHolder.getUser().getId());

        //拥有资格后将order输入到阻塞队列中
        orderTask.add(voucherOrder);

        return Result.ok(order);
/*        //查询ID
        Voucher voucher = iVoucherService.getById(voucherId);
        Long userID = UserHolder.getUser().getId();
        if (LocalDateTime.now().isBefore(voucher.getBeginTime())) {
            return Result.fail("秒杀未开始");
        }
        if (LocalDateTime.now().isAfter(voucher.getEndTime())) {
            return Result.fail("秒杀已结束");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        //原本互斥锁使用方法，有可能在没有提交的时候就又被获取到锁,因此将锁加在这里
        *//**
         * 集群模式下，相同的用户ID是不会被锁锁住的
         * 集群模式下，对于锁的监听器是有两个的，但是每个监听器只能观测自己JVM下的程序运行
         * 所以需要使用一个统一的多集群的监听器，分布式锁！！！
         * Mysql redis zookeeper，使用redis的setnx作为分布式锁
         *//*

        //创建锁对象
//        SimpleRedisLock lock = new SimpleRedisLock(userID + "order", stringRedisTemplate);

        //使用redissonh26625h52sa9dj2ak5d9klaj2d5lkaj96dl1a4klsd9ajldj小红
        //redisson实现分布式锁，redisClient获取锁
        RLock lock = redissonClient.getLock(userID + ":lock:order");
        //获取锁
        boolean islock = lock.tryLock();

        if (!islock) {
            //错误是一般是返回错误和重试
            return Result.fail("单一用户只允许一张");
        }


        //下面代码已经淘汰哩，是单机的悲观锁，上面是分布式锁
        //synchronized (userID.toString().intern())
            //实现一人一单,因为同时存在并发问题，但是有无法进行条件判断更新，所以只能使用悲观锁

            *//*return createVoucherOrder(voucherId);*//*
            //出现一个问题，因为事务管理加在这个函数上
            //但是因为该函数调用是使用this指向，没有事物能力，所以需要寻找代理对象调用函数

        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId ,userID);
        } finally {
            lock.unlock();
        }
        //获取事务代理对象，使用代理对象调用，函数要在Serive里面声明*/
    }

    private void HandleVoucherOrder(VoucherOrder voucherOrder){
        Long userId = voucherOrder.getUserId();
        //只能这样获取userid，因为是子线程
        RLock lock = redissonClient.getLock(userId + ":lock:order");
        //获取锁
        boolean islock = lock.tryLock();
        if (!islock) {
            //错误是一般是返回错误和重试
            log.error("不可重复性下单");
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }
    @Transactional
    public  void createVoucherOrder(VoucherOrder voucherOrder) {
        //使用悲观锁，将每一个userid作为锁，只有同一个用户来才加锁，所以锁加在了userID，不在函数上
/*      1  Long userID = UserHolder.getUser().getId();*/
/*      2  synchronized (userID.toString().intern())*/
        //userId,tostring.intern在线程池中寻找字符串对应的对象
        Long voucherId = voucherOrder.getVoucherId();
        Integer count = this.query().eq("voucher_id", voucherId).eq("user_id", voucherOrder.getUserId()).count();
            if (count > 0) {
                log.error("库存不足");
                return;
            }
            //如果是，判断库存，库存-1，返回订单账号，创建订单
            boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    //CAS乐观锁，对stock进行version判断，如果和最开始数据不同就不更新
                    /*.eq("stock",voucher.getStock()).update();*/

                    //但是使用乐观锁时，条件太谨慎，会导致大多数人同时进入时失败，
                    //因此只需要条件库存大于0就可以了
                    .gt("stock", 0).update();
            if (!success) {
                log.error("库存不足");
                return;
            }
            save(voucherOrder);
        }
}
