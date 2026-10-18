package com.leo.erp.master.carrier.web.dto;

import com.leo.erp.common.support.ValidationMessages;
import com.leo.erp.common.validation.SnowflakeId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CarrierRequest(
        @NotBlank(message = "物流商编码不能为空")
        @Size(max = 64, message = "物流商编码长度不能超过64")
        String carrierCode,
        @NotBlank(message = "物流方名称不能为空")
        @Size(max = 128, message = "物流方名称不能超过128个字符")
        String carrierName,
        @Size(max = 32, message = "联系人长度不能超过32")
        String contactName,
        @Size(max = 20, message = "联系电话长度不能超过20")
        String contactPhone,
        @Size(max = 64, message = "车辆类型长度不能超过64")
        String vehicleType,
        @Valid List<VehicleItem> vehicles,
        @Size(max = 32, message = "计价方式长度不能超过32")
        String priceMode,
        @NotNull(message = "默认结算主体不能为空")
        @SnowflakeId Long defaultSettlementCompanyId,
        @NotBlank(message = ValidationMessages.STATUS_REQUIRED)
        @Size(max = 16, message = "状态长度不能超过16")
        String status,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark
) {
}
