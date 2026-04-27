package com.leo.erp.system.printtemplate.mapper;

import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PrintTemplateMapper {

    PrintTemplateResponse toResponse(PrintTemplate template);
}
