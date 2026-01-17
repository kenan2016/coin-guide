package com.exchange.simple.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exchange.simple.entity.User; // 需自行创建 Entity
import com.exchange.simple.mapper.UserMapper; // 需自行创建 Mapper
import com.exchange.simple.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired private UserMapper userMapper;

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password) {
        // 1. 查库比对密码
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        
        if (user == null || !user.getPassword().equals(password)) {
            return "登录失败：账号或密码错误";
        }

        // 2. 生成 Token
        String token = JwtUtils.generateToken(user.getId());
        
        // 返回给前端，前端需保存这个 token
        return token;
    }
}