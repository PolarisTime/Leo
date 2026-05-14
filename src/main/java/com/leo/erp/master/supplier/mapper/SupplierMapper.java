package com.leo.erp.master.supplier.mapper;

import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.web.dto.SupplierResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = StrictMapperConfig.class)
public interface SupplierMapper {

    SupplierResponse toResponse(Supplier supplier);
}
