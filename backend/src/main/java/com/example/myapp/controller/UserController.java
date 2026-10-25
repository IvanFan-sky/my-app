package com.example.myapp.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.myapp.cache.UserCache;
import com.example.myapp.common.ApiResponse;
import com.example.myapp.common.BizException;
import com.example.myapp.common.AuditAction;
import com.example.myapp.common.ErrorCode;
import com.example.myapp.common.UserRole;
import com.example.myapp.dto.UserBatchForm;
import com.example.myapp.dto.UserForm;
import com.example.myapp.dto.UserStatsVO;
import com.example.myapp.dto.UserVO;
import com.example.myapp.entity.User;
import com.example.myapp.security.CurrentUser;
import com.example.myapp.security.LoginUser;
import com.example.myapp.security.RequireRole;
import com.example.myapp.service.AuditLogService;
import com.example.myapp.service.UserService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
// 整组只对管理员开放。这样声明在端点旁边，读 Controller 就知道谁能访问，
// 不需要去配置文件里找哪条路径挂了哪个拦截器。
@RequireRole(UserRole.ADMIN)
public class UserController {

    private final UserService userService;
    private final UserCache userCache;
    private final AuditLogService auditLogService;

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
    public ApiResponse<UserVO> create(@Valid @RequestBody UserForm form,
                                      @LoginUser CurrentUser currentUser) {
        if (!StringUtils.hasText(form.getPassword())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新增用户时密码不能为空");
        }
        User user = new User();
        applyForm(user, form);
        // 新增时按传入角色，未传则用最小权限的默认角色（USER）
        user.setRole(parseRole(form.getRole(), UserRole.DEFAULT_ROLE));
        userService.assignPassword(user, form.getPassword());
        try {
            userService.save(user);
        } catch (DuplicateKeyException e) {
            /*
             * 用户名唯一键冲突。
             *
             * 这里**不能只靠事先查重**（本方法也没有查重）：@TableLogic 让查询自动
             * 带上 deleted = 0，于是「已被逻辑删除的同名用户」查不出来，却仍然占着
             * uk_sys_user_username —— 只有唯一索引能兜住。
             *
             * 不捕获的话异常会落到 GlobalExceptionHandler 的兜底分支变成 5000
             * 「服务器内部错误」，管理员会去翻服务端日志找问题，而其实只要换个用户名。
             * 与 AuthServiceImpl.register 的处理保持一致。
             */
            log.info("新增用户失败：用户名唯一键冲突 username={}", form.getUsername());
            throw new BizException(ErrorCode.CONFLICT, "用户名已被占用，请换一个");
        }
        // 新增时这个 id 还不可能有缓存，但仍统一失效一次：
        // 依赖「新增不可能命中」这种推理，比无条件失效更容易在改动后失效
        userCache.evict(user.getId());
        User created = userService.getById(user.getId());
        // 操作人必须传当前登录用户：审计要回答的是「谁把谁怎么了」，
        // 只记 target 会让这条记录在追责时价值减半
        auditLogService.record(AuditAction.CREATE_USER, currentUser,
                created.getId(), created.getUsername(),
                Map.of("role", String.valueOf(created.getRole())), true);
        // 必须重新读一次：createdAt / updatedAt 由数据库的 DEFAULT CURRENT_TIMESTAMP 生成，
        // 刚 save 完的实体上这两列还是 null，直接返回会让前端拿到空时间（实测过）
        return ApiResponse.ok(UserVO.from(created));
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

        // 先记下库里的原始角色。下面会给 user.setRole(...) 赋新值，
        // 若等到那时再比较「新旧是否相同」就永远相等，版本号也就不会被提升。
        String previousRole = user.getRole();

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
            boolean roleChanged = !user.getRole().equals(previousRole);
            userService.update(Wrappers.<User>lambdaUpdate()
                    .eq(User::getId, id)
                    .set(User::getRole, user.getRole()));
            if (roleChanged) {
                // 角色变了，旧令牌里那份权限断言就过时了（比如从 ADMIN 降成 USER，
                // 但令牌里仍是 ADMIN 被缓存的角色拦不住）。作废它的全部令牌强制重登。
                userService.bumpTokenVersion(id);
            }
        }
        // 必须先失效再回源，否则下一次读会拿到旧缓存
        userCache.evict(id);
        User updated = userService.getById(id);

        // 审计：只记「哪些字段被改」，role 记旧→新（角色不是敏感信息，记出来才查得清越权）
        Map<String, Object> changed = new LinkedHashMap<>();
        if (!java.util.Objects.equals(user.getNickname(), updated.getNickname())) {
            changed.put("nickname", "changed");
        }
        if (!java.util.Objects.equals(user.getEmail(), updated.getEmail())) {
            changed.put("email", "changed");
        }
        if (!java.util.Objects.equals(user.getBio(), updated.getBio())) {
            changed.put("bio", "changed");
        }
        if (!java.util.Objects.equals(user.getAge(), updated.getAge())) {
            changed.put("age", "changed");
        }
        if (!java.util.Objects.equals(previousRole, updated.getRole())) {
            changed.put("role", previousRole + " -> " + updated.getRole());
        }
        if (hash != null) {
            // 重置密码：只记「做过」，绝不记值
            changed.put("password", "reset");
        }
        auditLogService.record(AuditAction.UPDATE_USER, currentUser,
                updated.getId(), updated.getUsername(), changed, true);

