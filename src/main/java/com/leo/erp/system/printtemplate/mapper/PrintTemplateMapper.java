package com.leo.erp.system.printtemplate.mapper;

import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface PrintTemplateMapper {

    @Mapping(target = "id", expression = "java(template.getId() == null ? null : String.valueOf(template.getId()))")
    @Mapping(target = "settlementCompanyId", expression = "java(template.getSettlementCompanyId() == null ? null : String.valueOf(template.getSettlementCompanyId()))")
    @Mapping(target = "createTime", source = "createdAt")
    @Mapping(target = "updateTime", source = "updatedAt")
    PrintTemplateResponse toResponse(PrintTemplate template);
}
