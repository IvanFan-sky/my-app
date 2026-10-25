package com.example.myapp.common;

/**
 * LIKE 通配符转义。
 *
 * <p><b>为什么需要它</b>：{@code likeRight(field, kw)} 由 MyBatis-Plus 拼成
 * {@code LIKE 'kw%'}，但 <b>不会</b>转义 kw 里的通配符。于是：
 * <ul>
 *   <li>搜 {@code %} → 退化成 {@code LIKE '%%'}，匹配全表（结果错，还白扫一遍）；</li>
 *   <li>搜 {@code _} → 变成「任意单个字符」，同样不是用户期望的字面匹配。</li>
 * </ul>
 * 这不是 SQL 注入（参数仍然预编译），而是「LIKE 语义注入」——
 * 参数值被当成了模式语法的一部分。
 *
 * <p>转义符用反斜杠：MySQL 在语句没有显式 {@code ESCAPE} 时默认就是它，
 * 所以不需要改 SQL。<b>但要注意</b>：如果哪天开了 {@code NO_BACKSLASH_ESCAPES}，
 * 这里必须跟着改成显式 {@code ESCAPE '!'} 之类的写法。
 */
public final class SqlLike {

    /** MySQL 默认的 LIKE 转义符 */
    private static final char ESCAPE = '\\';

    private SqlLike() {
    }

    /**
     * 把用户输入里的 {@code \}、{@code %}、{@code _} 转义，使其按字面量匹配。
     *
     * <p>反斜杠本身也要转义：否则用户输入 {@code \%} 时，
     * 我们转出来的 {@code \\%} 才对应「一个反斜杠 + 零个以上任意字符」，
     * 而不是「字面量 %」—— 少了这一步，转义可以被输入绕过。
     */
    public static String escape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ESCAPE || c == '%' || c == '_') {
                sb.append(ESCAPE);
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
