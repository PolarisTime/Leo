package com.leo.erp.attachment.service;

import com.leo.erp.attachment.mapper.AttachmentWebMapper;
import com.leo.erp.attachment.web.dto.AttachmentBindingResponse;
import com.leo.erp.attachment.web.dto.AttachmentUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class AttachmentWebService {

    private final AttachmentService attachmentService;
    private final AttachmentBindingService attachmentBindingService;
    private final AttachmentWebMapper attachmentWebMapper;

    public AttachmentWebService(AttachmentService attachmentService,
                                AttachmentBindingService attachmentBindingService,
                                AttachmentWebMapper attachmentWebMapper) {
        this.attachmentService = attachmentService;
        this.attachmentBindingService = attachmentBindingService;
        this.attachmentWebMapper = attachmentWebMapper;
    }

    public AttachmentUploadResponse upload(MultipartFile file, String sourceType, String moduleKey) throws IOException {
        return attachmentWebMapper.toUploadResponse(attachmentService.upload(file, sourceType, moduleKey));
    }

    public AttachmentBindingResponse detail(String moduleKey, Long recordId) {
        List<AttachmentView> attachments = attachmentBindingService.list(moduleKey, recordId);
        return attachmentWebMapper.toBindingResponse(moduleKey, recordId, attachments);
    }

    public AttachmentBindingResponse replace(String moduleKey, Long recordId, List<Long> attachmentIds) {
        List<AttachmentView> attachments = attachmentBindingService.replace(moduleKey, recordId, attachmentIds);
        return attachmentWebMapper.toBindingResponse(moduleKey, recordId, attachments);
    }
}
