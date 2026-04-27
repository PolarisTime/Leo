package com.leo.erp.master.supplier.web.dto;

public record SupplierResponse(
        Long id,
        String supplierCode,
        String supplierName,
        String contactName,
        String contactPhone,
        String city,
        String status,
        String remark
) {
}
