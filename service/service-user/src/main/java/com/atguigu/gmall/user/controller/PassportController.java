package com.atguigu.gmall.user.controller;

import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.service.UserService;
import com.baomidou.mybatisplus.extension.api.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/user/passport")
public class PassportController {
    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate redisTemplate;


    @PostMapping("login")
    public Result login(@RequestBody UserInfo userInfo){
        UserInfo login = userService.login(userInfo);
        if(login != null){

            HashMap<String, Object> hashMap = new HashMap<>();
            //根据sso的分析过程  用户登陆之后的信息 放入缓存
            String token = UUID.randomUUID().toString().replace("-","");
            hashMap.put("token",token);
            hashMap.put("nickName",login.getNickName());
            String userKey = RedisConst.USER_LOGIN_KEY_PREFIX+token;
            redisTemplate.opsForValue().set(userKey, login.getId().toString(), RedisConst.USERKEY_TIMEOUT, TimeUnit.SECONDS);

            return Result.ok(hashMap);
        }else {
            return Result.fail().message("用户名或密码不正确");
        }
    }
    //推出登录
    @GetMapping("logout")
    public Result logout(HttpServletRequest request){
        String token = request.getHeader("token");
        //删除缓存中的数据
        redisTemplate.delete(RedisConst.USER_LOGIN_KEY_PREFIX+token);
        return Result.ok();
    }
}
