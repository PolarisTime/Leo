package com.leo.erp.purchase.refund.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.payment.service.PaymentPurchasePrepaymentService;
import com.leo.erp.finance.supplierrefundreceipt.service.SupplierRefundReceiptGuard;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.service.PurchaseInboundCompletionSyncService;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemRepository;
import com.leo.erp.purchase.refund.domain.entity.PurchaseRefund;
import com.leo.erp.purchase.refund.domain.entity.PurchaseRefundItem;
import com.leo.erp.purchase.refund.mapper.PurchaseRefundMapper;
import com.leo.erp.purchase.refund.repository.PurchaseRefundRepository;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundRequest;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundPreviewResponse;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundSourceCandidateResponse;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseRefundServiceTest {

    @Test
    void shouldPageOnlyPositiveSourceCandidatesWithoutActiveRefund() {
        Fixture fixture = fixture();
        PurchaseOrder alreadyRefunded = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        PurchaseOrder weightDifference = sourceOrder(2L, 201L, 18, "35.23800000", "3250.00");
        weightDifference.setOrderNo("PO-002");
        weightDifference.setStatus(StatusConstants.PURCHASE_COMPLETED);
        PurchaseOrder fullySettled = sourceOrder(3L, 301L, 10, "20.00000000", "3000.00");
        fullySettled.setOrderNo("PO-003");
        fullySettled.setStatus(StatusConstants.PURCHASE_COMPLETED);
        PurchaseOrder draft = sourceOrder(4L, 401L, 10, "20.00000000", "3000.00");
        draft.setStatus(StatusConstants.DRAFT);
        when(fixture.purchaseOrderRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(1);
            assertThat(pageable.getPageSize()).isLessThanOrEqualTo(200);
            return new PageImpl<>(
                    List.of(alreadyRefunded, weightDifference, fullySettled, draft),
                    PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort()),
                    4
            );
        });
        when(fixture.repository.findActiveSourcePurchaseOrderIdsBySourcePurchaseOrderIdIn(any()))
                .thenReturn(List.of(1L));
        when(fixture.inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(any()))
                .thenReturn(List.of(
                        inboundItem(201L, 18, "36.00000000", "35.23800000", "3250.00"),
                        inboundItem(301L, 10, "20.00000000", "20.00000000", "3000.00")
                ));

        Page<PurchaseRefundSourceCandidateResponse> result = fixture.service.sourceCandidates(
                new PageQuery(0, 1, null, null),
                PageFilter.of(null, null, null, null, null, null)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        PurchaseRefundSourceCandidateResponse candidate = result.getContent().getFirst();
        assertThat(candidate.id()).isEqualTo(2L);
        assertThat(candidate.orderNo()).isEqualTo("PO-002");
        assertThat(candidate.status()).isEqualTo(StatusConstants.PURCHASE_COMPLETED);
        assertThat(candidate.refundableQuantity()).isZero();
        assertThat(candidate.refundableWeight()).isEqualByComparingTo("0.76200000");
        assertThat(candidate.refundableAmount()).isEqualByComparingTo("2476.50");
        verify(fixture.purchaseOrderRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldPreviewAuthoritativeRefundWithoutLockingOrSaving() {
        Fixture fixture = fixture();
        PurchaseOrder order = sourceOrder(1L, 101L, 18, "35.23800000", "3250.00");
        order.setSupplierId(501L);
        order.getItems().getFirst().setMaterialId(601L);
        order.getItems().getFirst().setWarehouseId(701L);
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(101L)))
                .thenReturn(List.of(inboundItem(101L, 18, "36.00000000", "35.23800000", "3250.00")));

        PurchaseRefundPreviewResponse preview = fixture.service.preview(1L);

        assertThat(preview.sourcePurchaseOrderId()).isEqualTo(1L);
        assertThat(PurchaseRefundPreviewResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .contains("supplierId");
        assertThat(preview.items().getFirst().getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .contains("materialId", "warehouseId", "batchNoNormalized");
        assertThat(ReflectionTestUtils.getField(preview, "supplierId")).isEqualTo(501L);
        assertThat(ReflectionTestUtils.getField(preview.items().getFirst(), "materialId")).isEqualTo(601L);
        assertThat(ReflectionTestUtils.getField(preview.items().getFirst(), "warehouseId")).isEqualTo(701L);
        assertThat(preview.purchaseOrderNo()).isEqualTo("PO-001");
        assertThat(preview.supplierCode()).isEqualTo("SUP-001");
        assertThat(preview.totalQuantity()).isZero();
        assertThat(preview.totalWeight()).isEqualByComparingTo("0.76200000");
        assertThat(preview.totalAmount()).isEqualByComparingTo("2476.50");
        assertThat(preview.items()).hasSize(1);
        assertThat(preview.items().getFirst().id()).isNull();
        assertThat(preview.items().getFirst().weightTon()).isEqualByComparingTo("0.76200000");
        verify(fixture.lockService, never()).lockTradeItemSources(any(), any(), any());
        verify(fixture.repository, never()).save(any());
    }

    @Test
    void shouldLockSourceItemsBeforeCheckingAllocationAndBuildAuthoritativeRefund() {
        Fixture fixture = fixture();
        PurchaseOrder order = sourceOrder(1L, 101L, 18, "36.00000000", "3250.00");
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalse(1L)).thenReturn(false);
        when(fixture.inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(101L)))
                .thenReturn(List.of(inboundItem(101L, 18, "36.00000000", "35.23800000", "3250.00")));
        when(fixture.repository.save(any(PurchaseRefund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.create(request(1L, StatusConstants.AUDITED));

        InOrder flow = inOrder(
                fixture.purchaseOrderItemRepository,
                fixture.lockService,
                fixture.purchaseOrderRepository,
                fixture.repository,
                fixture.inboundItemRepository
        );
        flow.verify(fixture.purchaseOrderItemRepository).findActiveIdsByPurchaseOrderId(1L);
        flow.verify(fixture.lockService).lockTradeItemSources(List.of(101L), List.of(), List.of());
        flow.verify(fixture.purchaseOrderRepository).findByIdAndDeletedFlagFalse(1L);
        flow.verify(fixture.repository).existsBySourcePurchaseOrderIdAndDeletedFlagFalse(1L);
        flow.verify(fixture.inboundItemRepository).findAllActiveBySourcePurchaseOrderItemIds(List.of(101L));

        ArgumentCaptor<PurchaseRefund> captor = ArgumentCaptor.forClass(PurchaseRefund.class);
        verify(fixture.repository).save(captor.capture());
        PurchaseRefund saved = captor.getValue();
        assertThat(saved.getSourcePurchaseOrderId()).isEqualTo(1L);
        assertThat(saved.getPurchaseOrderNo()).isEqualTo("PO-001");
        assertThat(saved.getSupplierName()).isEqualTo("供应商A");
        assertThat(saved.getSupplierCode()).isEqualTo("SUP-001");
        assertThat(saved.getTotalQuantity()).isZero();
        assertThat(saved.getTotalWeight()).isEqualByComparingTo("0.76200000");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("2476.50");
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().getFirst().getQuantity()).isZero();
        assertThat(saved.getItems().getFirst().getWeightTon()).isEqualByComparingTo("0.76200000");
        assertThat(order.getStatus()).isEqualTo(StatusConstants.PURCHASE_COMPLETED);
    }

    @Test
    void shouldRejectSecondActiveRefundAfterAcquiringSourceLock() {
        Fixture fixture = fixture();
        PurchaseOrder order = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalse(1L)).thenReturn(true);

        assertThatThrownBy(() -> fixture.service.create(request(1L, StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在未删除的采购退款单");

        InOrder flow = inOrder(fixture.lockService, fixture.repository);
        flow.verify(fixture.lockService).lockTradeItemSources(List.of(101L), List.of(), List.of());
        flow.verify(fixture.repository).existsBySourcePurchaseOrderIdAndDeletedFlagFalse(1L);
        verify(fixture.inboundItemRepository, never()).findAllActiveBySourcePurchaseOrderItemIds(any());
        verify(fixture.repository, never()).save(any());
    }

    @Test
    void shouldRejectRefundWhenThereIsNoPositiveQuantityOrWeight() {
        Fixture fixture = fixture();
        PurchaseOrder order = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalse(1L)).thenReturn(false);
        when(fixture.inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(101L)))
                .thenReturn(List.of(inboundItem(101L, 10, "20.00000000", "20.00000000", "3000.00")));

        assertThatThrownBy(() -> fixture.service.create(request(1L, StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可退款的数量或重量");

        verify(fixture.repository, never()).save(any());
    }

    @Test
    void shouldRestorePurchaseOrderToAuditedWhenRefundIsUnauditedBeforeFullInbound() {
        Fixture fixture = fixture();
        PurchaseOrder order = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        order.setStatus(StatusConstants.PURCHASE_COMPLETED);
        PurchaseRefund refund = refund(900L, 1L, StatusConstants.AUDITED, 101L, 6, "12.00000000");
        when(fixture.repository.findByIdAndDeletedFlagFalse(900L)).thenReturn(Optional.of(refund));
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalseAndIdNot(1L, 900L))
                .thenReturn(false);
        when(fixture.inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(101L)))
                .thenReturn(List.of(inboundItem(101L, 4, "8.00000000", "8.00000000", "3000.00")));
        when(fixture.repository.save(any(PurchaseRefund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.updateStatus(900L, StatusConstants.DRAFT);

        verify(fixture.lockService).lockTradeItemSources(List.of(101L), List.of(), List.of());
        assertThat(refund.getStatus()).isEqualTo(StatusConstants.DRAFT);
        assertThat(order.getStatus()).isEqualTo(StatusConstants.AUDITED);
        assertThat(refund.getTotalQuantity()).isEqualTo(6);
        assertThat(refund.getTotalWeight()).isEqualByComparingTo("12.00000000");
    }

    @Test
    void shouldKeepPurchaseOrderCompletedWhenRefundIsUnauditedAfterFullInbound() {
        Fixture fixture = fixture();
        PurchaseOrder order = sourceOrder(1L, 101L, 18, "35.23800000", "3250.00");
        order.setStatus(StatusConstants.PURCHASE_COMPLETED);
        PurchaseRefund refund = refund(900L, 1L, StatusConstants.AUDITED, 101L, 0, "0.76200000");
        when(fixture.repository.findByIdAndDeletedFlagFalse(900L)).thenReturn(Optional.of(refund));
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalseAndIdNot(1L, 900L))
                .thenReturn(false);
        when(fixture.inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(101L)))
                .thenReturn(List.of(inboundItem(101L, 18, "36.00000000", "35.23800000", "3250.00")));
        when(fixture.repository.save(any(PurchaseRefund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.updateStatus(900L, StatusConstants.DRAFT);

        assertThat(refund.getStatus()).isEqualTo(StatusConstants.DRAFT);
        assertThat(order.getStatus()).isEqualTo(StatusConstants.PURCHASE_COMPLETED);
        assertThat(refund.getTotalQuantity()).isZero();
        assertThat(refund.getTotalWeight()).isEqualByComparingTo("0.76200000");
    }

    @Test
    void shouldRejectAuditingWhenInboundChangedAndRefundIsNoLongerPositive() {
        Fixture fixture = fixture();
        PurchaseOrder order = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        PurchaseRefund refund = refund(900L, 1L, StatusConstants.DRAFT, 101L, 10, "20.00000000");
        when(fixture.repository.findByIdAndDeletedFlagFalse(900L)).thenReturn(Optional.of(refund));
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalseAndIdNot(1L, 900L))
                .thenReturn(false);
        when(fixture.inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(101L)))
                .thenReturn(List.of(inboundItem(101L, 10, "20.00000000", "20.00000000", "3000.00")));

        assertThatThrownBy(() -> fixture.service.updateStatus(900L, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可退款的数量或重量");

        assertThat(refund.getStatus()).isEqualTo(StatusConstants.DRAFT);
        verify(fixture.repository, never()).save(any());
    }

    @Test
    void shouldKeepBoundSupplierCodeStableAcrossAuditAndUnaudit() {
        Fixture fixture = fixture();
        PurchaseOrder order = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        PurchaseRefund refund = refund(900L, 1L, StatusConstants.DRAFT, 101L, 6, "12.00000000");
        refund.setSupplierCode("SUP-STABLE");
        refund.setSupplierName("供应商A");
        Supplier boundSupplier = new Supplier();
        boundSupplier.setSupplierCode("SUP-STABLE");
        boundSupplier.setSupplierName("供应商A");
        when(fixture.repository.findByIdAndDeletedFlagFalse(900L)).thenReturn(Optional.of(refund));
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalseAndIdNot(1L, 900L))
                .thenReturn(false);
        when(fixture.inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(101L)))
                .thenReturn(List.of(inboundItem(101L, 4, "8.00000000", "8.00000000", "3000.00")));
        when(fixture.supplierRepository.findBySupplierCodeAndDeletedFlagFalse("SUP-STABLE"))
                .thenReturn(Optional.of(boundSupplier));
        when(fixture.repository.save(any(PurchaseRefund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.updateStatus(900L, StatusConstants.AUDITED);
        fixture.service.updateStatus(900L, StatusConstants.DRAFT);

        assertThat(refund.getSupplierCode()).isEqualTo("SUP-STABLE");
        assertThat(refund.getSupplierName()).isEqualTo("供应商A");
        verify(fixture.supplierRepository, org.mockito.Mockito.times(2))
                .findBySupplierCodeAndDeletedFlagFalse("SUP-STABLE");
    }

    @Test
    void shouldRejectAuditWhenBoundSupplierCodeIsNoLongerValid() {
        Fixture fixture = fixture();
        PurchaseOrder order = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        PurchaseRefund refund = refund(900L, 1L, StatusConstants.DRAFT, 101L, 6, "12.00000000");
        refund.setSupplierCode("SUP-MISSING");
        refund.setSupplierName("供应商A");
        when(fixture.repository.findByIdAndDeletedFlagFalse(900L)).thenReturn(Optional.of(refund));
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalseAndIdNot(1L, 900L))
                .thenReturn(false);
        when(fixture.inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(101L)))
                .thenReturn(List.of(inboundItem(101L, 4, "8.00000000", "8.00000000", "3000.00")));
        when(fixture.supplierRepository.findBySupplierCodeAndDeletedFlagFalse("SUP-MISSING"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service.updateStatus(900L, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商编码")
                .hasMessageContaining("失效");

        assertThat(refund.getStatus()).isEqualTo(StatusConstants.DRAFT);
        assertThat(refund.getSupplierCode()).isEqualTo("SUP-MISSING");
        verify(fixture.repository, never()).save(any());
    }

    @Test
    void shouldRejectCreateWhenResolvedSupplierCodeIsBlank() {
        Fixture fixture = fixture();
        PurchaseOrder order = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        Supplier supplierWithoutCode = new Supplier();
        supplierWithoutCode.setSupplierCode(" ");
        supplierWithoutCode.setSupplierName("供应商A");
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalse(1L)).thenReturn(false);
        when(fixture.inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(101L)))
                .thenReturn(List.of(inboundItem(101L, 4, "8.00000000", "8.00000000", "3000.00")));
        when(fixture.supplierRepository
                .findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商A"))
                .thenReturn(Optional.of(supplierWithoutCode));

        assertThatThrownBy(() -> fixture.service.create(request(1L, StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商编码");

        verify(fixture.repository, never()).save(any());
    }

    @Test
    void shouldLockOldAndNewSourceItemsWhenDraftChangesPurchaseOrder() {
        Fixture fixture = fixture();
        PurchaseOrder oldOrder = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        PurchaseOrder newOrder = sourceOrder(2L, 201L, 8, "16.00000000", "3100.00");
        newOrder.setOrderNo("PO-002");
        PurchaseRefund existing = refund(900L, 1L, StatusConstants.DRAFT, 101L, 10, "20.00000000");
        when(fixture.repository.findByIdAndDeletedFlagFalse(900L)).thenReturn(Optional.of(existing));
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(oldOrder));
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(2L)).thenReturn(Optional.of(newOrder));
        when(fixture.repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalseAndIdNot(2L, 900L))
                .thenReturn(false);
        when(fixture.inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(201L)))
                .thenReturn(List.of());
        when(fixture.repository.save(any(PurchaseRefund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.update(900L, request(2L, StatusConstants.DRAFT));

        verify(fixture.lockService).lockTradeItemSources(List.of(101L, 201L), List.of(), List.of());
        assertThat(existing.getSourcePurchaseOrderId()).isEqualTo(2L);
        assertThat(existing.getPurchaseOrderNo()).isEqualTo("PO-002");
    }

    @Test
    void shouldLockSourceItemsBeforeDeletingDraft() {
        Fixture fixture = fixture();
        PurchaseOrder order = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        PurchaseRefund existing = refund(900L, 1L, StatusConstants.DRAFT, 101L, 10, "20.00000000");
        when(fixture.repository.findByIdAndDeletedFlagFalse(900L)).thenReturn(Optional.of(existing));
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.repository.save(any(PurchaseRefund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.delete(900L);

        InOrder flow = inOrder(fixture.lockService, fixture.repository);
        flow.verify(fixture.lockService).lockTradeItemSources(List.of(101L), List.of(), List.of());
        flow.verify(fixture.repository).save(existing);
        assertThat(existing.isDeletedFlag()).isTrue();
    }

    @Test
    void shouldRejectUnauditingAfterLockWhenSupplierRefundReceiptExists() {
        SupplierRefundReceiptGuard receiptGuard = mock(SupplierRefundReceiptGuard.class);
        Fixture fixture = fixture(receiptGuard);
        PurchaseOrder order = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        order.setStatus(StatusConstants.PURCHASE_COMPLETED);
        PurchaseRefund refund = refund(900L, 1L, StatusConstants.AUDITED, 101L, 6, "12.00000000");
        when(fixture.repository.findByIdAndDeletedFlagFalse(900L)).thenReturn(Optional.of(refund));
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        doThrow(new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "采购退款单已存在供应商退款到账单，不能反审核"
        )).when(receiptGuard).assertNoActiveReceipt(900L, "反审核");

        assertThatThrownBy(() -> fixture.service.updateStatus(900L, StatusConstants.DRAFT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商退款到账单");

        InOrder flow = inOrder(fixture.lockService, receiptGuard);
        flow.verify(fixture.lockService).lockTradeItemSources(List.of(101L), List.of(), List.of());
        flow.verify(receiptGuard).assertNoActiveReceipt(900L, "反审核");
        assertThat(refund.getStatus()).isEqualTo(StatusConstants.AUDITED);
        verify(fixture.repository, never()).save(any());
    }

    @Test
    void shouldRejectDeletingAfterLockWhenSupplierRefundReceiptExists() {
        SupplierRefundReceiptGuard receiptGuard = mock(SupplierRefundReceiptGuard.class);
        Fixture fixture = fixture(receiptGuard);
        PurchaseOrder order = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        PurchaseRefund refund = refund(900L, 1L, StatusConstants.DRAFT, 101L, 6, "12.00000000");
        when(fixture.repository.findByIdAndDeletedFlagFalse(900L)).thenReturn(Optional.of(refund));
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        doThrow(new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "采购退款单已存在供应商退款到账单，不能删除"
        )).when(receiptGuard).assertNoActiveReceipt(900L, "删除");

        assertThatThrownBy(() -> fixture.service.delete(900L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商退款到账单");

        InOrder flow = inOrder(fixture.lockService, receiptGuard);
        flow.verify(fixture.lockService).lockTradeItemSources(List.of(101L), List.of(), List.of());
        flow.verify(receiptGuard).assertNoActiveReceipt(900L, "删除");
        assertThat(refund.isDeletedFlag()).isFalse();
        verify(fixture.repository, never()).save(any());
    }

    @Test
    void shouldRejectAuditingRefundWhenSourcePurchaseOrderIsNotFullyPrepaid() {
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        Fixture fixture = fixture(prepaymentService, mock(PurchaseInboundCompletionSyncService.class));
        PurchaseOrder order = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        PurchaseRefund refund = refund(900L, 1L, StatusConstants.DRAFT, 101L, 6, "12.00000000");
        when(fixture.repository.findByIdAndDeletedFlagFalse(900L)).thenReturn(Optional.of(refund));
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalseAndIdNot(1L, 900L))
                .thenReturn(false);
        doThrow(new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "来源采购订单采购预付款未足额支付，不能审核采购退款单"
        )).when(prepaymentService).assertSourcePurchaseOrderFullyPaid(order);

        assertThatThrownBy(() -> fixture.service.updateStatus(900L, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购预付款")
                .hasMessageContaining("未足额支付");

        verify(fixture.repository, never()).save(any());
    }

    @Test
    void shouldSynchronizeInboundCompletionAfterAuditingRefund() {
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        PurchaseInboundCompletionSyncService completionSyncService =
                mock(PurchaseInboundCompletionSyncService.class);
        Fixture fixture = fixture(prepaymentService, completionSyncService);
        PurchaseOrder order = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        PurchaseRefund refund = refund(900L, 1L, StatusConstants.DRAFT, 101L, 6, "12.00000000");
        when(fixture.repository.findByIdAndDeletedFlagFalse(900L)).thenReturn(Optional.of(refund));
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalseAndIdNot(1L, 900L))
                .thenReturn(false);
        when(fixture.inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(101L)))
                .thenReturn(List.of(inboundItem(101L, 4, "8.00000000", "8.00000000", "3000.00")));
        when(fixture.repository.save(any(PurchaseRefund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.updateStatus(900L, StatusConstants.AUDITED);

        InOrder flow = inOrder(fixture.repository, completionSyncService);
        flow.verify(fixture.repository).save(refund);
        flow.verify(fixture.repository).flush();
        flow.verify(completionSyncService).synchronizeAfterPurchaseRefundStatusChange(List.of(101L));
    }

    @Test
    void shouldSynchronizeInboundCompletionAfterCreatingAuditedRefund() {
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        PurchaseInboundCompletionSyncService completionSyncService =
                mock(PurchaseInboundCompletionSyncService.class);
        Fixture fixture = fixture(prepaymentService, completionSyncService);
        PurchaseOrder order = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalse(1L)).thenReturn(false);
        when(fixture.inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(101L)))
                .thenReturn(List.of(inboundItem(101L, 4, "8.00000000", "8.00000000", "3000.00")));
        when(fixture.repository.save(any(PurchaseRefund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.create(request(1L, StatusConstants.AUDITED));

        InOrder flow = inOrder(fixture.repository, completionSyncService);
        flow.verify(fixture.repository).save(any(PurchaseRefund.class));
        flow.verify(fixture.repository).flush();
        flow.verify(completionSyncService).synchronizeAfterPurchaseRefundStatusChange(List.of(101L));
    }

    @Test
    void shouldSynchronizeInboundCompletionAfterUpdatingDraftRefundToAudited() {
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        PurchaseInboundCompletionSyncService completionSyncService =
                mock(PurchaseInboundCompletionSyncService.class);
        Fixture fixture = fixture(prepaymentService, completionSyncService);
        PurchaseOrder order = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        PurchaseRefund refund = refund(900L, 1L, StatusConstants.DRAFT, 101L, 6, "12.00000000");
        when(fixture.repository.findByIdAndDeletedFlagFalse(900L)).thenReturn(Optional.of(refund));
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalseAndIdNot(1L, 900L))
                .thenReturn(false);
        when(fixture.inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(101L)))
                .thenReturn(List.of(inboundItem(101L, 4, "8.00000000", "8.00000000", "3000.00")));
        when(fixture.repository.save(any(PurchaseRefund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.update(900L, request(1L, StatusConstants.AUDITED));

        InOrder flow = inOrder(fixture.repository, completionSyncService);
        flow.verify(fixture.repository).save(refund);
        flow.verify(fixture.repository).flush();
        flow.verify(completionSyncService).synchronizeAfterPurchaseRefundStatusChange(List.of(101L));
    }

    @Test
    void shouldSynchronizeInboundCompletionAfterUnauditingRefund() {
        PurchaseInboundCompletionSyncService completionSyncService =
                mock(PurchaseInboundCompletionSyncService.class);
        Fixture fixture = fixture(mock(PaymentPurchasePrepaymentService.class), completionSyncService);
        PurchaseOrder order = sourceOrder(1L, 101L, 10, "20.00000000", "3000.00");
        order.setStatus(StatusConstants.PURCHASE_COMPLETED);
        PurchaseRefund refund = refund(900L, 1L, StatusConstants.AUDITED, 101L, 6, "12.00000000");
        when(fixture.repository.findByIdAndDeletedFlagFalse(900L)).thenReturn(Optional.of(refund));
        when(fixture.purchaseOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(fixture.repository.existsBySourcePurchaseOrderIdAndDeletedFlagFalseAndIdNot(1L, 900L))
                .thenReturn(false);
        when(fixture.inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(101L)))
                .thenReturn(List.of(inboundItem(101L, 4, "8.00000000", "8.00000000", "3000.00")));
        when(fixture.repository.save(any(PurchaseRefund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.updateStatus(900L, StatusConstants.DRAFT);

        InOrder flow = inOrder(fixture.repository, completionSyncService);
        flow.verify(fixture.repository).save(refund);
        flow.verify(fixture.repository).flush();
        flow.verify(completionSyncService).synchronizeAfterPurchaseRefundStatusChange(List.of(101L));
    }

    private Fixture fixture() {
        return fixture(null, null, null);
    }

    private Fixture fixture(SupplierRefundReceiptGuard receiptGuard) {
        return fixture(receiptGuard, null, null);
    }

    private Fixture fixture(PaymentPurchasePrepaymentService prepaymentService,
                            PurchaseInboundCompletionSyncService completionSyncService) {
        return fixture(null, prepaymentService, completionSyncService);
    }

    private Fixture fixture(SupplierRefundReceiptGuard receiptGuard,
                            PaymentPurchasePrepaymentService prepaymentService,
                            PurchaseInboundCompletionSyncService completionSyncService) {
        PurchaseRefundRepository repository = mock(PurchaseRefundRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemRepository purchaseOrderItemRepository = mock(PurchaseOrderItemRepository.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PurchaseRefundMapper mapper = mock(PurchaseRefundMapper.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        Supplier supplier = new Supplier();
        supplier.setId(501L);
        supplier.setSupplierCode("SUP-001");
        supplier.setSupplierName("供应商A");
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商A"))
                .thenReturn(Optional.of(supplier));
        when(supplierRepository.findBySupplierCodeAndDeletedFlagFalse("SUP-001"))
                .thenReturn(Optional.of(supplier));
        when(supplierRepository.findByIdAndDeletedFlagFalse(501L)).thenReturn(Optional.of(supplier));
        when(idGenerator.nextId()).thenReturn(900L, 901L, 902L);
        when(purchaseOrderItemRepository.findActiveIdsByPurchaseOrderId(any()))
                .thenAnswer(invocation -> List.of(invocation.<Long>getArgument(0) * 100L + 1L));
        PurchaseRefundService service = new PurchaseRefundService(
                repository,
                purchaseOrderRepository,
                purchaseOrderItemRepository,
                inboundItemRepository,
                idGenerator,
                lockService,
                new PurchaseRefundCalculator(),
                mock(PurchaseRefundInvoiceCapacityGuard.class),
                mapper,
                accessGuard,
                workflowTransitionGuard,
                supplierRepository,
                receiptGuard,
                prepaymentService,
                completionSyncService
        );
        return new Fixture(
                service,
                repository,
                purchaseOrderRepository,
                purchaseOrderItemRepository,
                inboundItemRepository,
                lockService,
                supplierRepository
        );
    }

    private PurchaseRefundRequest request(Long sourcePurchaseOrderId, String status) {
        return new PurchaseRefundRequest(
                "TK-001",
                sourcePurchaseOrderId,
                LocalDate.of(2026, 7, 10),
                status,
                "采购员A",
                "自动计算"
        );
    }

    private PurchaseOrder sourceOrder(Long orderId,
                                      Long itemId,
                                      int quantity,
                                      String weightTon,
                                      String unitPrice) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(orderId);
        order.setOrderNo("PO-001");
        order.setSupplierName("供应商A");
        order.setSettlementCompanyId(11L);
        order.setSettlementCompanyName("结算公司A");
        order.setStatus(StatusConstants.AUDITED);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(itemId);
        item.setPurchaseOrder(order);
        item.setLineNo(1);
        item.setMaterialCode("MAT-001");
        item.setBrand("新澎辉");
        item.setCategory("盘螺");
        item.setMaterial("HRB400E");
        item.setSpec("8");
        item.setUnit("吨");
        item.setWarehouseName("一号库");
        item.setQuantity(quantity);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("2.00000000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal(weightTon));
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setAmount(item.getWeightTon().multiply(item.getUnitPrice()));
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    private PurchaseInboundItem inboundItem(Long sourceItemId,
                                            int quantity,
                                            String theoreticalWeight,
                                            String weighedWeight,
                                            String unitPrice) {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setStatus(StatusConstants.AUDITED);
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setPurchaseInbound(inbound);
        item.setSourcePurchaseOrderItemId(sourceItemId);
        item.setQuantity(quantity);
        item.setWeightTon(new BigDecimal(theoreticalWeight));
        item.setWeighWeightTon(new BigDecimal(weighedWeight));
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setAmount(TradeItemCalculator.calculateAmount(item.getWeightTon(), item.getUnitPrice()));
        item.setWeightAdjustmentAmount(TradeItemCalculator.calculateAmount(
                item.getWeighWeightTon().subtract(item.getWeightTon()),
                item.getUnitPrice()
        ));
        return item;
    }

    private PurchaseRefund refund(Long id,
                                  Long sourceOrderId,
                                  String status,
                                  Long sourceItemId,
                                  int quantity,
                                  String weightTon) {
        PurchaseRefund refund = new PurchaseRefund();
        refund.setId(id);
        refund.setRefundNo("TK-001");
        refund.setSourcePurchaseOrderId(sourceOrderId);
        refund.setStatus(status);
        refund.setSupplierCode("SUP-001");
        refund.setSupplierName("供应商A");
        PurchaseRefundItem item = new PurchaseRefundItem();
        item.setId(901L);
        item.setPurchaseRefund(refund);
        item.setSourcePurchaseOrderItemId(sourceItemId);
        item.setQuantity(quantity);
        item.setWeightTon(new BigDecimal(weightTon));
        refund.setItems(new ArrayList<>(List.of(item)));
        return refund;
    }

    private record Fixture(
            PurchaseRefundService service,
            PurchaseRefundRepository repository,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository,
            PurchaseInboundItemRepository inboundItemRepository,
            SourceAllocationLockService lockService,
            SupplierRepository supplierRepository
    ) {
    }
}
