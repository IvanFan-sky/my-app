package com.example.myapp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
// 注意包名：MyBatis-Plus 3.5.9 起 IService 从 extension.service 迁到了 spring.service
import com.baomidou.mybatisplus.spring.service.IService;
import com.example.myapp.entity.User;

/**
 * User 业务接口。
 *
 * <p>继承 IService 后自带 save / getById / updateById / removeById / list / count 等方法，
 * 不需要在实现类里逐个转发。只在这里声明额外需要的业务方法。
 */
public interface UserService extends IService<User> {

    /**
     * 按用户名模糊分页查询。
     *
     * @param username 关键字，为空则查全部
     * @param current  页码，从 1 开始
     * @param size     每页条数
     */
    Page<User> pageByUsername(String username, long current, long size);
}
