package com.leo.erp.master.carrier.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.leo.erp.common.support.PhoneMaskSerializer;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record VehicleItem(
        @Positive(message = "车辆ID必须为正数")
        @JsonAlias("id")
        Long vehicleId,
        @Size(max = 16, message = "车牌号长度不能超过16")
        String plate,
        @Size(max = 32, message = "联系人长度不能超过32")
        String contact,
        @Size(max = 20, message = "联系电话长度不能超过20")
        @JsonSerialize(using = PhoneMaskSerializer.class) String phone,
        @Size(max = 64, message = "备注长度不能超过64")
        String remark
) {
    public VehicleItem(String plate,
                       String contact,
                       String phone,
                       String remark) {
        this(null, plate, contact, phone, remark);
    }
}
