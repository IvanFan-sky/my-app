package com.example.myapp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
// 注意包名：MyBatis-Plus 3.5.9 起 ServiceImpl 从 extension.service.impl 迁到了 spring.service.impl
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.myapp.common.BizException;
import com.example.myapp.common.ErrorCode;
import com.example.myapp.dto.UserVO;
import com.example.myapp.entity.User;
import com.example.myapp.mapper.UserMapper;
import com.example.myapp.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /** BCrypt 只使用明文的前 72 字节，更长的部分会被静默忽略（见 assignPassword） */
    private static final int BCRYPT_MAX_BYTES = 72;

    private static final int PASSWORD_MIN_LENGTH = 6;

    private final PasswordEncoder passwordEncoder;

    @Override
    public Page<UserVO> pageByUsername(String username, long current, long size) {
        // like 的第一个参数是条件：关键字为空时不拼这段 where，等于查全部
        Page<User> page = page(new Page<>(current, size),
                Wrappers.<User>lambdaQuery()
                        .like(StringUtils.hasText(username), User::getUsername, username)
                        .orderByDesc(User::getId));

        // 转成 VO：Page 的 records 类型要跟着换，分页元信息（total/current/size）原样保留
        Page<UserVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(UserVO::from).toList());
        return result;
    }

    @Override
    public void assignPassword(User user, String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            // 修改场景下留空 = 不改密码，保持原哈希不动
            return;
        }
        String password = rawPassword.trim();
        if (password.length() < PASSWORD_MIN_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码长度不能少于 " + PASSWORD_MIN_LENGTH + " 个字符");
        }
        // BCrypt 以字节计长，且超过 72 字节的部分会被静默截断。
        // 中文字符占 3 字节，因此这里必须按 UTF-8 字节数判断，不能按字符数。
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "密码过长（按 UTF-8 编码不得超过 " + BCRYPT_MAX_BYTES + " 字节，中文约 24 个字）");
        }
        user.setPasswordHash(passwordEncoder.encode(password));
    }
}
