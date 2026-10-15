package com.example.myapp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.myapp.entity.User;

/**
 * User 的数据访问接口。
 *
 * <p>继承 BaseMapper 即可获得单表 CRUD；复杂 SQL 写在
 * resources/mapper/UserMapper.xml，或在此加 @Select 注解方法。
 *
 * <p>这里不需要 @Mapper 注解，因为启动类上已有 @MapperScan("com.example.myapp.mapper")。
 */
public interface UserMapper extends BaseMapper<User> {
}
