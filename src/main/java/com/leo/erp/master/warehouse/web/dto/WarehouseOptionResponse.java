package com.leo.erp.master.warehouse.web.dto;

public record WarehouseOptionResponse(
        Long id,
        Long value,
        String label,
        String warehouseCode,
        String warehouseName
) {
    public WarehouseOptionResponse {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("仓库选项必须包含有效稳定ID");
        }
        if (!id.equals(value)) {
            throw new IllegalArgumentException("仓库选项value必须与ID一致");
        }
        label = requireText(label, "仓库选项label不能为空");
        warehouseCode = requireText(warehouseCode, "仓库编码不能为空");
        warehouseName = requireText(warehouseName, "仓库名称不能为空");
    }

    public WarehouseOptionResponse(Long id, String warehouseCode, String warehouseName) {
        this(id, id, optionLabel(warehouseCode, warehouseName), warehouseCode, warehouseName);
    }

    private static String optionLabel(String warehouseCode, String warehouseName) {
        return requireText(warehouseCode, "仓库编码不能为空")
                + " / "
                + requireText(warehouseName, "仓库名称不能为空");
    }

    private static String requireText(String value, String message) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
