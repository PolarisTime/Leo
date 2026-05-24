package com.leo.erp.master.carrier.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.leo.erp.common.support.PhoneMaskSerializer;

public record VehicleItem(
        String plate,
        String contact,
        @JsonSerialize(using = PhoneMaskSerializer.class) String phone,
        String remark
) {}
