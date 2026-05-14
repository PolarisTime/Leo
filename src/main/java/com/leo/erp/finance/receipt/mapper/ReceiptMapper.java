package com.leo.erp.finance.receipt.mapper;

import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface ReceiptMapper {

    @Mapping(target = "items", ignore = true)
    ReceiptResponse toResponse(Receipt receipt);
}
