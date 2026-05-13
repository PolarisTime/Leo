package com.leo.erp.master.material.mapper;

import com.leo.erp.master.material.domain.entity.MaterialCategory;
import com.leo.erp.master.material.web.dto.MaterialCategoryOptionResponse;
import com.leo.erp.master.material.web.dto.MaterialCategoryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MaterialCategoryMapper {

    MaterialCategoryResponse toResponse(MaterialCategory materialCategory);

    @Mapping(target = "value", source = "categoryName")
    @Mapping(target = "label", source = "categoryName")
    MaterialCategoryOptionResponse toOptionResponse(MaterialCategory materialCategory);
}
