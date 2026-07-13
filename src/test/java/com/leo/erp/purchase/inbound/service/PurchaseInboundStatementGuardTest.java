package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseInboundStatementGuardTest {

    @Test
    void shouldRejectReopeningCompletedInboundReferencedBySupplierStatement() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundStatementGuard guard = new PurchaseInboundStatementGuard(repository);
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(101L);
        when(repository.findMatchingOccupiedSourceInboundIdsExcludingCurrentStatement(List.of(101L), null))
                .thenReturn(List.of(101L));

        assertThatThrownBy(() -> guard.assertStatusTransitionAllowed(
                inbound,
                StatusConstants.INBOUND_COMPLETED,
                StatusConstants.DRAFT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商对账单")
                .hasMessageContaining("反审核");

        verify(repository).findMatchingOccupiedSourceInboundIdsExcludingCurrentStatement(List.of(101L), null);
    }
}
