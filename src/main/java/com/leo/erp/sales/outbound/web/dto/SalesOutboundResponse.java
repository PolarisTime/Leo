package com.leo.erp.sales.outbound.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOutboundResponse(
        Long id,
        String outboundNo,
        String salesOrderNo,
        Long customerId,
        String customerName,
        Long projectId,
        String projectName,
        Long warehouseId,
        String warehouseName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate outboundDate,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        boolean deletedFlag,
        String remark,
        List<SalesOutboundItemResponse> items,
        /** 项目简称(取自 md_project.project_name_abbr); 项目缺失/已删除/简称为空 → null, 由前端回退显示全称。 */
        String projectNameAbbr
) {

    /**
     * 补「项目简称」: 由查询侧 {@code ProjectNameAbbrResolver} 统一回填, 不参与 MapStruct 映射。
     *
     * <p>刻意声明为 static: 单参实例方法会被 MapStruct 识别为"流式 setter"并要求提供映射目标。</p>
     */
    public static SalesOutboundResponse withProjectNameAbbr(SalesOutboundResponse source, String projectNameAbbr) {
        return new SalesOutboundResponse(
                source.id(), source.outboundNo(), source.salesOrderNo(), source.customerId(),
                source.customerName(), source.projectId(), source.projectName(), source.warehouseId(),
                source.warehouseName(), source.settlementCompanyId(), source.settlementCompanyName(),
                source.outboundDate(), source.totalWeight(), source.totalAmount(), source.status(),
                source.deletedFlag(), source.remark(), source.items(), projectNameAbbr);
    }
}
