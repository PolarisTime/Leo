package com.leo.erp.finance.receipt.mapper;

import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReceiptMapper {

    ReceiptResponse toResponse(Receipt receipt);
}
