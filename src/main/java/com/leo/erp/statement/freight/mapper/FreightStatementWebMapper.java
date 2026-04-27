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
import org.springframework.stereotype.Component;

@Component
public class FreightStatementWebMapper {

    private final AttachmentWebMapper attachmentWebMapper;

    public FreightStatementWebMapper(AttachmentWebMapper attachmentWebMapper) {
        this.attachmentWebMapper = attachmentWebMapper;
    }

    public FreightStatementCommand toCommand(FreightStatementRequest request) {
        return new FreightStatementCommand(
                request.statementNo(),
                request.sourceBillNos(),
                request.carrierName(),
                request.startDate(),
                request.endDate(),
                request.totalWeight(),
                request.totalFreight(),
                request.paidAmount(),
                request.unpaidAmount(),
                request.status(),
                request.signStatus(),
                request.attachment(),
                request.attachmentIds(),
                request.remark(),
                request.items().stream().map(this::toItemCommand).toList()
        );
    }

    public FreightStatementResponse toResponse(FreightStatementView view) {
        return new FreightStatementResponse(
                view.id(),
                view.statementNo(),
                view.sourceBillNos(),
                view.carrierName(),
                view.startDate(),
                view.endDate(),
                view.totalWeight(),
                view.totalFreight(),
                view.paidAmount(),
                view.unpaidAmount(),
                view.status(),
                view.signStatus(),
                view.attachment(),
                attachmentWebMapper.toResponses(view.attachments()),
                view.remark(),
                view.items().stream().map(this::toItemResponse).toList()
        );
    }

    private FreightStatementItemCommand toItemCommand(FreightBillItemRequest item) {
        return new FreightStatementItemCommand(
                item.sourceNo(),
                item.customerName(),
                item.projectName(),
                item.materialCode(),
                item.materialName(),
                item.brand(),
                item.category(),
                item.material(),
                item.spec(),
                item.length(),
                item.quantity(),
                item.quantityUnit(),
                item.pieceWeightTon(),
                item.piecesPerBundle(),
                item.batchNo(),
                item.weightTon(),
                item.warehouseName()
        );
    }

    private FreightBillItemResponse toItemResponse(FreightStatementItemView item) {
        return new FreightBillItemResponse(
                item.id(),
                item.lineNo(),
                item.sourceNo(),
                item.customerName(),
                item.projectName(),
                item.materialCode(),
                item.materialName(),
                item.brand(),
                item.category(),
                item.material(),
                item.spec(),
                item.length(),
                item.quantity(),
                item.quantityUnit(),
                item.pieceWeightTon(),
                item.piecesPerBundle(),
                item.batchNo(),
                item.weightTon(),
                item.warehouseName()
        );
    }
}
