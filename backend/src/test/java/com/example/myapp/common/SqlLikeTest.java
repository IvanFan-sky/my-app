package com.example.myapp.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LIKE 通配符转义的纯函数测试。
 *
 * <p>没转义时，用户搜一个 {@code %} 就退化成 {@code LIKE '%%'}，
 * 结果全错还白扫一遍全表 —— 这类问题不会报错，只会「搜出来的东西不对劲」。
 */
class SqlLikeTest {

    @Test
    @DisplayName("null 与空串原样返回")
    void nullAndEmpty() {
        assertThat(SqlLike.escape(null)).isNull();
        assertThat(SqlLike.escape("")).isEmpty();
    }

    @Test
    @DisplayName("普通关键字不被改动")
    void plainKeywordUntouched() {
        assertThat(SqlLike.escape("alice")).isEqualTo("alice");
        assertThat(SqlLike.escape("张三")).isEqualTo("张三");
    }

    @Test
    @DisplayName("% 与 _ 被反斜杠转义")
    void wildcardsAreEscaped() {
        assertThat(SqlLike.escape("%")).isEqualTo("\\%");
        assertThat(SqlLike.escape("50%")).isEqualTo("50\\%");
        assertThat(SqlLike.escape("a_b")).isEqualTo("a\\_b");
        assertThat(SqlLike.escape("%_%")).isEqualTo("\\%\\_\\%");
    }

    @Test
    @DisplayName("反斜杠本身也要转义，否则输入 \\% 可以绕过")
    void backslashIsEscapedFirst() {
        assertThat(SqlLike.escape("\\")).isEqualTo("\\\\");
        assertThat(SqlLike.escape("\\%")).isEqualTo("\\\\\\%");
    }
}
