package com.leo.erp.master.supplier.web.dto;

import java.util.List;

public record SupplierResponse(
        Long id,
        String supplierCode,
        String supplierName,
        String shortName,
        String contactName,
        String contactPhone,
        String city,
        String status,
        String remark,
        List<String> brands
) {

    public SupplierResponse {
        brands = brands == null ? List.of() : List.copyOf(brands);
    }

    /** 兼容无品牌的构造(品牌缺省为空列表)。 */
    public SupplierResponse(Long id, String supplierCode, String supplierName, String shortName,
                            String contactName, String contactPhone, String city, String status, String remark) {
        this(id, supplierCode, supplierName, shortName, contactName, contactPhone, city, status, remark, List.of());
    }

    /** 兼容无简称、无品牌的构造(简称缺省为 null)。 */
    public SupplierResponse(Long id, String supplierCode, String supplierName, String contactName,
                            String contactPhone, String city, String status, String remark) {
        this(id, supplierCode, supplierName, null, contactName, contactPhone, city, status, remark, List.of());
    }
}
