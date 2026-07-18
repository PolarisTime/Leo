package com.leo.erp.master.supplier.web.dto;

import jakarta.validation.constraints.NotBlank;

public record SupplierRequest(
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
