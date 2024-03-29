package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;
    @Override
    public Result updateFollow(Long id, Boolean isFollow) {
        Long userID = UserHolder.getUser().getId();
        Follow follow = getById(id);
        String key ="follows:" +userID;
        if(follow == null && BooleanUtil.isTrue(isFollow)){
            follow = Follow.builder()
                    .userId(userID)
                    .followUserId(id)
                    .createTime(LocalDateTime.now())
                    .build();
            boolean save = save(follow);
            if (save) {
                stringRedisTemplate.opsForSet().add(key,id.toString());
            }
        }else if(follow != null && !BooleanUtil.isTrue(isFollow)){
            boolean remove = remove(new QueryWrapper<Follow>()
                    .eq("follow_user_id", id).eq("user_id", userID));
            if (remove) {
                stringRedisTemplate.opsForSet().remove(key,id.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result IsFollow(Long id) {
        Long userID = UserHolder.getUser().getId();
        Integer count = query().eq("follow_user_id", id).eq("user_id", userID).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommon(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key  = "follows:" +userId;
        String followkey = "follows:" +id;
        Set<String> common = stringRedisTemplate.opsForSet().intersect(key, followkey);
        if(common == null || common.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = common.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userList = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userList);
    }
}
