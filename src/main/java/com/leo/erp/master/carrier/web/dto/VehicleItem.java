package com.leo.erp.master.carrier.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.leo.erp.common.support.PhoneMaskSerializer;
import jakarta.validation.constraints.Positive;

public record VehicleItem(
        @Positive(message = "车辆ID必须为正数")
        @JsonAlias("id")
        Long vehicleId,
        String plate,
        String contact,
        @JsonSerialize(using = PhoneMaskSerializer.class) String phone,
        String remark
) {
    public VehicleItem(String plate,
                       String contact,
                       String phone,
                       String remark) {
        this(null, plate, contact, phone, remark);
    }
}
