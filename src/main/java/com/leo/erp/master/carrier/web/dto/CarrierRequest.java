package com.leo.erp.master.carrier.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CarrierRequest(
        @NotBlank(message = "物流商编码不能为空")
        String carrierCode,
        @NotBlank(message = "物流方名称不能为空")
        @Size(max = 128, message = "物流方名称不能超过128个字符")
        String carrierName,
        String contactName,
        String contactPhone,
        String vehicleType,
        List<@Valid VehicleItem> vehicles,
        String priceMode,
        @NotNull(message = "默认结算主体不能为空")
        Long defaultSettlementCompanyId,
        @NotBlank(message = "状态不能为空")
        String status,
        String remark
) {
}
