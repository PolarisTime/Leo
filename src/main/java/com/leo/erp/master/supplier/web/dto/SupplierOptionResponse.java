package com.leo.erp.master.supplier.web.dto;

public record SupplierOptionResponse(
        Long id,
        Long value,
        String label,
        String supplierCode,
        String supplierName
) {
    public SupplierOptionResponse {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("供应商选项必须包含有效稳定ID");
        }
        if (!id.equals(value)) {
            throw new IllegalArgumentException("供应商选项value必须与ID一致");
        }
        label = requireText(label, "供应商选项label不能为空");
        supplierCode = requireText(supplierCode, "供应商编码不能为空");
        supplierName = requireText(supplierName, "供应商名称不能为空");
    }

    public SupplierOptionResponse(Long id, String supplierCode, String supplierName) {
        this(id, id, optionLabel(supplierCode, supplierName), supplierCode, supplierName);
    }

    private static String optionLabel(String supplierCode, String supplierName) {
        return requireText(supplierCode, "供应商编码不能为空")
                + " / "
                + requireText(supplierName, "供应商名称不能为空");
    }

    private static String requireText(String value, String message) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
