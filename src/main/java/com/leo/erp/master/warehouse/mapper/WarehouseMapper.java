package com.leo.erp.master.warehouse.mapper;

import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import com.leo.erp.master.warehouse.web.dto.WarehouseResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WarehouseMapper {

    WarehouseResponse toResponse(Warehouse warehouse);
}
