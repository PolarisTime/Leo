package com.leo.erp.auth.web;

import com.leo.erp.auth.service.RefreshTokenAdminService;
import com.leo.erp.auth.web.dto.RefreshTokenAdminResponse;
import com.leo.erp.auth.web.dto.RefreshTokenSessionSummaryResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenAdminControllerTest {

    private final RefreshTokenAdminService service = mock(RefreshTokenAdminService.class);
    private final RefreshTokenAdminController controller = new RefreshTokenAdminController(service);

    @Test
    void pageReturnsPaginatedTokens() {
        RefreshTokenAdminResponse token = mock(RefreshTokenAdminResponse.class);
        Page<RefreshTokenAdminResponse> page = new PageImpl<>(List.of(token));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(service.pageWithUserInfo(any(), eq("test"))).thenReturn(page);

        ApiResponse<PageResponse<RefreshTokenAdminResponse>> response = controller.page(query, "test");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void summaryReturnsSessionSummary() {
        RefreshTokenSessionSummaryResponse summary = mock(RefreshTokenSessionSummaryResponse.class);
        when(service.summary()).thenReturn(summary);

        ApiResponse<RefreshTokenSessionSummaryResponse> response = controller.summary();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(summary);
    }

    @Test
    void revokeCallsServiceRevoke() {
        ApiResponse<Void> response = controller.revoke(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("已禁用");
        verify(service).revoke(1L);
    }

    @Test
    void revokeAllReturnsRevokedCount() {
        when(service.revokeAll()).thenReturn(5);

        ApiResponse<Integer> response = controller.revokeAll();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("已禁用 5 个令牌");
        assertThat(response.data()).isEqualTo(5);
    }
}
