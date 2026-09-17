package com.leo.erp.master.supplier.mapper;

import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.web.dto.SupplierResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface SupplierMapper {

    @Mapping(target = "brands", ignore = true)
    SupplierResponse toResponse(Supplier supplier);
}
