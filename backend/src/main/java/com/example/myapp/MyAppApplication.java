package com.example.myapp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * my-app 后端启动类。
 *
 * <p>@MapperScan 扫描 Mapper 接口所在包；没有它 MyBatis-Plus 不会注册这些接口。
 * 如果以后拆多模块，把包名改宽或加多个 @MapperScan。
 */
@SpringBootApplication
@MapperScan("com.example.myapp.mapper")
public class MyAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyAppApplication.class, args);
    }
}
