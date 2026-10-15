package com.example.myapp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.myapp.common.ApiResponse;
import com.example.myapp.common.BizException;
import com.example.myapp.entity.User;
import com.example.myapp.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User 的 REST 接口，路径前缀 /api/users。
 *
 * <p>统一返回 ApiResponse；业务失败抛 BizException，由 GlobalExceptionHandler 兜住。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** GET /api/users?username=ab&current=1&size=10 */
    @GetMapping
    public ApiResponse<Page<User>> page(@RequestParam(required = false) String username,
                                       @RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(userService.pageByUsername(username, current, size));
    }

    /** GET /api/users/{id} */
    @GetMapping("/{id}")
    public ApiResponse<User> get(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BizException(4004, "用户不存在: " + id);
        }
        return ApiResponse.ok(user);
    }

    /** POST /api/users */
    @PostMapping
    public ApiResponse<User> create(@Valid @RequestBody UserForm form) {
        User user = new User();
        applyForm(user, form);
        userService.save(user);
        return ApiResponse.ok(user);
    }

    /** PUT /api/users/{id} */
    @PutMapping("/{id}")
    public ApiResponse<User> update(@PathVariable Long id, @Valid @RequestBody UserForm form) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BizException(4004, "用户不存在: " + id);
        }
        applyForm(user, form);
        userService.updateById(user);
        return ApiResponse.ok(user);
    }

    /** DELETE /api/users/{id} —— 逻辑删除，只把 deleted 置 1 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.removeById(id);
        return ApiResponse.ok();
    }

    private static void applyForm(User user, UserForm form) {
        user.setUsername(form.getUsername());
        user.setEmail(form.getEmail());
        user.setAge(form.getAge());
    }

    /**
     * 请求体。刻意与实体分开：实体字段由数据库决定，表单字段由接口契约决定，
     * 两者混用会让「客户端能改 createdAt」这类漏洞悄悄出现。
     */
    @Data
    public static class UserForm {

        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名最长 64 个字符")
        private String username;

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱最长 128 个字符")
        private String email;

        @Min(value = 0, message = "年龄不能小于 0")
        @Max(value = 200, message = "年龄不能大于 200")
        private Integer age;
    }
}
