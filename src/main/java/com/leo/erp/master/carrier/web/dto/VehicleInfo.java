package com.leo.erp.master.carrier.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.leo.erp.common.support.PhoneMaskSerializer;

public record VehicleInfo(
        Long id,
        String plate,
        String contact,
        @JsonSerialize(using = PhoneMaskSerializer.class) String phone,
        String remark
) {
    @JsonProperty("vehicleId")
    public Long vehicleId() {
        return id;
    }
}
