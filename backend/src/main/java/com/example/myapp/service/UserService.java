package com.example.myapp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
// 注意包名：MyBatis-Plus 3.5.9 起 IService 从 extension.service 迁到了 spring.service
import com.baomidou.mybatisplus.spring.service.IService;
import com.example.myapp.dto.UserVO;
import com.example.myapp.entity.User;

/**
 * User 业务接口。
 *
 * <p>继承 IService 后自带 save / getById / updateById / removeById / list / count 等方法，
 * 不需要在实现类里逐个转发。只在这里声明额外需要的业务方法。
 *
 * <p>注意泛型参数是 {@link User}（实体），但对外暴露的方法返回值是
 * {@link UserVO}。这是有意的：实体带着 passwordHash，不该跨出 Service 边界。
 */
public interface UserService extends IService<User> {

    /**
     * 按用户名模糊分页查询。
     *
     * @param username 关键字，为空则查全部
     * @param current  页码，从 1 开始
     * @param size     每页条数
     */
    Page<UserVO> pageByUsername(String username, long current, long size);

    /**
     * 把表单里的明文密码加密后写进实体的 passwordHash。
     *
     * <p>密码相关的逻辑全部收在 Service，Controller 只负责组装表单字段。
     *
     * @param user        目标实体
     * @param rawPassword 明文密码；为 null / 空白时表示「不改密码」，直接返回
     */
    void assignPassword(User user, String rawPassword);
}
