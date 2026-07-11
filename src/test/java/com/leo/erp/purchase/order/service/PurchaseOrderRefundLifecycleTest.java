package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.service.PaymentPurchasePrepaymentService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import com.leo.erp.purchase.refund.repository.PurchaseRefundRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderRefundLifecycleTest {

    @Test
    void shouldRequireActiveRefundLookupAsPurchaseOrderLifecycleDependency() {
        boolean hasRefundRepositoryDependency = Arrays.stream(PurchaseOrderService.class.getConstructors())
                .map(java.lang.reflect.Constructor::getParameterTypes)
                .anyMatch(parameterTypes -> Arrays.asList(parameterTypes).contains(PurchaseRefundRepository.class));

        assertThat(hasRefundRepositoryDependency).isTrue();
    }

    @Test
    void shouldRejectUnauditingPurchaseOrderWhenAnyActiveRefundExists() {
        PurchaseRefundRepository refundRepository = mock(PurchaseRefundRepository.class);
        PurchaseOrderService service = service(refundRepository, null, null);
        PurchaseOrder order = order(StatusConstants.AUDITED);
        when(refundRepository.existsBySourcePurchaseOrderIdAndDeletedFlagFalse(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.beforeStatusUpdate(
                order,
                StatusConstants.AUDITED,
                StatusConstants.DRAFT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购退款单")
                .hasMessageContaining("不能反审核");
    }

    @Test
    void shouldRejectDeletingPurchaseOrderWhenAnyActiveRefundExists() {
        PurchaseRefundRepository refundRepository = mock(PurchaseRefundRepository.class);
        PurchaseOrderService service = service(refundRepository, null, null);
        PurchaseOrder order = order(StatusConstants.DRAFT);
        when(refundRepository.existsBySourcePurchaseOrderIdAndDeletedFlagFalse(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.beforeDelete(order))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购退款单")
                .hasMessageContaining("不能删除");
    }

    @Test
    void shouldRejectCriticalPurchaseOrderUpdateWhenAnyActiveRefundExists() {
        PurchaseRefundRepository refundRepository = mock(PurchaseRefundRepository.class);
        PurchaseOrderSupplierResolver supplierResolver = mock(PurchaseOrderSupplierResolver.class);
        PurchaseOrderApplyService applyService = mock(PurchaseOrderApplyService.class);
        PurchaseOrderService service = service(refundRepository, supplierResolver, applyService);
        PurchaseOrder order = order(StatusConstants.DRAFT);
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                "PO-001",
                "SUP-001",
                "供应商A",
                LocalDateTime.of(2026, 7, 11, 0, 0),
                "采购员",
                1L,
                StatusConstants.DRAFT,
                "仅修改备注也必须经过来源退款保护",
                List.of()
        );
        when(refundRepository.existsBySourcePurchaseOrderIdAndDeletedFlagFalse(1L)).thenReturn(true);
        when(supplierResolver.requireMasterSupplier("SUP-001", "供应商A"))
                .thenReturn(new PurchaseOrderSupplierResolver.SupplierIdentity("SUP-001", "供应商A"));

        assertThatThrownBy(() -> service.apply(order, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购退款单")
                .hasMessageContaining("不能修改");
        verify(applyService, never()).applyItems(any(), any(), any());
    }

    @Test
    void shouldRejectCriticalPurchaseOrderUpdateWhenAnyActivePrepaymentExists() {
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        PurchaseOrderApplyService applyService = mock(PurchaseOrderApplyService.class);
        PurchaseOrderService service = service(
                mock(PurchaseRefundRepository.class),
                mock(PurchaseOrderSupplierResolver.class),
                applyService,
                prepaymentService
        );
        PurchaseOrder order = order(StatusConstants.DRAFT);
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                "PO-001",
                "SUP-001",
                "供应商A",
                LocalDateTime.of(2026, 7, 11, 0, 0),
                "采购员",
                1L,
                StatusConstants.DRAFT,
                "修改备注",
                List.of()
        );
        doThrow(new BusinessException(
                com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                "采购订单已存在采购预付款，不能修改"
        )).when(prepaymentService).assertNoActivePrepayment(1L, "修改");

        assertThatThrownBy(() -> service.apply(order, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购预付款")
                .hasMessageContaining("不能修改");

        verify(applyService, never()).applyItems(any(), any(), any());
    }

    @Test
    void shouldRejectUnauditingPurchaseOrderWhenAnyActivePrepaymentExists() {
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        PurchaseOrderService service = service(
                mock(PurchaseRefundRepository.class),
                null,
                null,
                prepaymentService
        );
        PurchaseOrder order = order(StatusConstants.AUDITED);
        doThrow(new BusinessException(
                com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                "采购订单已存在采购预付款，不能反审核"
        )).when(prepaymentService).assertNoActivePrepayment(1L, "反审核");

        assertThatThrownBy(() -> service.beforeStatusUpdate(
                order,
                StatusConstants.AUDITED,
                StatusConstants.DRAFT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购预付款")
                .hasMessageContaining("不能反审核");
    }

    @Test
    void shouldRejectDeletingPurchaseOrderWhenAnyActivePrepaymentExists() {
        PaymentPurchasePrepaymentService prepaymentService = mock(PaymentPurchasePrepaymentService.class);
        PurchaseOrderService service = service(
                mock(PurchaseRefundRepository.class),
                null,
                null,
                prepaymentService
        );
        PurchaseOrder order = order(StatusConstants.DRAFT);
        doThrow(new BusinessException(
                com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                "采购订单已存在采购预付款，不能删除"
        )).when(prepaymentService).assertNoActivePrepayment(1L, "删除");

        assertThatThrownBy(() -> service.beforeDelete(order))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购预付款")
                .hasMessageContaining("不能删除");
    }

    private PurchaseOrderService service(PurchaseRefundRepository refundRepository,
                                         PurchaseOrderSupplierResolver supplierResolver,
                                         PurchaseOrderApplyService applyService) {
        return new PurchaseOrderService(
                mock(PurchaseOrderRepository.class),
                mock(SnowflakeIdGenerator.class),
                null,
                null,
                supplierResolver,
                applyService,
                null,
                mock(WorkflowTransitionGuard.class),
                null,
                null,
                refundRepository
        );
    }

    private PurchaseOrderService service(PurchaseRefundRepository refundRepository,
                                         PurchaseOrderSupplierResolver supplierResolver,
                                         PurchaseOrderApplyService applyService,
                                         PaymentPurchasePrepaymentService prepaymentService) {
        return new PurchaseOrderService(
                mock(PurchaseOrderRepository.class),
                mock(SnowflakeIdGenerator.class),
                null,
                null,
                supplierResolver,
                applyService,
                null,
                mock(WorkflowTransitionGuard.class),
                null,
                null,
                refundRepository,
                prepaymentService
        );
    }

    private PurchaseOrder order(String status) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setOrderNo("PO-001");
        order.setSupplierCode("SUP-001");
        order.setSupplierName("供应商A");
        order.setSettlementCompanyId(1L);
        order.setStatus(status);
        return order;
    }
}
