package com.example.myapp.common;

/**
 * 分页参数（current / size）。
 *
 * <p><b>为什么要有这个类</b>：分页接口的 {@code size} 是客户端直接传的，
 * 不做限制意味着 {@code GET /api/users?size=999999} 就能一次全表扫描
 * + 全量序列化 + 全量进内存。前端只会传 10/20/50，但接口本身是登录后可调的 ——
 * 前端的下拉选项不是安全边界。
 *
 * <p><b>越界抛 4000 而不是静默截断</b>：静默把 size=5000 改成 100，
 * 调用方会以为拿到了 5000 条，实际只拿到 100 条，还会因为 total 对不上
 * 去查半天。报错比「贴心地猜」更不容易出错。
 *
 * <p>注意：导出接口的 5000 行上限<b>不</b>走这里 —— 那是服务端自己决定的
 * 一次性读取量（见 {@code UserController.EXPORT_MAX_ROWS}），不是客户端入参。
 *
 * @param current 页码，从 1 开始
 * @param size    每页条数，1~{@value #MAX_SIZE}
 */
public record PageQuery(long current, long size) {

    /** 单页上限。超过这个数就不是「翻页」，该换导出或异步任务了 */
    public static final long MAX_SIZE = 100;

    /**
     * 校验并构造。
     *
     * @throws BizException 参数越界时抛 4000
     */
    public static PageQuery of(long current, long size) {
        if (current < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "current 必须大于等于 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "size 必须在 1~" + MAX_SIZE + " 之间");
        }
        return new PageQuery(current, size);
    }
}
