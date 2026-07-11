package com.leo.erp.master.carrier.web.dto;

import java.util.List;

public record CarrierOptionResponse(
        Long id,
        String carrierCode,
        String label,
        String value,
        List<String> vehiclePlates,
        Long defaultSettlementCompanyId,
        String defaultSettlementCompanyName
) {
    public CarrierOptionResponse(Long id,
                                 String label,
                                 String value,
                                 List<String> vehiclePlates,
                                 Long defaultSettlementCompanyId,
                                 String defaultSettlementCompanyName) {
        this(id, null, label, value, vehiclePlates, defaultSettlementCompanyId, defaultSettlementCompanyName);
    }

    public CarrierOptionResponse(Long id, String label, String value, List<String> vehiclePlates) {
        this(id, null, label, value, vehiclePlates, null, null);
    }
}
