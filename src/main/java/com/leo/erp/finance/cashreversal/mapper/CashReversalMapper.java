package com.leo.erp.finance.cashreversal.mapper;

import com.leo.erp.common.mapper.StrictMapperConfig;
import com.leo.erp.finance.cashreversal.domain.entity.CashReversal;
import com.leo.erp.finance.cashreversal.web.dto.CashReversalResponse;
import org.mapstruct.Mapper;

@Mapper(config = StrictMapperConfig.class)
public interface CashReversalMapper {

    CashReversalResponse toResponse(CashReversal reversal);
}
