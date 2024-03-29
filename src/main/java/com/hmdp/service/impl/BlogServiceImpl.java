package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollRedult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired private IUserService userService;

    @Autowired
    private IFollowService followService;
    @Override
    public Result quertHotBlog(Integer current) {
         // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            this.BlogToUser(blog);
            this.isBloglike(blog);}
        );
        return Result.ok(records);
    }

    private void BlogToUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryBlogByid(Long id) {
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("笔记不存在");
        }
        BlogToUser(blog);
        return Result.ok(blog);
    }

    private void isBloglike(Blog blog) {
        Long id =blog.getId();
        Long userId =blog.getUserId();
        Double member = stringRedisTemplate.opsForZSet().score(
                BLOG_LIKED_KEY + id, userId.toString());
        blog.setIsLike(member != null);
    }

    @Override
    public Result likeBlog(Long id) {
        Blog blog = getById(id);
        Long userId = blog.getUserId();
        Double islike = stringRedisTemplate.opsForZSet().score
                (BLOG_LIKED_KEY + id, userId.toString());
        if (islike != null) {
            stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id, userId.toString());
            update().setSql("like = like - 1").eq("id",id).update();
        }else{
            stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id, userId.toString(),System.currentTimeMillis());
            update().setSql("like = like + 1").eq("id",id).update();
        }
        return Result.ok();
    }

    @Override
    public Result queryBloglike(Long id) {
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String join = StrUtil.join(",", ids);

        List<UserDTO> userList = userService.query()
                .in("id",ids)
                .last("order by field(id" + join + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userList);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean save = save(blog);
        if(!save){
            return Result.fail("新增笔记失败");
        }else{
            List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
            for(Follow follow:follows){
                Long userId = follow.getUserId();
                String key = FEED_KEY +userId;
                stringRedisTemplate.opsForZSet().add(key,blog.getId() .toString(),System.currentTimeMillis());
            }
        }// 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogofFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> blogSet = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if(blogSet == null ||blogSet.isEmpty()){
            return Result.ok();
        }
        //获取到blog的id列表
        List<Long> ids = new ArrayList<>(blogSet.size());
        //获取到最小时间戳，更新max
        long minTime = 0;
        //获取到在range中的相同的时间戳，使得offset+1
        int os = 1;
        for (ZSetOperations.TypedTuple<String> blog : blogSet) {
            ids.add(Long.valueOf(blog.getValue()));
            long time = blog.getScore().longValue();
            if(time == minTime){
                os++;
            }else {
                minTime = time;
                os = 1;
            }
        }
        String idStr = StrUtil.join(",", ids);
        //获取blog列表，并且避免in导致顺序错误
        List<Blog> blogs = query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list();
        //对于一个blog来说需要用户查看是否点过赞，因此要实现blog数据完整
        blogs.forEach(blog->{
            BlogToUser(blog);
            isBloglike(blog);
        });
        //获取到此时offset
        ScrollRedult redult = ScrollRedult.builder()
                .list(blogs)
                .offset(os)
                .mintime(minTime)
                .build();
        return Result.ok(redult);
    }
}
