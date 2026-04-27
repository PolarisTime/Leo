package com.leo.erp.finance.invoiceissue.mapper;

import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InvoiceIssueMapper {

    @Mapping(target = "items", ignore = true)
    InvoiceIssueResponse toResponse(InvoiceIssue invoiceIssue);
}
