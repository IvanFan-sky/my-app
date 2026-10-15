package com.example.myapp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
// 注意包名：MyBatis-Plus 3.5.9 起 ServiceImpl 从 extension.service.impl 迁到了 spring.service.impl
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.myapp.entity.User;
import com.example.myapp.mapper.UserMapper;
import com.example.myapp.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public Page<User> pageByUsername(String username, long current, long size) {
        // like 的第一个参数是条件：关键字为空时不拼这段 where，等于查全部
        return page(new Page<>(current, size),
                Wrappers.<User>lambdaQuery()
                        .like(StringUtils.hasText(username), User::getUsername, username)
                        .orderByDesc(User::getId));
    }
}
