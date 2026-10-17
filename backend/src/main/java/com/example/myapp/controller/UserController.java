package com.example.myapp.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.myapp.cache.UserCache;
import com.example.myapp.common.ApiResponse;
import com.example.myapp.common.BizException;
import com.example.myapp.common.ErrorCode;
import com.example.myapp.common.UserRole;
import com.example.myapp.dto.UserForm;
import com.example.myapp.dto.UserStatsVO;
import com.example.myapp.dto.UserVO;
import com.example.myapp.entity.User;
import com.example.myapp.security.CurrentUser;
import com.example.myapp.security.LoginUser;
import com.example.myapp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
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
 *
 * <p>整组接口都要求登录（见 WebMvcConfig 的拦截器注册），因为这里能读到全部用户资料。
 *
 * <p><b>缓存失效是这一层最容易漏的地方</b>：下面每个写操作（create / update / delete）
 * 都必须调 {@code userCache.evict(id)}。漏掉的后果不是「数据有点旧」，而是
 * {@code AuthInterceptor} 会从缓存里读到已删除的用户、继续放行其旧令牌 —— 那是权限漏洞。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserCache userCache;

    /** GET /api/users?keyword=ab&current=1&size=10 */
    @GetMapping
    public ApiResponse<Page<UserVO>> page(@RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(userService.pageByKeyword(keyword, current, size));
    }

    /**
     * GET /api/users/stats —— 概览统计。
     *
     * <p>刻意放在 {@code /{id}} 之前：Spring MVC 的路径匹配虽然能区分字面量与变量，
     * 但把固定路径写在变量路径前面更直观，也避免将来有人把 {id} 改成正则时误伤。
     */
    @GetMapping("/stats")
    public ApiResponse<UserStatsVO> stats() {
        UserStatsVO stats = new UserStatsVO(
                userService.count(),
                userCache.hitCount(),
                userCache.missCount(),
                userCache.hitRate());
        return ApiResponse.ok(stats);
    }

    /** GET /api/users/{id} —— 走 Redis 缓存 */
    @GetMapping("/{id}")
    public ApiResponse<UserVO> get(@PathVariable Long id) {
        UserVO vo = userService.getUserVoById(id);
        if (vo == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在: " + id);
        }
        return ApiResponse.ok(vo);
    }

    /**
     * POST /api/users
     *
     * <p>新增时必须给密码，否则建出来的账号无法登录。这个「条件必填」的规则
     * 注解表达不了（@NotBlank 会让修改接口留空改密也失败），所以在方法体里显式校验。
     */
    @PostMapping
    public ApiResponse<UserVO> create(@Valid @RequestBody UserForm form) {
        if (!StringUtils.hasText(form.getPassword())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新增用户时密码不能为空");
        }
        User user = new User();
        applyForm(user, form);
        // 新增时按传入角色，未传则用最小权限的默认角色（USER）
        user.setRole(parseRole(form.getRole(), UserRole.DEFAULT_ROLE));
        userService.assignPassword(user, form.getPassword());
        userService.save(user);
        // 新增时这个 id 还不可能有缓存，但仍统一失效一次：
        // 依赖「新增不可能命中」这种推理，比无条件失效更容易在改动后失效
        userCache.evict(user.getId());
        // 必须重新读一次：createdAt / updatedAt 由数据库的 DEFAULT CURRENT_TIMESTAMP 生成，
        // 刚 save 完的实体上这两列还是 null，直接返回会让前端拿到空时间（实测过）
        return ApiResponse.ok(UserVO.from(userService.getById(user.getId())));
    }

    /** PUT /api/users/{id} —— password 留空表示不修改密码 */
    @PutMapping("/{id}")
    public ApiResponse<UserVO> update(@PathVariable Long id,
                                      @Valid @RequestBody UserForm form,
                                      @LoginUser CurrentUser currentUser) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在: " + id);
        }

        // 角色：未传表示不改；传了必须是白名单内的值
        if (StringUtils.hasText(form.getRole())) {
            // 不允许改自己的角色。否则唯一的管理员把自己降级后，
            // 就再没有任何账号能进用户管理了（只能去数据库里手动修）。
            if (currentUser != null && currentUser.id().equals(id)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "不能修改自己的角色");
            }
            user.setRole(parseRole(form.getRole(), user.getRole()));
        }

        // 用 overwriteEditable 而不是 updateById：后者会把 null 字段排除在 SET 之外，
        // 导致管理员「清空邮箱/昵称」时实际什么都没改（MyBatis-Plus 的 NOT_NULL 策略）
        String hash = userService.encodePassword(form.getPassword());
        userService.overwriteEditable(id,
                StringUtils.hasText(form.getNickname()) ? form.getNickname().trim() : null,
                StringUtils.hasText(form.getEmail()) ? form.getEmail().trim() : null,
                StringUtils.hasText(form.getBio()) ? form.getBio().trim() : null,
                form.getAge(),
                hash);

        // 角色单独更新：它的语义是「不传就不改」，与上面「不传就清空」正好相反，
        // 混在同一个方法里会让调用者分不清，所以显式分开
        if (StringUtils.hasText(form.getRole())) {
            userService.update(Wrappers.<User>lambdaUpdate()
                    .eq(User::getId, id)
                    .set(User::getRole, user.getRole()));
        }
        // 必须先失效再回源，否则下一次读会拿到旧缓存
        userCache.evict(id);
        // 同理重新读一次：updated_at 由数据库的 ON UPDATE 维护，
        // 内存里的还是改之前的值，直接返回会让前端以为「没保存成功」
        return ApiResponse.ok(UserVO.from(userService.getById(id)));
    }

    /** DELETE /api/users/{id} —— 逻辑删除，只把 deleted 置 1 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @LoginUser CurrentUser currentUser) {
        // 不允许删自己：删掉后当前令牌立刻失效（拦截器查不到用户），会被直接踢回登录页。
        // 前端已禁用按钮，这里是后端兜底 —— 前端限制不能当安全边界。
        if (currentUser != null && currentUser.id().equals(id)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能删除自己的账号");
        }
        userService.removeById(id);
        // 这行不能省：不删缓存的话，AuthInterceptor 会从缓存读到「用户还存在」，
        // 已删除用户的旧令牌在有效期内仍能访问接口
        userCache.evict(id);
        return ApiResponse.ok();
    }

    /**
     * 把表单里的可写字段搬到实体上。
     *
     * <p>注意这里**没有** password 与 deleted：密码由 assignPassword 专门处理
     * （要做哈希和长度校验），deleted 由逻辑删除机制维护。字段少搬一个，
     * 就少一条「客户端能改不该改的字段」的漏洞。
     */
    private static void applyForm(User user, UserForm form) {
        user.setUsername(form.getUsername());
        user.setNickname(StringUtils.hasText(form.getNickname()) ? form.getNickname().trim() : null);
        user.setEmail(form.getEmail());
        user.setBio(StringUtils.hasText(form.getBio()) ? form.getBio().trim() : null);
        user.setAge(form.getAge());
    }

    /**
     * 校验并转换角色。
     *
     * <p>必须做白名单校验：role 来自请求体，不校验就等于允许往数据库写任意字符串，
     * 例如将来某处用 {@code role = 'admin'} 做小写比较，就会被 {@code ADMIN} 之类绕出问题。
     *
     * @param raw      请求体里的角色，可为空
     * @param fallback 为空时的取值（新增用默认角色，修改用原角色）
     */
    private static String parseRole(String raw, String fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        String role = raw.trim();
        if (!UserRole.isValid(role)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "角色只能是 ADMIN 或 USER");
        }
        return role;
    }
}
