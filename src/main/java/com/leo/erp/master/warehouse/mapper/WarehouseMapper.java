package com.leo.erp.master.warehouse.mapper;

import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import com.leo.erp.master.warehouse.web.dto.WarehouseResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = StrictMapperConfig.class)
public interface WarehouseMapper {

    WarehouseResponse toResponse(Warehouse warehouse);
}
