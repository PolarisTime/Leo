package com.leo.erp.master.supplier.web.dto;

import java.util.List;

public record SupplierOptionResponse(
        Long id,
        Long value,
        String label,
        String supplierCode,
        String supplierName,
        String shortName,
        List<String> brands
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
        shortName = shortName == null ? null : shortName.trim();
        brands = brands == null ? List.of() : List.copyOf(brands);
    }

    public SupplierOptionResponse(Long id, Long value, String label, String supplierCode, String supplierName,
                                  String shortName) {
        this(id, value, label, supplierCode, supplierName, shortName, List.of());
    }

    public SupplierOptionResponse(Long id, String supplierCode, String supplierName, String shortName,
                                  List<String> brands) {
        this(id, id, optionLabel(supplierCode, supplierName), supplierCode, supplierName, shortName, brands);
    }

    public SupplierOptionResponse(Long id, String supplierCode, String supplierName, String shortName) {
        this(id, supplierCode, supplierName, shortName, List.of());
    }

    public SupplierOptionResponse(Long id, String supplierCode, String supplierName) {
        this(id, supplierCode, supplierName, null);
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
