package com.leo.erp.attachment.service;

import com.leo.erp.attachment.mapper.AttachmentWebMapper;
import com.leo.erp.attachment.web.dto.AttachmentBindingResponse;
import com.leo.erp.attachment.web.dto.AttachmentUploadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

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
