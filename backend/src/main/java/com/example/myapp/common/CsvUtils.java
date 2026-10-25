package com.example.myapp.common;

/**
 * CSV 单元格处理。
 *
 * <p><b>为什么单独成类</b>：这段逻辑原来以 private static 方法塞在
 * {@code UserController} 里，只能靠「启动整个 Spring 上下文 + 发一次导出请求」
 * 间接验证。它是纯字符串函数，也是安全相关的（防公式注入），
 * 抽出来就能用毫秒级的单测覆盖每一个分支。
 *
 * <p>要处理两件事，缺一不可：
 * <ol>
 *   <li><b>列不错位</b>：逗号（列分隔）、双引号（值里的引号要翻倍）、换行，
 *       不处理的话「a,b」这样的邮箱会把后面的列全部挤歪 —— 而错位的表格
 *       看起来「有数据」，比直接报错更难发现。</li>
 *   <li><b>公式不执行</b>：以 {@code = + - @} 开头的值会被 Excel / WPS
 *       当成公式执行（CSV 注入）。前面补一个单引号，让它按文本处理。
 *       制表符与回车同样能触发这类解析，一并防住。</li>
 * </ol>
 */
public final class CsvUtils {

    /**
     * 能触发 Excel 公式解析的首字符。
     *
     * <p>除了常见的 {@code = + - @}，还包含制表符与回车：
     * 部分版本会先把它们当空白跳过后再按公式解析，等于绕过了前缀检查。
     */
    private static final String FORMULA_PREFIXES = "=+-@\t\r";

    private CsvUtils() {
    }

    /**
     * 把一个原始值转成安全的 CSV 单元格（不含分隔逗号）。
     *
     * @param value 原始值，可为 null
     * @return 转义后的单元格；null 转为空串
     */
    public static String cell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && FORMULA_PREFIXES.indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            // 引号在引号包裹的字段里要写成两个引号
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
