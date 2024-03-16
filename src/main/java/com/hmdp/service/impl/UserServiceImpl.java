package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.HashUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone,HttpSession session) {
        //判断电话号码合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("非法电话号码！");
        }
        //获取验证码
        String randomNumbers = RandomUtil.randomNumbers(6);
        //保存到redis中
        /*session.setAttribute("code",randomNumbers);*/
        stringRedisTemplate.opsForValue().set("login:code:"+phone,randomNumbers,2, TimeUnit.MINUTES);
        log.info("验证码为：{}",randomNumbers);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        //手机号码合法
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("非法电话号码！");
        }
        //验证码合法
        String code = stringRedisTemplate.opsForValue().get("login:code:"+ phone);
        String co = loginForm.getCode();
        if(code == null || !code.equals(co))
        {
            return Result.fail("验证码错误");
        }
        //根据手机查询用户
        User user = query().eq("phone", phone).one();
        //注册用户或者直接返回
        if(user == null){
            user = creatUserWithPhone(phone);
        }
        UserDTO dto = new UserDTO();
        BeanUtils.copyProperties(user,dto);
        //生成token
        String token = UUID.randomUUID().toString(true);
        //user转为Hash,设置copeoption，设置所有对象为string
        Map<String, Object> userMap = BeanUtil.beanToMap(dto, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        /* session.setAttribute("user",dto);*/
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        //user信息传输到redis中并且设置有效时间

        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);

        stringRedisTemplate.expire(tokenKey,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("wuser"+RandomUtil.randomString(5));
        save(user);
        return user;
    }
}
