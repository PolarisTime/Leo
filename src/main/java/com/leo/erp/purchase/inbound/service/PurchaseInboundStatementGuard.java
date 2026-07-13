package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PurchaseInboundStatementGuard {

    private final SupplierStatementRepository supplierStatementRepository;

    public PurchaseInboundStatementGuard(SupplierStatementRepository supplierStatementRepository) {
        this.supplierStatementRepository = supplierStatementRepository;
    }

    public void assertStatusTransitionAllowed(
            PurchaseInbound inbound,
            String currentStatus,
            String nextStatus
    ) {
        if (!StatusConstants.INBOUND_COMPLETED.equals(currentStatus)
                || !StatusConstants.DRAFT.equals(nextStatus)
                || inbound.getId() == null) {
            return;
        }
        List<Long> occupiedInboundIds =
                supplierStatementRepository.findMatchingOccupiedSourceInboundIdsExcludingCurrentStatement(
                        List.of(inbound.getId()),
                        null
                );
        if (!occupiedInboundIds.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购入库已被供应商对账单引用，请先处理供应商对账单后再反审核"
            );
        }
    }
}
