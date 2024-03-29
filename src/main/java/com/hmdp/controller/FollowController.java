package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
   private IFollowService followService;
    @PutMapping("/{id}/{isFollow}")
    public Result updateFollow(@PathVariable Long id,@PathVariable Boolean isFollow){
        return followService.updateFollow(id,isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result IsFollow(@PathVariable Long id){
        return followService.IsFollow(id);
    }

    @GetMapping("/common/{id}")
    public  Result followCommon(@PathVariable Long id){
        return followService.followCommon(id);
    }
}
