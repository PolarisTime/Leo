package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PurchaseInboundStatementGuardTest {

    @Test
    void shouldRejectReopeningCompletedInboundReferencedBySupplierStatement() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PurchaseInboundStatementGuard guard = new PurchaseInboundStatementGuard(repository, lockService);
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

        InOrder flow = inOrder(lockService, repository);
        flow.verify(lockService).lockDocumentSources(List.of(101L), List.of(), List.of(), List.of());
        flow.verify(repository).findMatchingOccupiedSourceInboundIdsExcludingCurrentStatement(List.of(101L), null);
    }

    @Test
    void shouldRejectDeletingInboundReferencedBySupplierStatement() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PurchaseInboundStatementGuard guard = new PurchaseInboundStatementGuard(repository, lockService);
        PurchaseInbound inbound = inboundWithItem(101L, 201L);
        when(repository.findMatchingOccupiedSourceInboundIdsExcludingCurrentStatement(List.of(101L), null))
                .thenReturn(List.of(101L));

        assertThatThrownBy(() -> guard.assertMutable(inbound, "删除"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商对账单")
                .hasMessageContaining("不能删除");

        verify(lockService).lockDocumentSources(List.of(101L), List.of(), List.of(), List.of());
    }

    @Test
    void shouldCheckStatementWhenSourceLineQuantityChanges() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PurchaseInboundStatementGuard guard = new PurchaseInboundStatementGuard(repository, lockService);
        PurchaseInbound inbound = inboundWithItem(101L, 201L);
        when(repository.findMatchingOccupiedSourceInboundIdsExcludingCurrentStatement(List.of(101L), null))
                .thenReturn(List.of(101L));

        assertThatThrownBy(() -> guard.assertSourceLineMutationAllowed(
                inbound,
                List.of(itemRequest(201L, 2)),
                "修改"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商对账单")
                .hasMessageContaining("不能修改");
    }

    @Test
    void shouldAllowHeaderOnlyUpdateWithoutCheckingStatement() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PurchaseInboundStatementGuard guard = new PurchaseInboundStatementGuard(repository, lockService);
        PurchaseInbound inbound = inboundWithItem(101L, 201L);

        guard.assertSourceLineMutationAllowed(inbound, List.of(itemRequest(201L, 1)), "修改");

        verify(lockService, never()).lockDocumentSources(List.of(101L), List.of(), List.of(), List.of());
        verifyNoInteractions(repository);
    }

    private PurchaseInbound inboundWithItem(Long inboundId, Long itemId) {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(inboundId);
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(itemId);
        item.setPurchaseInbound(inbound);
        item.setLineNo(1);
        item.setMaterialId(301L);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setLength("12m");
        item.setUnit("吨");
        item.setSourcePurchaseOrderItemId(401L);
        item.setWarehouseId(501L);
        item.setWarehouseName("一号库");
        item.setSettlementMode("理算");
        item.setBatchNo("B1");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("0.10000000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("0.10000000"));
        item.setWeighWeightTon(null);
        item.setWeightAdjustmentTon(BigDecimal.ZERO);
        item.setWeightAdjustmentAmount(BigDecimal.ZERO);
        item.setUnitPrice(new BigDecimal("4000.00"));
        item.setAmount(new BigDecimal("400.00"));
        inbound.setItems(new ArrayList<>(List.of(item)));
        return inbound;
    }

    private PurchaseInboundItemRequest itemRequest(Long itemId, Integer quantity) {
        return new PurchaseInboundItemRequest(
                itemId,
                301L,
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                401L,
                501L,
                "一号库",
                "理算",
                "B1",
                quantity,
                "件",
                new BigDecimal("0.10000000"),
                1,
                new BigDecimal("0.10000000"),
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("4000.00"),
                new BigDecimal("400.00")
        );
    }
}
