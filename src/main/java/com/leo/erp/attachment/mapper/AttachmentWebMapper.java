package com.leo.erp.attachment.mapper;

import com.leo.erp.attachment.service.AttachmentView;
import com.leo.erp.attachment.web.dto.AttachmentBindingResponse;
import com.leo.erp.attachment.web.dto.AttachmentResponse;
import com.leo.erp.attachment.web.dto.AttachmentUploadResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AttachmentWebMapper {

    AttachmentUploadResponse toUploadResponse(AttachmentView view);

    AttachmentResponse toResponse(AttachmentView view);

    List<AttachmentResponse> toResponses(List<AttachmentView> views);

    @Mapping(target = "attachments", source = "views")
    AttachmentBindingResponse toBindingResponse(String moduleKey, Long recordId, List<AttachmentView> views);
}
