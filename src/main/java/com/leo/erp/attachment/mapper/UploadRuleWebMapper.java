package com.leo.erp.attachment.mapper;

import com.leo.erp.attachment.service.PageUploadRuleDetail;
import com.leo.erp.attachment.service.UpdatePageUploadRuleCommand;
import com.leo.erp.attachment.web.dto.UploadRuleRequest;
import com.leo.erp.attachment.web.dto.UploadRuleResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UploadRuleWebMapper {

    UploadRuleResponse toResponse(PageUploadRuleDetail detail);

    UpdatePageUploadRuleCommand toCommand(UploadRuleRequest request);
}
