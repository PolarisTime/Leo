package com.leo.erp.sales.contract.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售合同新增/整体替换请求。
 *
 * <p>合同编号为空时由服务端按雪花 ID 自动生成; 客户/项目名称由 master.api 端口回填快照, 不接受客户端伪造。</p>
 */
public record SalesContractRequest(
        @Size(max = 64, message = "合同编号长度不能超过64个字符") String contractNo,
        @NotBlank(message = "合同名称不能为空")
        @Size(max = 128, message = "合同名称长度不能超过128个字符") String name,
        @NotNull(message = "客户不能为空") @Positive(message = "客户ID必须为正整数") Long customerId,
        @NotNull(message = "项目不能为空") @Positive(message = "项目ID必须为正整数") Long projectId,
        @NotNull(message = "签订日期不能为空") LocalDate signDate,
        LocalDate startDate,
        LocalDate endDate,
        @NotNull(message = "合同总金额不能为空")
        @DecimalMin(value = "0", message = "合同总金额不能为负")
        @Digits(integer = 16, fraction = 2, message = "合同总金额精度不合法") BigDecimal totalAmount,
        @NotNull(message = "合同总吨位不能为空")
        @DecimalMin(value = "0", message = "合同总吨位不能为负")
        @Digits(integer = 10, fraction = 8, message = "合同总吨位精度不合法") BigDecimal totalTonnage,
        @Pattern(regexp = "^(草稿|审核|签发|归档|作废)?$", message = "销售合同状态不合法")
        String status,
        @Size(max = 255, message = "备注长度不能超过255个字符") String remark
) {
}
