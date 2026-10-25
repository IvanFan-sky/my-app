package com.example.myapp.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 分页参数校验的纯函数测试。
 *
 * <p>边界值要逐个钉住：上限本身必须可用（100 能过），
 * 上限 +1 必须被拒（101 报错），否则「限制」只是写在注释里。
 */
class PageQueryTest {

    @Test
    @DisplayName("合法参数原样保留")
    void validValues() {
        PageQuery query = PageQuery.of(1, 10);
        assertThat(query.current()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(10);

        // 上限本身是合法值，不能被误杀
        assertThat(PageQuery.of(1, PageQuery.MAX_SIZE).size())
                .isEqualTo(PageQuery.MAX_SIZE);
    }

    @Test
    @DisplayName("current 小于 1 抛 4000")
    void rejectsNonPositiveCurrent() {
        for (long current : new long[]{0, -1}) {
            assertThatThrownBy(() -> PageQuery.of(current, 10))
                    .isInstanceOf(BizException.class)
                    .satisfies(e -> assertThat(((BizException) e).getCode())
                            .isEqualTo(ErrorCode.PARAM_INVALID));
        }
    }

    @Test
    @DisplayName("size 越界（0 / 上限+1）抛 4000")
    void rejectsOutOfRangeSize() {
        for (long size : new long[]{0, -5, PageQuery.MAX_SIZE + 1, 100_000}) {
            assertThatThrownBy(() -> PageQuery.of(1, size))
                    .as("size=%s 应被拒绝", size)
                    .isInstanceOf(BizException.class)
                    .satisfies(e -> assertThat(((BizException) e).getCode())
                            .isEqualTo(ErrorCode.PARAM_INVALID));
        }
    }
}
