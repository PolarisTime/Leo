package com.leo.erp.master.carrier.web.dto;

public record VehicleOptionResponse(
        Long id,
        Long value,
        String label,
        String plate
) {
    public VehicleOptionResponse {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("车辆选项必须包含有效稳定ID");
        }
        if (!id.equals(value)) {
            throw new IllegalArgumentException("车辆选项value必须与ID一致");
        }
        label = requireText(label, "车辆选项label不能为空");
        plate = requireText(plate, "车辆选项车牌不能为空");
    }

    public VehicleOptionResponse(Long id, String plate) {
        this(id, id, plate, plate);
    }

    private static String requireText(String value, String message) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
