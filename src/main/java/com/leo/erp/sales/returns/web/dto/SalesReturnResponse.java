package com.leo.erp.sales.returns.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesReturnResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String returnNo,
        String salesOrderNo,
        @JsonSerialize(using = ToStringSerializer.class) Long customerId,
        String customerName,
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        String projectName,
        @JsonSerialize(using = ToStringSerializer.class) Long warehouseId,
        String warehouseName,
        @JsonSerialize(using = ToStringSerializer.class) Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate returnDate,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        boolean deletedFlag,
        String remark,
        List<SalesReturnItemResponse> items,
        /** 项目简称(取自 md_project.project_name_abbr); 项目缺失/已删除/简称为空 → null, 由前端回退显示全称。 */
        String projectNameAbbr
) {

    /**
     * 补「项目简称」: 由查询侧 {@code ProjectNameAbbrResolver} 统一回填。
     *
     * <p>刻意声明为 static: 单参实例方法会被 MapStruct 识别为"流式 setter"并要求提供映射目标。</p>
     */
    public static SalesReturnResponse withProjectNameAbbr(SalesReturnResponse source, String projectNameAbbr) {
        return new SalesReturnResponse(
                source.id(), source.returnNo(), source.salesOrderNo(), source.customerId(),
                source.customerName(), source.projectId(), source.projectName(), source.warehouseId(),
                source.warehouseName(), source.settlementCompanyId(), source.settlementCompanyName(),
                source.returnDate(), source.totalWeight(), source.totalAmount(), source.status(),
                source.deletedFlag(), source.remark(), source.items(), projectNameAbbr);
    }
}
