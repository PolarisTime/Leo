package com.leo.erp.master.supplier.mapper;

import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.web.dto.SupplierResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SupplierMapper {

    SupplierResponse toResponse(Supplier supplier);
}
