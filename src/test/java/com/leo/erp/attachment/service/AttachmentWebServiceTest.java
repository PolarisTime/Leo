package com.leo.erp.attachment.service;

import com.leo.erp.attachment.mapper.AttachmentWebMapper;
import com.leo.erp.attachment.web.dto.AttachmentDirectUploadCompleteRequest;
import com.leo.erp.attachment.web.dto.AttachmentDirectUploadPrepareRequest;
import com.leo.erp.attachment.web.dto.AttachmentDirectUploadPrepareResponse;
import com.leo.erp.attachment.web.dto.AttachmentBindingResponse;
import com.leo.erp.attachment.web.dto.AttachmentUploadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachmentWebServiceTest {

    @Test
    void uploadShouldDelegateAndMapResponse() throws IOException {
        AttachmentService attachmentService = mock(AttachmentService.class);
        AttachmentBindingService bindingService = mock(AttachmentBindingService.class);
        AttachmentWebMapper mapper = mock(AttachmentWebMapper.class);

        MultipartFile file = mock(MultipartFile.class);
        AttachmentView view = mock(AttachmentView.class);
        when(attachmentService.upload(file, "sales-order", "sales-order")).thenReturn(view);

        AttachmentUploadResponse expected = mock(AttachmentUploadResponse.class);
        when(mapper.toUploadResponse(view)).thenReturn(expected);

        AttachmentWebService service = new AttachmentWebService(attachmentService, bindingService, mapper);

        AttachmentUploadResponse result = service.upload(file, "sales-order", "sales-order");

        assertThat(result).isEqualTo(expected);
        verify(attachmentService).upload(file, "sales-order", "sales-order");
    }

    @Test
    void prepareDirectUploadShouldDelegateAndMapResponse() {
        AttachmentService attachmentService = mock(AttachmentService.class);
        AttachmentBindingService bindingService = mock(AttachmentBindingService.class);
        AttachmentWebMapper mapper = mock(AttachmentWebMapper.class);
        AttachmentDirectUploadPrepareRequest request = new AttachmentDirectUploadPrepareRequest(
                "test.pdf", "application/pdf", 1024L, "PAGE_UPLOAD",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        AttachmentService.DirectUploadPrepareResult prepareResult = new AttachmentService.DirectUploadPrepareResult(
                1L,
                "token",
                "attachments/2026/07/1/test.pdf",
                "s3:test-bucket/attachments/2026/07/1/test.pdf",
                URI.create("https://upload.example.com/test.pdf"),
                "PUT",
                Map.of("Content-Type", "application/pdf"),
                Instant.parse("2026-07-01T08:00:00Z")
        );
        AttachmentDirectUploadPrepareResponse expected = mock(AttachmentDirectUploadPrepareResponse.class);

        when(attachmentService.prepareDirectUpload(
                request.fileName(), request.contentType(), request.fileSize(), request.sourceType(), "sales-order",
                request.sha256Hex(), 9L))
                .thenReturn(prepareResult);
        when(mapper.toDirectUploadPrepareResponse(prepareResult)).thenReturn(expected);

        AttachmentWebService service = new AttachmentWebService(attachmentService, bindingService, mapper);

        AttachmentDirectUploadPrepareResponse result = service.prepareDirectUpload(request, "sales-order", 9L);

        assertThat(result).isEqualTo(expected);
        verify(attachmentService).prepareDirectUpload(
                "test.pdf", "application/pdf", 1024L, "PAGE_UPLOAD", "sales-order",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 9L);
    }

    @Test
    void completeDirectUploadShouldDelegateAndMapResponse() {
        AttachmentService attachmentService = mock(AttachmentService.class);
        AttachmentBindingService bindingService = mock(AttachmentBindingService.class);
        AttachmentWebMapper mapper = mock(AttachmentWebMapper.class);
        AttachmentDirectUploadCompleteRequest request = new AttachmentDirectUploadCompleteRequest(1L, "token");
        AttachmentView view = mock(AttachmentView.class);
        AttachmentUploadResponse expected = mock(AttachmentUploadResponse.class);

        when(attachmentService.completeDirectUpload(1L, "token", "sales-order", 9L)).thenReturn(view);
        when(mapper.toUploadResponse(view)).thenReturn(expected);

        AttachmentWebService service = new AttachmentWebService(attachmentService, bindingService, mapper);

        AttachmentUploadResponse result = service.completeDirectUpload(request, "sales-order", 9L);

        assertThat(result).isEqualTo(expected);
        verify(attachmentService).completeDirectUpload(1L, "token", "sales-order", 9L);
    }

    @Test
    void detailShouldDelegateAndMapResponse() {
        AttachmentService attachmentService = mock(AttachmentService.class);
        AttachmentBindingService bindingService = mock(AttachmentBindingService.class);
        AttachmentWebMapper mapper = mock(AttachmentWebMapper.class);

        List<AttachmentView> attachments = List.of();
        when(bindingService.list("sales-order", 1L)).thenReturn(attachments);

        AttachmentBindingResponse expected = mock(AttachmentBindingResponse.class);
        when(mapper.toBindingResponse("sales-order", 1L, attachments)).thenReturn(expected);

        AttachmentWebService service = new AttachmentWebService(attachmentService, bindingService, mapper);

        AttachmentBindingResponse result = service.detail("sales-order", 1L);

        assertThat(result).isEqualTo(expected);
        verify(bindingService).list("sales-order", 1L);
    }

    @Test
    void replaceShouldDelegateAndMapResponse() {
        AttachmentService attachmentService = mock(AttachmentService.class);
        AttachmentBindingService bindingService = mock(AttachmentBindingService.class);
        AttachmentWebMapper mapper = mock(AttachmentWebMapper.class);

        List<Long> attachmentIds = List.of(1L, 2L);
        List<AttachmentView> attachments = List.of();
        when(bindingService.replace("sales-order", 1L, attachmentIds)).thenReturn(attachments);

        AttachmentBindingResponse expected = mock(AttachmentBindingResponse.class);
        when(mapper.toBindingResponse("sales-order", 1L, attachments)).thenReturn(expected);

        AttachmentWebService service = new AttachmentWebService(attachmentService, bindingService, mapper);

        AttachmentBindingResponse result = service.replace("sales-order", 1L, attachmentIds);

        assertThat(result).isEqualTo(expected);
        verify(bindingService).replace("sales-order", 1L, attachmentIds);
    }
}
