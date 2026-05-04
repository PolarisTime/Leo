package com.leo.erp.master.carrier.web.dto;

import java.util.List;

public record CarrierOptionResponse(
        String label,
        String value,
        List<String> vehiclePlates
) {
}