        // 同理重新读一次：updated_at 由数据库的 ON UPDATE 维护，
        // 内存里的还是改之前的值，直接返回会让前端以为「没保存成功」
        return ApiResponse.ok(UserVO.from(updated));
    }

    /** DELETE /api/users/{id} —— 逻辑删除，只把 deleted 置 1 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @LoginUser CurrentUser currentUser) {
        // 不允许删自己：删掉后当前令牌立刻失效（拦截器查不到用户），会被直接踢回登录页。
        // 前端已禁用按钮，这里是后端兜底 —— 前端限制不能当安全边界。
        if (currentUser != null && currentUser.id().equals(id)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能删除自己的账号");
        }
        User target = userService.getById(id);
        userService.removeById(id);
        // 这行不能省：不删缓存的话，AuthInterceptor 会从缓存读到「用户还存在」，
        // 已删除用户的旧令牌在有效期内仍能访问接口
        userCache.evict(id);
        auditLogService.record(AuditAction.DELETE_USER, currentUser,
                id, target == null ? null : target.getUsername(), null, true);
        return ApiResponse.ok();
    }

    /**
     * POST /api/users/batch-delete —— 批量逻辑删除。
     *
     * <p>逐条删除而不是一条 `UPDATE ... WHERE id IN (...)`：
     * 单条写法的 SQL 更短，但**缓存失效需要知道每一条的 id**，
     * 而这个接口最容易漏的正是缓存（漏了等于已删用户仍能用旧令牌访问）。
     * 用 listByIds 拿到真实存在的行，逐条 removeById + evict，路径与单删完全一致。
     *
     * <p>只返回实际删除的条数：ids 里可能有已被删除或不存在的 id，
     * 直接回传入数量会让前端以为全删成功了。
     */
    @PostMapping("/batch-delete")
    public ApiResponse<Integer> batchDelete(@Valid @RequestBody UserBatchForm form,
                                            @LoginUser CurrentUser currentUser) {
        List<Long> ids = form.getIds().stream().filter(java.util.Objects::nonNull).distinct().toList();

        // 与单删一致：不允许把自己删掉，否则当前登录立刻失效
        if (currentUser != null && ids.contains(currentUser.id())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能删除自己的账号，请从选择中移除");
        }

        // 先查出真实存在的行：一是为了审计能记下用户名，二是避免对不存在的 id 白忙
        List<User> targets = userService.listByIds(ids);
        for (User target : targets) {
            userService.removeById(target.getId());
            userCache.evict(target.getId());
        }

        int deleted = targets.size();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("requested", ids.size());
        detail.put("deleted", deleted);
        // 用户名列表截断到 20 个，避免 detail 超长被截断成半截 JSON
        detail.put("usernames", targets.stream().map(User::getUsername).limit(20).toList());
        auditLogService.record(AuditAction.BATCH_DELETE, currentUser, null, null, detail, true);

        return ApiResponse.ok(deleted);
    }

    /**
     * GET /api/users/export?keyword= —— 导出 CSV。
     *
     * <p>为什么不走「前端拿 JSON 自己拼 CSV」：导出的目的通常是要 Excel 打开，
     * 而 Excel 对 UTF-8 的识别依赖 **BOM**，前端拼容易漏掉；另外上万行数据
     * 全塞进浏览器内存也不合适。这里由服务端直接写流。
     *
     * <p>上限 5000 行：再大就不该用同步导出（应该走异步任务 + 下载链接），
     * 否则一次导出可能把数据库连接和内存占住。超出时静默截断而不是报错 ——
     * 对使用者来说「拿到前 5000 条」比「什么都没有」有用；文件名里会带行数以便发现。
     */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String keyword,
                       HttpServletResponse response) throws IOException {
        // 复用与列表完全相同的查询：导出的结果必须和界面上看到的筛选结果一致
        List<UserVO> rows = userService.pageByKeyword(keyword, 1, EXPORT_MAX_ROWS).getRecords();

        String filename = "users-" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename + "\"");
        // 告诉浏览器这是个下载，且不要让中间层按 JSON 处理
        response.setHeader("X-Content-Type-Options", "nosniff");

        var writer = response.getWriter();
        // UTF-8 BOM：Excel 靠它识别编码，否则中文会显示成乱码
        writer.write('\uFEFF');

        writer.write("ID,用户名,显示名,角色,邮箱,年龄,创建时间,更新时间\r\n");
        for (UserVO row : rows) {
            writer.write(String.join(",",
                    csvCell(row.getId() == null ? null : String.valueOf(row.getId())),
                    csvCell(row.getUsername()),
                    csvCell(row.getNickname()),
                    csvCell(row.getRole()),
                    csvCell(row.getEmail()),
                    csvCell(row.getAge() == null ? null : String.valueOf(row.getAge())),
                    csvCell(formatTime(row.getCreatedAt())),
                    csvCell(formatTime(row.getUpdatedAt()))));
            writer.write("\r\n");
        }
        writer.flush();
    }

    /** 导出上限。见 export 方法里的说明 */
    private static final int EXPORT_MAX_ROWS = 5000;

    /**
     * CSV 单元格转义。
     *
     * <p>三个必须处理的字符：逗号（列分隔）、双引号（值里的引号要翻倍）、换行。
     * 不做的话，「a,b」这样的邮箱或昵称会直接把列错位 —— 而错位的表格
     * 看起来「有数据」，比报错更难发现。
     *
     * <p>另外防一下 CSV 注入：以 = + - @ 开头的值会被 Excel 当公式执行，
     * 前面补一个单引号让它按文本处理。
     */
    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String formatTime(java.time.LocalDateTime time) {
        return time == null ? null : time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
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
