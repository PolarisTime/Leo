package com.leo.erp.system.norule.mapper;

import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.web.dto.NoRuleResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = StrictMapperConfig.class)
public interface NoRuleMapper {

    NoRuleResponse toResponse(NoRule rule);
}
