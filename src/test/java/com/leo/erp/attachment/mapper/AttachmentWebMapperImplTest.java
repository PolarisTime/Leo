package com.leo.erp.attachment.mapper;

import com.leo.erp.attachment.service.AttachmentView;
import com.leo.erp.attachment.web.dto.AttachmentBindingResponse;
import com.leo.erp.attachment.web.dto.AttachmentResponse;
import com.leo.erp.attachment.web.dto.AttachmentUploadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {AttachmentWebMapperImpl.class})
class AttachmentWebMapperImplTest {

    @Autowired
    private AttachmentWebMapper mapper;

    @Test
    void shouldMapToUploadResponse() {
        AttachmentView view = createView();

        AttachmentUploadResponse response = mapper.toUploadResponse(view);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("test.pdf");
        assertThat(response.fileName()).isEqualTo("test.pdf");
        assertThat(response.originalFileName()).isEqualTo("原始文件.pdf");
        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.fileSize()).isEqualTo(1024L);
        assertThat(response.sourceType()).isEqualTo("PAGE_UPLOAD");
        assertThat(response.uploader()).isEqualTo("tester");
        assertThat(response.uploadTime()).isEqualTo(LocalDateTime.of(2026, 4, 25, 12, 0));
        assertThat(response.previewSupported()).isTrue();
        assertThat(response.previewType()).isEqualTo("pdf");
        assertThat(response.previewUrl()).isEqualTo("/api/attachment/1/preview");
        assertThat(response.downloadUrl()).isEqualTo("/api/attachment/1/download");
        assertThat(response.storageType()).isEqualTo("s3");
        assertThat(response.storageLabel()).isEqualTo("S3存储");
    }

    @Test
    void shouldMapToResponse() {
        AttachmentView view = createView();

        AttachmentResponse response = mapper.toResponse(view);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("test.pdf");
        assertThat(response.fileName()).isEqualTo("test.pdf");
        assertThat(response.originalFileName()).isEqualTo("原始文件.pdf");
        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.fileSize()).isEqualTo(1024L);
        assertThat(response.sourceType()).isEqualTo("PAGE_UPLOAD");
        assertThat(response.uploader()).isEqualTo("tester");
        assertThat(response.uploadTime()).isEqualTo(LocalDateTime.of(2026, 4, 25, 12, 0));
        assertThat(response.previewSupported()).isTrue();
        assertThat(response.previewType()).isEqualTo("pdf");
        assertThat(response.previewUrl()).isEqualTo("/api/attachment/1/preview");
        assertThat(response.downloadUrl()).isEqualTo("/api/attachment/1/download");
        assertThat(response.storageType()).isEqualTo("s3");
        assertThat(response.storageLabel()).isEqualTo("S3存储");
    }

    @Test
    void shouldMapToResponses() {
        AttachmentView view1 = createView();
        AttachmentView view2 = new AttachmentView(
                2L, "other.png", "other.png", "other.png",
                "image/png", 2048L, "PAGE_UPLOAD", "user2",
                LocalDateTime.of(2026, 5, 1, 10, 0),
                true, "image", "/api/attachment/2/preview", "/api/attachment/2/download"
        );

        List<AttachmentResponse> responses = mapper.toResponses(List.of(view1, view2));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(1L);
        assertThat(responses.get(0).name()).isEqualTo("test.pdf");
        assertThat(responses.get(1).id()).isEqualTo(2L);
        assertThat(responses.get(1).name()).isEqualTo("other.png");
        assertThat(responses.get(1).previewType()).isEqualTo("image");
    }

    @Test
    void shouldMapToResponses_withEmptyList() {
        List<AttachmentResponse> responses = mapper.toResponses(List.of());

        assertThat(responses).isEmpty();
    }

    @Test
    void shouldMapToBindingResponse() {
        AttachmentView view = createView();

        AttachmentBindingResponse response = mapper.toBindingResponse("sales-order", 1L, List.of(view));

        assertThat(response.moduleKey()).isEqualTo("sales-order");
        assertThat(response.recordId()).isEqualTo(1L);
        assertThat(response.attachments()).hasSize(1);
        assertThat(response.attachments().get(0).id()).isEqualTo(1L);
        assertThat(response.attachments().get(0).name()).isEqualTo("test.pdf");
    }

    @Test
    void shouldMapToBindingResponse_withEmptyAttachments() {
        AttachmentBindingResponse response = mapper.toBindingResponse("sales-order", 1L, List.of());

        assertThat(response.moduleKey()).isEqualTo("sales-order");
        assertThat(response.recordId()).isEqualTo(1L);
        assertThat(response.attachments()).isEmpty();
    }

    @Test
    void shouldMapToBindingResponse_withMultipleAttachments() {
        AttachmentView view1 = createView();
        AttachmentView view2 = new AttachmentView(
                2L, "other.png", "other.png", "other.png",
                "image/png", 2048L, "PAGE_UPLOAD", "user2",
                LocalDateTime.of(2026, 5, 1, 10, 0),
                true, "image", "/api/attachment/2/preview", "/api/attachment/2/download"
        );

        AttachmentBindingResponse response = mapper.toBindingResponse("purchase-order", 99L, List.of(view1, view2));

        assertThat(response.moduleKey()).isEqualTo("purchase-order");
        assertThat(response.recordId()).isEqualTo(99L);
        assertThat(response.attachments()).hasSize(2);
        assertThat(response.attachments()).extracting(AttachmentResponse::id).containsExactly(1L, 2L);
    }

    @Test
    void shouldHandleNullPreviewUrl_whenNotSupported() {
        AttachmentView view = new AttachmentView(
                3L, "data.xlsx", "data.xlsx", "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 512L,
                "PAGE_UPLOAD", "tester", LocalDateTime.of(2026, 5, 2, 8, 0),
                false, "none", null, "/api/attachment/3/download"
        );

        AttachmentResponse response = mapper.toResponse(view);

        assertThat(response.previewSupported()).isFalse();
        assertThat(response.previewType()).isEqualTo("none");
        assertThat(response.previewUrl()).isNull();
        assertThat(response.downloadUrl()).isEqualTo("/api/attachment/3/download");
    }

    private AttachmentView createView() {
        return new AttachmentView(
                1L,
                "test.pdf",
                "test.pdf",
                "原始文件.pdf",
                "application/pdf",
                1024L,
                "PAGE_UPLOAD",
                "tester",
                LocalDateTime.of(2026, 4, 25, 12, 0),
                true,
                "pdf",
                "/api/attachment/1/preview",
                "/api/attachment/1/download",
                "s3",
                "S3存储"
        );
    }
}
