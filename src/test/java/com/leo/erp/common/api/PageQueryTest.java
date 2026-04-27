package com.leo.erp.common.api;

import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @Test
    void shouldRejectNegativePage() {
        assertThatThrownBy(() -> PageQuery.of(-1, 20, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("page 不能小于0");
    }

    @Test
    void shouldRejectInvalidSize() {
        assertThatThrownBy(() -> PageQuery.of(0, 0, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("size 必须在1到200之间");
    }

    @Test
    void shouldRejectInvalidDirection() {
        assertThatThrownBy(() -> PageQuery.of(0, 20, "id", "sideways"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("direction 只能为 asc 或 desc");
    }

    @Test
    void shouldRejectSortFieldOutsideAllowList() {
        assertThatThrownBy(() -> PageQuery.of(0, 20, "status", "asc", Set.of("id", "name")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sortBy 不支持当前列表");
    }

    @Test
    void shouldNormalizeValidValues() {
        PageQuery query = PageQuery.of(1, 50, " updatedAt ", " DESC ");

        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(50);
        assertThat(query.sortBy()).isEqualTo("updatedAt");
        assertThat(query.direction()).isEqualTo("desc");
    }
}
