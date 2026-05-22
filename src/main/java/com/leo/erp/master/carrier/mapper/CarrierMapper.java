package com.leo.erp.master.carrier.mapper;

import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.domain.entity.Vehicle;
import com.leo.erp.master.carrier.web.dto.CarrierResponse;
import com.leo.erp.master.carrier.web.dto.VehicleInfo;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = StrictMapperConfig.class)
public interface CarrierMapper {

    CarrierResponse toResponse(Carrier carrier);

    VehicleInfo toVehicleInfo(Vehicle vehicle);
}
