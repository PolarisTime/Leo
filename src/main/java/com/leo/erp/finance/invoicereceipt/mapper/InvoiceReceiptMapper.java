package com.leo.erp.finance.invoicereceipt.mapper;

import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface InvoiceReceiptMapper {

    @Mapping(target = "items", ignore = true)
    InvoiceReceiptResponse toResponse(InvoiceReceipt invoiceReceipt);
}
