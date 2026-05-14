package com.leo.erp.attachment.mapper;

import com.leo.erp.attachment.service.PageUploadRuleDetail;
import com.leo.erp.attachment.service.UpdatePageUploadRuleCommand;
import com.leo.erp.attachment.web.dto.UploadRuleRequest;
import com.leo.erp.attachment.web.dto.UploadRuleResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = StrictMapperConfig.class)
public interface UploadRuleWebMapper {

    UploadRuleResponse toResponse(PageUploadRuleDetail detail);

    UpdatePageUploadRuleCommand toCommand(UploadRuleRequest request);
}
