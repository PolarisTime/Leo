package com.leo.erp.master.carrier.web.dto;

public record VehicleInfo(
        Long id,
        String plate,
        String contact,
        String phone,
        String remark
) {}
