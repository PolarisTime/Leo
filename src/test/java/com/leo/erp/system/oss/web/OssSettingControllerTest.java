package com.leo.erp.system.oss.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.system.oss.service.OssSettingService;
import com.leo.erp.system.oss.web.dto.OssSettingRequest;
import com.leo.erp.system.oss.web.dto.OssSettingResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssSettingControllerTest {

    private final OssSettingService service = mock(OssSettingService.class);
    private final OssSettingController controller = new OssSettingController(service);

    @Test
    void currentReturnsOssSetting() {
        OssSettingResponse setting = mock(OssSettingResponse.class);
        when(service.current()).thenReturn(setting);

        ApiResponse<OssSettingResponse> response = controller.current();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(setting);
        verify(service).current();
    }

    @Test
    void saveReturnsSavedOssSetting() {
        OssSettingRequest request = mock(OssSettingRequest.class);
        OssSettingResponse setting = mock(OssSettingResponse.class);
        when(service.save(request)).thenReturn(setting);

        ApiResponse<OssSettingResponse> response = controller.save(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("保存成功");
        assertThat(response.data()).isEqualTo(setting);
        verify(service).save(request);
    }
}
