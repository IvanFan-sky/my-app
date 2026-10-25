package com.example.myapp.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.myapp.cache.UserCache;
import com.example.myapp.common.ApiResponse;
import com.example.myapp.common.BizException;
import com.example.myapp.common.AuditAction;
import com.example.myapp.common.CsvUtils;
import com.example.myapp.common.ErrorCode;
import com.example.myapp.common.PageQuery;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
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
        // 收敛成 1~100：size 直接来自请求参数，不限制就等于开放全表扫描
        PageQuery pageQuery = PageQuery.of(current, size);
        return ApiResponse.ok(
                userService.pageByKeyword(keyword, pageQuery.current(), pageQuery.size()));
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
                // 走 10 秒缓存：工作台每次进入都会调它，没必要每次 COUNT(*)
                userService.countUsersForStats(),
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

        // 原始角色：审计要记「旧 -> 新」，存一份再改
        String previousRole = user.getRole();

        /*
         * 角色：未传表示不改；传了必须是白名单内的值。
         *
         * 值没变时**不写这一列**，也不作废旧令牌 —— 管理员点一次「保存」
         * 不该把对方所有设备踢下线。所以这里算出「确实要改的新角色」，
         * 没变就留 null 交给 updateManaged 当作「不改」。
         */
        String newRole = null;
        if (StringUtils.hasText(form.getRole())) {
            // 不允许改自己的角色。否则唯一的管理员把自己降级后，
            // 就再没有任何账号能进用户管理了（只能去数据库里手动修）。
            if (currentUser != null && currentUser.id().equals(id)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "不能修改自己的角色");
            }
            String parsed = parseRole(form.getRole(), previousRole);
            if (!parsed.equals(previousRole)) {
                newRole = parsed;
            }
        }

        /*
         * 一次 UPDATE 写完：可编辑字段 + 密码 + 角色，版本号最多 +1。
         *
         * 不能用 updateById（NOT_NULL 策略会跳过 null，导致「清空邮箱」变成「不改邮箱」）；
         * 也不能拆成「overwriteEditable + 改角色」两条 —— 那会让同时改密码和角色时
         * token_version 一次跳 2。
         */
        String hash = userService.encodePassword(form.getPassword());
        userService.updateManaged(id,
                StringUtils.hasText(form.getNickname()) ? form.getNickname().trim() : null,
                StringUtils.hasText(form.getEmail()) ? form.getEmail().trim() : null,
                StringUtils.hasText(form.getBio()) ? form.getBio().trim() : null,
                form.getAge(),
                hash,
                newRole);

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
        if (target == null) {
            /*
             * 不存在（或已被别人删掉）时返回 404，而不是「删除成功」。
             *
             * 静默成功有两个害处：并发删除时后者会以为是自己删的；
             * 前端传错 id 也永远不会被发现。审计里的 targetUsername 也会是 null。
             */
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在: " + id);
        }
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

        // 先查出真实存在的行：一是为了审计能记下用户名，二是避免对不存在的 id 白忙。
        // 只取 id 与 username 两列 —— 把整行查出来（含 password_hash）纯属浪费
        List<User> targets = userService.list(Wrappers.<User>lambdaQuery()
                .select(User::getId, User::getUsername)
                .in(User::getId, ids));

        int deleted = targets.size();
        if (deleted > 0) {
            List<Long> targetIds = targets.stream().map(User::getId).toList();
            // 一条 UPDATE ... WHERE id IN (...)（逻辑删除即 SET deleted = 1），
            // 而不是 N 次 removeById；缓存同理，合并成一次 DEL
            userService.removeByIds(targetIds);
            userCache.evictAll(targetIds);
        }
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
     * 对使用者来说「拿到前 5000 条」比「什么都没有」有用。
     *
     * <p><b>为什么用 {@link StreamingResponseBody} 而不是直接 getWriter()</b>：
     * 前者是「分批查一批、写一批」，内存峰值只有一批（{@value #EXPORT_BATCH_SIZE} 条）：
     * 后者要先把 5000 行全部读进 List，峰值是 5000 个 UserVO。
     * 两个必须知道的限制：
     * <ul>
     *   <li>流式响应在独立线程执行，**中途抛异常无法再转成 JSON 错误体**
     *       （响应已提交），客户端只会拿到一个截断的文件。所以只把
     *       「查询 + 写行」放进流里，其余逻辑（文件名、响应头）都在外面，
     *       参数错误仍能正常返回。</li>
     *   <li>每批查询都关掉 count（{@code countTotal=false}）：导出不需要总数，
     *       否则 10 批就是 10 次 {@code COUNT(*)}。</li>
     * </ul>
     */
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(required = false) String keyword) {

        String filename = "users-" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";

        StreamingResponseBody body = out -> {
            Writer writer = new BufferedWriter(
                    new OutputStreamWriter(out, StandardCharsets.UTF_8));
            // UTF-8 BOM：Excel 靠它识别编码，否则中文会显示成乱码
            writer.write('\uFEFF');
            writer.write("ID,用户名,显示名,角色,邮箱,年龄,创建时间,更新时间\r\n");

            int written = 0;
            long page = 1;
            while (written < EXPORT_MAX_ROWS) {
                int batch = Math.min(EXPORT_BATCH_SIZE, EXPORT_MAX_ROWS - written);
                // 复用与列表相同的查询：导出的结果必须和界面上看到的一致
                List<UserVO> rows = userService
                        .pageByKeyword(keyword, page, batch, false)
                        .getRecords();
                if (rows.isEmpty()) {
                    break;
                }
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
                written += rows.size();
                page++;
                // 这一批没取满，说明已经到底了，不用再查一次空页
                if (rows.size() < batch) {
                    break;
                }
            }
            writer.flush();
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                // 告诉浏览器这是个下载，且不要让中间层按 JSON 处理
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    /** 导出上限。见 export 方法里的说明 */
    private static final int EXPORT_MAX_ROWS = 5000;

    /** 每批读取行数：决定流式导出的内存峰值 */
    private static final int EXPORT_BATCH_SIZE = 500;

    /**
     * CSV 单元格转义已抽到 {@link CsvUtils#cell(String)}。
     *
     * <p>抽出去的理由是可测：它是纯字符串函数，又是防公式注入的安全逻辑，
     * 留在 Controller 里只能靠「启 Spring + 发一次导出」间接验证，
     * 而这类分支（引号翻倍、换行包裹、公式前缀）恰好最需要逐条断言。
     */
    private static String csvCell(String value) {
        return CsvUtils.cell(value);
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
