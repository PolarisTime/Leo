package com.leo.erp.search.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.search.service.GlobalSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalSearchControllerTest {

    private final GlobalSearchService globalSearchService = mock(GlobalSearchService.class);
    private final GlobalSearchController controller = new GlobalSearchController(globalSearchService);

    @Test
    void searchReturnsResults() {
        GlobalSearchResponse item = mock(GlobalSearchResponse.class);
        when(globalSearchService.search(eq("test"), eq(20), eq(List.of("sales-order")))).thenReturn(List.of(item));

        ApiResponse<List<GlobalSearchResponse>> response = controller.search("test", 20, List.of("sales-order"));

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void searchWithNullKeywordUsesEmptyString() {
        GlobalSearchResponse item = mock(GlobalSearchResponse.class);
        when(globalSearchService.search(eq(""), eq(20), eq(null))).thenReturn(List.of(item));

        ApiResponse<List<GlobalSearchResponse>> response = controller.search(null, 20, null);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }
}
