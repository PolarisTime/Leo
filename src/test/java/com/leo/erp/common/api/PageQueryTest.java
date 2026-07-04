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
        assertThatThrownBy(() -> PageQuery.of(0, 201, null, null))
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
    void shouldRejectInvalidSortFieldFormat() {
        assertThatThrownBy(() -> PageQuery.of(0, 20, "1bad", "asc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sortBy 格式不合法");
    }

    @Test
    void shouldNormalizeValidValues() {
        PageQuery query = PageQuery.of(1, 50, " updatedAt ", " DESC ");

        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(50);
        assertThat(query.sortBy()).isEqualTo("updatedAt");
        assertThat(query.direction()).isEqualTo("desc");
    }

    @Test
    void shouldUseDefaultsAndFallbackSortWhenValuesAreBlank() {
        PageQuery query = PageQuery.of(null, null, " ", " ");

        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
        assertThat(query.sortBy()).isNull();
        assertThat(query.direction()).isNull();
        assertThat(query.toPageable("createdAt").getSort().getOrderFor("createdAt")).isNotNull();
    }

    @Test
    void shouldUseDefaultSortWhenPageableSortFieldIsBlank() {
        PageQuery query = new PageQuery(0, 20, " ", "asc");

        var order = query.toPageable("createdAt").getSort().getOrderFor("createdAt");

        assertThat(order).isNotNull();
        assertThat(order.isAscending()).isTrue();
    }

    @Test
    void shouldBuildAscendingPageableWhenDirectionIsAsc() {
        PageQuery query = PageQuery.of(0, 10, "id", "asc");

        assertThat(query.toPageable("createdAt").getSort().getOrderFor("id").isAscending()).isTrue();
        assertThat(query.toPageable("createdAt").getSort().getOrderFor("createdAt")).isNull();
    }
}
