package com.leo.erp.attachment.web;

import com.leo.erp.attachment.service.UploadRuleService;
import com.leo.erp.attachment.web.dto.UploadRuleRequest;
import com.leo.erp.attachment.web.dto.UploadRuleResponse;
import com.leo.erp.common.api.ApiResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadRuleControllerTest {

    private final UploadRuleService uploadRuleService = mock(UploadRuleService.class);
    private final UploadRuleController controller = new UploadRuleController(uploadRuleService);

    @Test
    void detailReturnsUploadRule() {
        UploadRuleResponse rule = mock(UploadRuleResponse.class);
        when(uploadRuleService.responseDetail(eq("sales-order"))).thenReturn(rule);

        ApiResponse<UploadRuleResponse> response = controller.detail("sales-order");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(rule);
    }

    @Test
    void updateReturnsUpdatedRule() {
        UploadRuleRequest request = mock(UploadRuleRequest.class);
        UploadRuleResponse updated = mock(UploadRuleResponse.class);
        when(uploadRuleService.responseUpdate(eq("sales-order"), eq(request))).thenReturn(updated);

        ApiResponse<UploadRuleResponse> response = controller.update("sales-order", request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(uploadRuleService).responseUpdate("sales-order", request);
    }
}
