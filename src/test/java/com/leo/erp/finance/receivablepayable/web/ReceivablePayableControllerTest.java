package com.leo.erp.finance.receivablepayable.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.finance.receivablepayable.service.ReceivablePayableService;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableDetailResponse;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceivablePayableControllerTest {

    private final ReceivablePayableService receivablePayableService = mock(ReceivablePayableService.class);
    private final ReceivablePayableController controller = new ReceivablePayableController(receivablePayableService);

    @Test
    void pageReturnsPaginatedReceivablePayables() {
        ReceivablePayableResponse item = mock(ReceivablePayableResponse.class);
        Page<ReceivablePayableResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(receivablePayableService.page(any(), eq("in"), eq("customer"), eq("active"), eq("test"))).thenReturn(page);

        ApiResponse<PageResponse<ReceivablePayableResponse>> response = controller.page(query, "in", "customer", "active", "test");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsReceivablePayableById() {
        ReceivablePayableDetailResponse detail = mock(ReceivablePayableDetailResponse.class);
        when(receivablePayableService.detail("1")).thenReturn(detail);

        ApiResponse<ReceivablePayableDetailResponse> response = controller.detail("1");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(detail);
    }

    @Test
    void exportReturnsFileDownload() {
        byte[] content = "test".getBytes();
        FileDownloadResponse file = new FileDownloadResponse("test.xlsx", MediaType.APPLICATION_OCTET_STREAM, content);
        when(receivablePayableService.exportExcel(eq("in"), eq("customer"), eq("active"), eq("test"))).thenReturn(file);

        ResponseEntity<byte[]> response = controller.export("in", "customer", "active", "test");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(content);
        verify(receivablePayableService).exportExcel("in", "customer", "active", "test");
    }
}
