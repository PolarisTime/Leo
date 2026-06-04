package com.leo.erp.finance.ledgeradjustment.mapper;

import com.leo.erp.common.mapper.StrictMapperConfig;
import com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentResponse;
import org.mapstruct.Mapper;

@Mapper(config = StrictMapperConfig.class)
public interface LedgerAdjustmentMapper {

    LedgerAdjustmentResponse toResponse(LedgerAdjustment adjustment);
}
