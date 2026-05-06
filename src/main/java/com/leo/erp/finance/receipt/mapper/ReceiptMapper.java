package com.leo.erp.finance.receipt.mapper;

import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReceiptMapper {

    @Mapping(target = "items", ignore = true)
    ReceiptResponse toResponse(Receipt receipt);
}
