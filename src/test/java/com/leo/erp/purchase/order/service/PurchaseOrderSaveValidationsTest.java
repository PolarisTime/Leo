package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseOrderSaveValidationsTest {

    private static PurchaseOrderItem item(Integer quantity) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setLineNo(2);
        item.setQuantity(quantity);
        return item;
    }

    private static PurchaseOrderItemRequest itemRequest(Integer quantity) {
        return new PurchaseOrderItemRequest(
                1L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                "库房A", "B001", quantity, null, null, null, null, null, null);
    }

    private static PurchaseOrderRequest request(PurchaseOrderItemRequest... items) {
        return new PurchaseOrderRequest(
                "PO001", 10L, null, "供应商A", LocalDateTime.of(2026, 9, 1, 0, 0),
                "采购员A", 30L, null, null, List.of(items), false);
    }

    @Test
    void assertLineQuantities_nullQuantity_shouldThrowWithLineNumber() {
        PurchaseOrderRequest request = request(itemRequest(null));

        assertThatThrownBy(() -> PurchaseOrderSaveValidations.assertLineQuantities(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void assertLineQuantities_zeroQuantity_shouldThrowWithLineNumber() {
        PurchaseOrderRequest request = request(itemRequest(5), itemRequest(0));

        assertThatThrownBy(() -> PurchaseOrderSaveValidations.assertLineQuantities(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行");
    }

    @Test
    void assertLineQuantities_positiveQuantities_shouldPass() {
        PurchaseOrderRequest request = request(itemRequest(1), itemRequest(100));

        assertThatCode(() -> PurchaseOrderSaveValidations.assertLineQuantities(request))
                .doesNotThrowAnyException();
    }

    @Test
    void assertAuditableLineQuantities_nullQuantity_shouldThrowWithEntityLineNo() {
        PurchaseOrder order = new PurchaseOrder();
        order.getItems().add(item(null));

        assertThatThrownBy(() -> PurchaseOrderSaveValidations.assertAuditableLineQuantities(order))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行");
    }

    @Test
    void assertAuditableLineQuantities_positiveQuantities_shouldPass() {
        PurchaseOrder order = new PurchaseOrder();
        order.getItems().add(item(1));

        assertThatCode(() -> PurchaseOrderSaveValidations.assertAuditableLineQuantities(order))
                .doesNotThrowAnyException();
    }

    @Test
    void assertStatusNotChangedBySave_newOrderWithDraft_shouldPass() {
        PurchaseOrder order = new PurchaseOrder();

        assertThatCode(() -> PurchaseOrderSaveValidations.assertStatusNotChangedBySave(
                order, StatusConstants.DRAFT))
                .doesNotThrowAnyException();
    }

    @Test
    void assertStatusNotChangedBySave_newOrderWithNonDraft_shouldThrow() {
        PurchaseOrder order = new PurchaseOrder();

        assertThatThrownBy(() -> PurchaseOrderSaveValidations.assertStatusNotChangedBySave(
                order, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能保存为草稿");
    }

    @Test
    void assertStatusNotChangedBySave_statusChangedBySave_shouldThrow() {
        PurchaseOrder order = new PurchaseOrder();
        order.setStatus(StatusConstants.AUDITED);

        assertThatThrownBy(() -> PurchaseOrderSaveValidations.assertStatusNotChangedBySave(
                order, StatusConstants.DRAFT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("普通保存不能修改采购订单状态");
    }

    @Test
    void assertStatusNotChangedBySave_sameStatus_shouldPass() {
        PurchaseOrder order = new PurchaseOrder();
        order.setStatus(StatusConstants.AUDITED);

        assertThatCode(() -> PurchaseOrderSaveValidations.assertStatusNotChangedBySave(
                order, StatusConstants.AUDITED))
                .doesNotThrowAnyException();
    }

    @Test
    void assertSettlementCompanyMutable_newOrder_shouldPass() {
        PurchaseOrder order = new PurchaseOrder();

        assertThatCode(() -> PurchaseOrderSaveValidations.assertSettlementCompanyMutable(order, 99L))
                .doesNotThrowAnyException();
    }

    @Test
    void assertSettlementCompanyMutable_auditedOrderWithChangedCompany_shouldThrow() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setStatus(StatusConstants.AUDITED);
        order.setSettlementCompanyId(30L);

        assertThatThrownBy(() -> PurchaseOrderSaveValidations.assertSettlementCompanyMutable(order, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许修改采购结算主体");
    }

    @Test
    void assertSettlementCompanyMutable_auditedOrderWithSameCompany_shouldPass() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setStatus(StatusConstants.AUDITED);
        order.setSettlementCompanyId(30L);

        assertThatCode(() -> PurchaseOrderSaveValidations.assertSettlementCompanyMutable(order, 30L))
                .doesNotThrowAnyException();
    }

    @Test
    void assertSettlementCompanyMutable_draftOrderOrMissingCompany_shouldPass() {
        PurchaseOrder draft = new PurchaseOrder();
        draft.setId(1L);
        draft.setStatus(StatusConstants.DRAFT);
        draft.setSettlementCompanyId(30L);
        PurchaseOrder withoutCompany = new PurchaseOrder();
        withoutCompany.setId(1L);
        withoutCompany.setStatus(StatusConstants.AUDITED);

        assertThatCode(() -> {
            PurchaseOrderSaveValidations.assertSettlementCompanyMutable(draft, 99L);
            PurchaseOrderSaveValidations.assertSettlementCompanyMutable(withoutCompany, 99L);
        }).doesNotThrowAnyException();
    }
}
