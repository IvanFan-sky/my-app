package com.example.myapp.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
// 注意包名：MyBatis-Plus 3.5.9 起 ServiceImpl 从 extension.service.impl 迁到了 spring.service.impl
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.myapp.cache.UserCache;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /** BCrypt 只使用明文的前 72 字节，更长的部分会被静默忽略（见 assignPassword） */
    private static final int BCRYPT_MAX_BYTES = 72;

    private static final int PASSWORD_MIN_LENGTH = 6;

    private final PasswordEncoder passwordEncoder;
    private final UserCache userCache;

    @Override
    public Page<UserVO> pageByKeyword(String keyword, long current, long size) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;

        Page<User> page = page(new Page<>(current, size),
                Wrappers.<User>lambdaQuery()
                        // 用户名或邮箱任一命中即可。
                        // 说明：sys_user 只有 username 的唯一索引和 created_at 的普通索引，
                        // **email 上没有索引**，所以这块 like 会走全表扫描。
                        // 当前数据量下可以接受；等用户量上来了应当给 email 建索引，
                        // 或改用前缀匹配（like 'kw%'，能用上索引）。这里是有意取舍，不是没想到。
                        .and(kw != null, w -> w
                                .like(User::getUsername, kw)
                                .or()
                                .like(User::getEmail, kw))
                        .orderByDesc(User::getId));

        // 转成 VO：Page 的 records 类型要跟着换，分页元信息（total/current/size）原样保留
        Page<UserVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(UserVO::from).toList());
        return result;
    }

    @Override
    public UserVO getUserVoById(Long id) {
        if (id == null) {
            return null;
        }
        // cache-aside：先查缓存，未命中再回源并回填
        Optional<UserVO> cached = userCache.get(id);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 回源。注意这里用 getById：@TableLogic 会自动带上 deleted = 0，
        // 所以已被逻辑删除的用户在这里拿到的是 null —— 也就不会被重新写进缓存
        User user = getById(id);
        if (user == null) {
            return null;
        }
        UserVO vo = UserVO.from(user);
        userCache.put(vo);
        return vo;
    }

    @Override
    public void assignPassword(User user, String rawPassword) {
        String hash = encodePassword(rawPassword);
        if (hash != null) {
            user.setPasswordHash(hash);
        }
    }

    @Override
    public String encodePassword(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            // 修改场景下留空 = 不改密码
            return null;
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
        return passwordEncoder.encode(password);
    }

    @Override
    public void overwriteEditable(Long id, String nickname, String email, String bio, Integer age,
                                  String passwordHash) {
        // 显式 set 每一列：null 也会被写成 NULL。
        // 这正是与 updateById 的关键差异 —— 后者会跳过 null 字段，
        // 导致「清空昵称」变成「不改昵称」。
        LambdaUpdateWrapper<User> wrapper = Wrappers.<User>lambdaUpdate()
                .eq(User::getId, id)
                .set(User::getNickname, nickname)
                .set(User::getEmail, email)
                .set(User::getBio, bio)
                .set(User::getAge, age);

        // 密码是唯一「null 表示不改」的字段：不清空密码是刻意的，
        // 否则一次不带密码的资料保存就会把账号锁死。
        if (passwordHash != null) {
            wrapper.set(User::getPasswordHash, passwordHash);
        }
        update(wrapper);
    }
}
