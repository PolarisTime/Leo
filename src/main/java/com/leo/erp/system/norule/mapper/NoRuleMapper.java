package com.leo.erp.system.norule.mapper;

import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.web.dto.NoRuleResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NoRuleMapper {

    NoRuleResponse toResponse(NoRule rule);
}
