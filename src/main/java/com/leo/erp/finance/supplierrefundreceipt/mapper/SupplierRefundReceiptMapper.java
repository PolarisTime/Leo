package com.leo.erp.finance.supplierrefundreceipt.mapper;

import com.leo.erp.finance.supplierrefundreceipt.domain.entity.SupplierRefundReceipt;
import com.leo.erp.finance.supplierrefundreceipt.web.dto.SupplierRefundReceiptResponse;
import org.springframework.stereotype.Component;

@Component
public class SupplierRefundReceiptMapper {

    public SupplierRefundReceiptResponse toResponse(SupplierRefundReceipt receipt) {
        return new SupplierRefundReceiptResponse(
                receipt.getId(),
                receipt.getRefundReceiptNo(),
                receipt.getPurchaseRefundId(),
                receipt.getSupplierId(),
                receipt.getSupplierCode(),
                receipt.getSupplierName(),
                receipt.getSettlementCompanyId(),
                receipt.getSettlementCompanyName(),
                receipt.getReceiptDate(),
                receipt.getReceiptMethod(),
                receipt.getAmount(),
                receipt.getStatus(),
                receipt.isDeletedFlag(),
                receipt.getOperatorName(),
                receipt.getRemark()
        );
    }
}
