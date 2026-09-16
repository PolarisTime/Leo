package com.leo.erp.master.supplier.web.dto;

public record SupplierResponse(
        Long id,
        String supplierCode,
        String supplierName,
        String shortName,
        String contactName,
        String contactPhone,
        String city,
        String status,
        String remark
) {

    /** 兼容无简称的构造(简称缺省为 null)。 */
    public SupplierResponse(Long id, String supplierCode, String supplierName, String contactName,
                            String contactPhone, String city, String status, String remark) {
        this(id, supplierCode, supplierName, null, contactName, contactPhone, city, status, remark);
    }
}
