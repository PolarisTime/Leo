package com.leo.erp.master.material.mapper;

import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.web.dto.MaterialResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = StrictMapperConfig.class)
public interface MaterialMapper {

    MaterialResponse toResponse(Material material);
}
