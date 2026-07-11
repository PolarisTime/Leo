package com.leo.erp.master.supplier.web.dto;

public record SupplierOptionResponse(
        Long id,
        String supplierCode,
        String label,
        String value
) {
    public SupplierOptionResponse(Long id, String label, String value) {
        this(id, null, label, value);
    }
}
