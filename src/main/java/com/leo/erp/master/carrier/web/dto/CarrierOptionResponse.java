package com.leo.erp.master.carrier.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CarrierOptionResponse(
        Long id,
        Long value,
        String label,
        String carrierCode,
        String carrierName,
        Long defaultSettlementCompanyId,
        String defaultSettlementCompanyName,
        List<VehicleOptionResponse> vehicles
) {
    public CarrierOptionResponse {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("物流商选项必须包含有效稳定ID");
        }
        if (!id.equals(value)) {
            throw new IllegalArgumentException("物流商选项value必须与ID一致");
        }
        label = requireText(label, "物流商选项label不能为空");
        carrierCode = requireText(carrierCode, "物流商编码不能为空");
        carrierName = requireText(carrierName, "物流商名称不能为空");
        defaultSettlementCompanyName = validateSettlementCompany(
                defaultSettlementCompanyId,
                defaultSettlementCompanyName
        );
        vehicles = vehicles == null ? List.of() : List.copyOf(vehicles);
    }

    public CarrierOptionResponse(Long id,
                                 String carrierCode,
                                 String carrierName,
                                 Long defaultSettlementCompanyId,
                                 String defaultSettlementCompanyName,
                                 List<VehicleOptionResponse> vehicles) {
        this(
                id,
                id,
                optionLabel(carrierCode, carrierName),
                carrierCode,
                carrierName,
                defaultSettlementCompanyId,
                defaultSettlementCompanyName,
                vehicles
        );
    }

    private static String optionLabel(String carrierCode, String carrierName) {
        return requireText(carrierCode, "物流商编码不能为空")
                + " / "
                + requireText(carrierName, "物流商名称不能为空");
    }

    @JsonProperty(value = "vehiclePlates", access = JsonProperty.Access.READ_ONLY)
    public List<String> vehiclePlates() {
        return vehicles.stream()
                .map(VehicleOptionResponse::plate)
                .toList();
    }

    private static String validateSettlementCompany(Long id, String name) {
        String normalizedName = normalize(name);
        if (id == null) {
            if (normalizedName != null) {
                throw new IllegalArgumentException("默认结算主体ID不能为空");
            }
            return null;
        }
        if (id <= 0) {
            throw new IllegalArgumentException("默认结算主体ID必须为正数");
        }
        return requireText(normalizedName, "默认结算主体名称不能为空");
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        String normalized = value == null ? null : value.trim();
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
