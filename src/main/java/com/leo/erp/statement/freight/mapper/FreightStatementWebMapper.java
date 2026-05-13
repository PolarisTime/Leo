package com.leo.erp.statement.freight.mapper;

import com.leo.erp.attachment.mapper.AttachmentWebMapper;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemResponse;
import com.leo.erp.statement.freight.service.FreightStatementCommand;
import com.leo.erp.statement.freight.service.FreightStatementItemCommand;
import com.leo.erp.statement.freight.service.FreightStatementItemView;
import com.leo.erp.statement.freight.service.FreightStatementView;
import com.leo.erp.statement.freight.web.dto.FreightStatementRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = AttachmentWebMapper.class)
public interface FreightStatementWebMapper {

    FreightStatementCommand toCommand(FreightStatementRequest request);

    FreightStatementItemCommand toItemCommand(FreightBillItemRequest item);

    @Mapping(target = "attachments", source = "attachments")
    FreightStatementResponse toResponse(FreightStatementView view);

    FreightBillItemResponse toItemResponse(FreightStatementItemView item);
}
