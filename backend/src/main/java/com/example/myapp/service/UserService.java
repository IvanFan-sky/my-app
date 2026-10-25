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
 *
 * <p><b>读路径分两种，别用错</b>：
 * <ul>
 *   <li>{@link #getById}（继承来的）—— 直连数据库，返回带 passwordHash 的实体。
 *       只有认证流程（校验口令）该用它。</li>
 *   <li>{@link #getUserVoById} —— 走 Redis 缓存，返回对外可见的 VO。
 *       接口层、鉴权拦截器用这个。</li>
 * </ul>
 */
public interface UserService extends IService<User> {

    /**
     * 按关键字（用户名 / 邮箱）模糊分页查询。
     *
     * @param keyword 关键字，为空则查全部
     * @param current 页码，从 1 开始
     * @param size    每页条数
     */
    Page<UserVO> pageByKeyword(String keyword, long current, long size);

    /**
     * 同上，但可以关掉 count 查询。
     *
     * <p>导出是「按批滚动读取」，每批都带一次 {@code COUNT(*)} 纯属浪费，
     * 而且导出根本不需要总数 —— 它有自己的 5000 行上限。
     *
     * @param countTotal false 表示不查总数（{@code Page.getTotal()} 会是 0）
     */
    Page<UserVO> pageByKeyword(String keyword, long current, long size, boolean countTotal);

    /**
     * 用户总数，带 10 秒的进程内缓存。
     *
     * <p>只给概览统计用（工作台每次进入都会调）。InnoDB 的 {@code COUNT(*)}
     * 即使走索引也是一次完整扫描，用户量上来后没必要每次重算。
     *
     * <p><b>为什么不做写时失效</b>：失效点分散在新增 / 删除 / 批量删除 / 注册
     * 四五处，漏一处就会变成「数字永远不更新」；而 10 秒的滞后在一个概览数字上
     * 几乎察觉不到，且会自愈。两害相权，选滞后。
     */
    long countUsersForStats();

    /**
     * 按主键取用户公开信息，**优先读 Redis 缓存**，未命中则回源数据库并回填。
     *
     * @return 用户不存在或已被逻辑删除时返回 null
     */
    UserVO getUserVoById(Long id);

    /**
     * 把表单里的明文密码加密后写进实体的 passwordHash。
     *
     * <p>密码相关的逻辑全部收在 Service，Controller 只负责组装表单字段。
     *
     * @param user        目标实体
     * @param rawPassword 明文密码；为 null / 空白时表示「不改密码」，直接返回
     */
    void assignPassword(User user, String rawPassword);

    /**
     * 校验并加密密码，返回 BCrypt 哈希。
     *
     * <p>与 {@link #assignPassword} 共用同一套校验（最少 6 位、UTF-8 不超过 72 字节），
     * 区别是它只返回哈希、不碰实体 —— 更新路径需要「哈希 + 显式 set」的组合，
     * 见 {@link #overwriteEditable}。
     *
     * @param rawPassword 明文密码
     * @return BCrypt 哈希；rawPassword 为 null / 空白时返回 null（表示不改密码）
     * @throws com.example.myapp.common.BizException 密码不合规（4000）
     */
    String encodePassword(String rawPassword);

    /**
     * 覆盖用户的可编辑字段（显示名 / 邮箱 / 简介 / 年龄）。
     *
     * <p><b>为什么不能用 {@code updateById}</b>：MyBatis-Plus 默认的 NOT_NULL 更新策略
     * 会把值为 null 的字段排除在 SET 子句之外，于是「把昵称置空」会变成「不修改昵称」 ——
     * 用户点清空后刷新，值还在（实测踩到）。这里改用显式 set 的 UpdateWrapper，
     * 每一列都写，null 也照写，语义才是「覆盖」。
     *
     * <p>时间字段不在 set 列表里，因此仍由数据库的 ON UPDATE 自动更新。
     *
     * @param passwordHash 已编码的密码哈希；**null 表示不改密码**（这一点与其他字段不同）
     */
    void overwriteEditable(Long id, String nickname, String email, String bio, Integer age,
                           String passwordHash);

    /**
     * 管理员维护用户：一次 UPDATE 写完全部可编辑字段，并按需作废旧令牌。
     *
     * <p><b>为什么不直接用 {@link #overwriteEditable} 再补一次改角色</b>：
     * 那样「同时改密码和角色」会走两条 UPDATE，密码那条把 token_version +1、
     * 角色那条再 +1，一次操作版本号跳了 2。不影响正确性（旧令牌照样全失效），
     * 但版本号的语义变得无法解释 —— 将来若有人拿它做「改过几次密码」的依据就会读到错值。
     *
     * <p>这里合并成一条语句，并且**只在敏感字段真的变化时** +1：
     * 只改昵称不该把人踢下线。
     *
     * @param role         新角色；<b>null 表示不改</b>（与 nickname 等「null 即清空」相反）
     * @param passwordHash 已编码的密码哈希；null 表示不改密码
     */
    void updateManaged(Long id, String nickname, String email, String bio, Integer age,
                       String passwordHash, String role);

    /**
     * 把用户的令牌版本号 +1，使其**所有已签发的令牌立即失效**。
     *
     * <p>用在哪：改角色（权限变了，旧令牌里的角色断言就过时了）、
     * 以及管理员强制踢下线。改密码走 {@link #overwriteEditable}，
     * 那里在同一条 UPDATE 里自增，不需要再调本方法。
     *
     * <p>调用方**必须**同时 {@code userCache.evict(id)}：版本号在 UserVO 里被缓存，
     * 不失效的话拦截器会继续读到旧版本，撤销等于没做。
     */
    void bumpTokenVersion(Long id);
}
