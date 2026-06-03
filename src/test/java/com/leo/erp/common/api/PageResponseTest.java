package com.leo.erp.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void shouldCreateFromPage() {
        Page<String> page = new PageImpl<>(
                List.of("a", "b", "c"),
                PageRequest.of(1, 10),
                25
        );

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b", "c");
        assertThat(response.totalElements()).isEqualTo(25);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.currentPage()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(10);
        assertThat(response.hasMore()).isTrue();
    }

    @Test
    void shouldIndicateNoMorePages() {
        Page<String> page = new PageImpl<>(
                List.of("a"),
                PageRequest.of(2, 10),
                21
        );

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void shouldHandleEmptyPage() {
        Page<String> page = new PageImpl<>(
                List.of(),
                PageRequest.of(0, 10),
                0
        );

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.hasMore()).isFalse();
    }
}
