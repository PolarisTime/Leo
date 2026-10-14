package com.leo.erp.logistics.bill.mapper;

import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface FreightBillMapper {

    @Mapping(target = "items", ignore = true)
    @Mapping(target = "chargeItems", ignore = true)
    @Mapping(target = "totalExpenseAmount", ignore = true)
    // 「按吨位计算的金额」是只读派生值: 与计价口径同一处计算(列表与详情同形), 不让各调用方各算一遍。
    @Mapping(target = "tonnageFreightAmount",
            expression = "java(com.leo.erp.common.support.TradeItemCalculator.calculateAmount("
                    + "bill.getTotalWeight(), bill.getUnitPrice()))")
    FreightBillResponse toResponse(FreightBill bill);
}
