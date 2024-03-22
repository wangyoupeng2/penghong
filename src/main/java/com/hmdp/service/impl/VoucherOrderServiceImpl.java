package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisInWork;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private RedisInWork redisInWork;
    @Resource
    private IVoucherService iVoucherService;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Override

    public Result seckillVoucher(Long voucherId) {
        //查询ID
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
        //原本的锁使用方法，有可能在没有提交的时候就又被获取到锁,因此将锁加在这里
        synchronized (userID.toString().intern()) {
            //实现一人一单,因为同时存在并发问题，但是有无法进行条件判断更新，所以只能使用悲观锁

            /*return createVoucherOrder(voucherId);*/
            //出现一个问题，因为事务管理加在这个函数上
            //但是因为该函数调用是使用this指向，没有事物能力，所以需要寻找代理对象调用函数

            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId ,userID);
            //获取事务代理对象，使用代理对象调用，函数要在Serive里面声明
        }
    }

    @Transactional
    public  Result createVoucherOrder(Long voucherId,Long userID) {
        //使用悲观锁，将每一个userid作为锁，只有同一个用户来才加锁，所以锁加在了userID，不在函数上
/*      1  Long userID = UserHolder.getUser().getId();*/
/*      2  synchronized (userID.toString().intern())*/
        //userId,tostring.intern在线程池中寻找字符串对应的对象
            Integer count = this.query().eq("voucher_id", voucherId).eq("user_id", userID).count();
            if (count > 0) {
                return Result.fail("单一用户只允许一张");
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
                return Result.fail("库存不足");
            }
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setVoucherId(voucherId);
            voucherOrder.setId(redisInWork.nextID("order"));
            voucherOrder.setUserId(UserHolder.getUser().getId());
            save(voucherOrder);

            //返回订单id
            return Result.ok(voucherOrder.getId());
        }
}
