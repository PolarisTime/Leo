package com.leo.erp.finance.invoiceissue.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceIssueRequest(
        @NotBlank(message = "开票单号不能为空")
        String issueNo,
        @NotBlank(message = "发票号码不能为空")
        String invoiceNo,
        String sourceSalesOrderNos,
        @NotBlank(message = "客户不能为空")
        String customerName,
        @NotBlank(message = "项目不能为空")
        String projectName,
        @NotNull(message = "发票日期不能为空")
        LocalDate invoiceDate,
        @NotBlank(message = "发票类型不能为空")
        String invoiceType,
        @NotNull(message = "金额不能为空")
        @DecimalMin(value = "0.00", message = "金额不能小于0")
        BigDecimal amount,
        @NotNull(message = "税额不能为空")
        @DecimalMin(value = "0.00", message = "税额不能小于0")
        BigDecimal taxAmount,
        @NotBlank(message = "状态不能为空")
        String status,
        @NotBlank(message = "经办人不能为空")
        String operatorName,
        String remark,
        @Valid @NotEmpty(message = "请至少填写一条开票明细")
        List<InvoiceIssueItemRequest> items
) {
}
