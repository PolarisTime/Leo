package com.leo.erp.attachment.service;

import com.leo.erp.attachment.mapper.AttachmentWebMapper;
import com.leo.erp.attachment.web.dto.AttachmentBindingResponse;
import com.leo.erp.attachment.web.dto.AttachmentBindingCountResponse;
import com.leo.erp.attachment.web.dto.AttachmentDirectUploadCompleteRequest;
import com.leo.erp.attachment.web.dto.AttachmentDirectUploadPrepareRequest;
import com.leo.erp.attachment.web.dto.AttachmentDirectUploadPrepareResponse;
import com.leo.erp.attachment.web.dto.AttachmentUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    public AttachmentDirectUploadPrepareResponse prepareDirectUpload(
            AttachmentDirectUploadPrepareRequest request, String moduleKey, Long ownerUserId) {
        AttachmentService.DirectUploadPrepareResult result = attachmentService.prepareDirectUpload(
                request.fileName(),
                request.contentType(),
                request.fileSize(),
                request.sourceType(),
                moduleKey,
                request.sha256Hex(),
                ownerUserId
        );
        return attachmentWebMapper.toDirectUploadPrepareResponse(result);
    }

    public AttachmentUploadResponse completeDirectUpload(
            AttachmentDirectUploadCompleteRequest request, String moduleKey, Long ownerUserId) {
        return attachmentWebMapper.toUploadResponse(
                attachmentService.completeDirectUpload(request.attachmentId(), request.token(), moduleKey, ownerUserId));
    }

    public AttachmentBindingResponse detail(String moduleKey, Long recordId) {
        List<AttachmentView> attachments = attachmentBindingService.list(moduleKey, recordId);
        return attachmentWebMapper.toBindingResponse(moduleKey, recordId, attachments);
    }

    public AttachmentBindingResponse replace(String moduleKey, Long recordId, List<Long> attachmentIds) {
        List<AttachmentView> attachments = attachmentBindingService.replace(moduleKey, recordId, attachmentIds);
        return attachmentWebMapper.toBindingResponse(moduleKey, recordId, attachments);
    }

    public AttachmentBindingCountResponse counts(String moduleKey, List<Long> recordIds) {
        Map<Long, Integer> counts = attachmentBindingService.countByRecordIds(moduleKey, recordIds);
        return new AttachmentBindingCountResponse(moduleKey, counts);
    }
}
