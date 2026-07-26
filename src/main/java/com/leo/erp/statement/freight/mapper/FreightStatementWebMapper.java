package com.leo.erp.statement.freight.mapper;

import com.leo.erp.attachment.api.AttachmentView;
import com.leo.erp.statement.freight.service.FreightStatementCommand;
import com.leo.erp.statement.freight.service.FreightStatementItemCommand;
import com.leo.erp.statement.freight.service.FreightStatementItemView;
import com.leo.erp.statement.freight.service.FreightStatementView;
import com.leo.erp.statement.freight.web.dto.FreightStatementRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementAttachmentResponse;
import com.leo.erp.statement.freight.web.dto.FreightStatementItemRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementItemResponse;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface FreightStatementWebMapper {

    @Mapping(target = "settlementCompanyId", source = "settlementCompanyId")
    @Mapping(target = "settlementCompanyName", source = "settlementCompanyName")
    FreightStatementCommand toCommand(FreightStatementRequest request);

    FreightStatementItemCommand toItemCommand(FreightStatementItemRequest item);

    @Mapping(target = "attachments", source = "attachments")
    FreightStatementResponse toResponse(FreightStatementView view);

    FreightStatementAttachmentResponse toAttachmentResponse(AttachmentView view);

    @Mapping(target = "sourceSalesOrderItemId", ignore = true)
    FreightStatementItemResponse toItemResponse(FreightStatementItemView item);
}
