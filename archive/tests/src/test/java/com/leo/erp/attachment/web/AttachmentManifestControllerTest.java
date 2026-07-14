package com.leo.erp.attachment.web;

import com.leo.erp.attachment.service.AttachmentManifestExportResult;
import com.leo.erp.attachment.service.AttachmentManifestExportService;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.RequiresPermission;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachmentManifestControllerTest {

    private final AttachmentManifestExportService exportService = mock(AttachmentManifestExportService.class);
    private final AttachmentManifestController controller = new AttachmentManifestController(exportService);

    @Test
    void exportDailyRequiresGeneralSettingUpdatePermission() throws NoSuchMethodException {
        RequiresPermission permission = AttachmentManifestController.class
                .getMethod("exportDaily")
                .getAnnotation(RequiresPermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.resource()).isEqualTo("general-setting");
        assertThat(permission.action()).isEqualTo("update");
    }

    @Test
    void exportDailyReturnsManifestResult() {
        AttachmentManifestExportResult expected = new AttachmentManifestExportResult(
                "attachment-manifests/daily/2026/07/02/manifest-20260702T020304Z.jsonl.gz",
                "s3:bucket/attachment-manifests/daily/2026/07/02/manifest-20260702T020304Z.jsonl.gz",
                2,
                3
        );
        when(exportService.exportDaily()).thenReturn(expected);

        ApiResponse<AttachmentManifestExportResult> response = controller.exportDaily();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("附件恢复清单导出成功");
        assertThat(response.data()).isEqualTo(expected);
        verify(exportService).exportDaily();
    }
}
