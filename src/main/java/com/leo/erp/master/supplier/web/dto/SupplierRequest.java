package com.leo.erp.master.supplier.web.dto;

import com.leo.erp.common.support.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SupplierRequest(
        @NotBlank(message = "供应商编码不能为空")
        String supplierCode,
        @NotBlank(message = "供应商名称不能为空")
        String supplierName,
        String shortName,
        String contactName,
        String contactPhone,
        String city,
        @NotBlank(message = ValidationMessages.STATUS_REQUIRED)
        String status,
        String remark,
        @Size(max = 200, message = "经营品牌数量不能超过200个")
        List<@Size(max = 64, message = "品牌名称长度不能超过64个字符") String> brands
) {

    /** 兼容无品牌的构造(品牌缺省为 null, 表示不改动/无品牌)。 */
    public SupplierRequest(String supplierCode, String supplierName, String shortName, String contactName,
                           String contactPhone, String city, String status, String remark) {
        this(supplierCode, supplierName, shortName, contactName, contactPhone, city, status, remark, null);
    }

    /** 兼容无简称、无品牌的构造(简称缺省为 null)。 */
    public SupplierRequest(String supplierCode, String supplierName, String contactName, String contactPhone,
                           String city, String status, String remark) {
        this(supplierCode, supplierName, null, contactName, contactPhone, city, status, remark, null);
    }
}
