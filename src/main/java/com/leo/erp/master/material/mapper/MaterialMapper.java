package com.leo.erp.master.material.mapper;

import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.web.dto.MaterialResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MaterialMapper {

    MaterialResponse toResponse(Material material);
}
