package com.leo.erp.master.supplier.web.dto;

import jakarta.validation.constraints.NotBlank;

public record SupplierRequest(
        @NotBlank(message = "供应商编码不能为空")
        String supplierCode,
        @NotBlank(message = "供应商名称不能为空")
        String supplierName,
        String contactName,
        String contactPhone,
        String city,
        @NotBlank(message = "状态不能为空")
        String status,
        String remark
) {
}
