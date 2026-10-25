package com.example.myapp.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSV 单元格转义的纯函数测试。
 *
 * <p>不起 Spring 上下文，毫秒级。这里要守住的是两类「静默错误」：
 * 列错位（表格看起来有数据、实际全歪）和公式被执行（CSV 注入）。
 */
class CsvUtilsTest {

    @Test
    @DisplayName("null 转空串，普通值原样返回")
    void plainValues() {
        assertThat(CsvUtils.cell(null)).isEmpty();
        assertThat(CsvUtils.cell("")).isEmpty();
        assertThat(CsvUtils.cell("alice")).isEqualTo("alice");
        assertThat(CsvUtils.cell("张三")).isEqualTo("张三");
    }

    @Test
    @DisplayName("逗号 / 双引号 / 换行必须被引号包裹，否则列会错位")
    void quoting() {
        assertThat(CsvUtils.cell("a,b")).isEqualTo("\"a,b\"");
        // 值里的引号要翻倍，否则提前闭合字段，后面的列全歪
        assertThat(CsvUtils.cell("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(CsvUtils.cell("a\nb")).isEqualTo("\"a\nb\"");
        assertThat(CsvUtils.cell("a\rb")).isEqualTo("\"a\rb\"");
    }

    @Test
    @DisplayName("以 = + - @ 开头的值补单引号，防止 Excel 当公式执行")
    void formulaInjection() {
        assertThat(CsvUtils.cell("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvUtils.cell("+86")).isEqualTo("'+86");
        assertThat(CsvUtils.cell("-1")).isEqualTo("'-1");
        assertThat(CsvUtils.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }

    @Test
    @DisplayName("制表符与回车同样能触发公式解析，也要挡")
    void tabAndCarriageReturnPrefix() {
        // 部分 Excel 版本会先跳过前导空白再按公式解析，只防 =+-@ 等于留了口子
        assertThat(CsvUtils.cell("\t=1+1")).isEqualTo("'\t=1+1");
        assertThat(CsvUtils.cell("\r=1+1")).isEqualTo("\"'\r=1+1\"");
    }

    @Test
    @DisplayName("公式前缀与分隔符同时出现时，两种处理都要生效")
    void formulaAndQuotingTogether() {
        assertThat(CsvUtils.cell("=a,b")).isEqualTo("\"'=a,b\"");
    }
}
