package com.leo.erp.common.validation;

import com.leo.erp.common.charge.api.DocumentChargeItemRequest;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationRequest;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.master.material.web.dto.MaterialRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求 DTO 金额/重量/单价/数量的 @Digits 精度校验。
 * 超精度输入会生成 Digits 约束违规，GlobalExceptionHandler 据此返回 422 与 errors[]。
 */
class RequestNumericPrecisionValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> digitsViolationPaths(Object request) {
        return VALIDATOR.validate(request).stream()
                .filter(violation -> "Digits".equals(
                        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()))
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void material_pieceWeightAndUnitPrice_rejectOutOfRangePrecision() {
        MaterialRequest scaleViolation = new MaterialRequest("111", "品牌", "棉布", "面料", "1米", "100",
                "米", "件", new BigDecimal("1.123456789"), 2, new BigDecimal("10.00"), null, null);
        assertThat(digitsViolationPaths(scaleViolation)).containsExactly("pieceWeightTon");

        MaterialRequest integerViolation = new MaterialRequest("111", "品牌", "棉布", "面料", "1米", "100",
                "米", "件", new BigDecimal("1.12345678"), 2, new BigDecimal("12345678901.00"), null, null);
        assertThat(digitsViolationPaths(integerViolation)).containsExactly("unitPrice");

        MaterialRequest valid = new MaterialRequest("111", "品牌", "棉布", "面料", "1米", "100",
                "米", "件", new BigDecimal("1.12345678"), 2, new BigDecimal("9999999999.99"), null, null);
        assertThat(digitsViolationPaths(valid)).isEmpty();
    }

    @Test
    void purchaseOrderItem_rejectsOverPrecisionAmountAndWeight() {
        PurchaseOrderItemRequest amountViolation = purchaseOrderItem(new BigDecimal("1.0"),
                new BigDecimal("1.00"), new BigDecimal("1234567890123.00"));
        assertThat(digitsViolationPaths(amountViolation)).containsExactly("amount");

        PurchaseOrderItemRequest weightViolation = purchaseOrderItem(new BigDecimal("1.123456789"),
                new BigDecimal("1.00"), new BigDecimal("1.00"));
        assertThat(digitsViolationPaths(weightViolation)).containsExactly("weightTon");

        PurchaseOrderItemRequest valid = purchaseOrderItem(new BigDecimal("1.12345678"),
                new BigDecimal("9999999999.99"), new BigDecimal("999999999999.99"));
        assertThat(digitsViolationPaths(valid)).isEmpty();
    }

    @Test
    void purchaseInboundItem_rejectsOverPrecisionWeightAdjustment() {
        PurchaseInboundItemRequest request = new PurchaseInboundItemRequest(null, 1L, "编码", "品牌", "类别",
                "材质", "规格", "1米", "米", 1L, 1L, "仓库", null, "批号", 1, "件",
                new BigDecimal("1.0"), 1, new BigDecimal("1.0"), new BigDecimal("1.0"),
                new BigDecimal("1.0"), new BigDecimal("1.123"), new BigDecimal("1.00"),
                new BigDecimal("1.00"));
        assertThat(digitsViolationPaths(request)).containsExactly("weightAdjustmentAmount");
    }

    @Test
    void salesOrderItem_rejectsOverPrecisionPieceWeight() {
        SalesOrderItemRequest request = new SalesOrderItemRequest(null, 1L, "编码", "品牌", "类别", "材质",
                "规格", "1米", "米", null, null, 1L, "仓库", "批号", 1, "件",
                new BigDecimal("1.123456789"), 1, new BigDecimal("1.0"), new BigDecimal("1.00"),
                new BigDecimal("1.00"));
        assertThat(digitsViolationPaths(request)).containsExactly("pieceWeightTon");
    }

    @Test
    void salesOutboundItem_rejectsOverPrecisionWeight() {
        SalesOutboundItemRequest request = new SalesOutboundItemRequest(null, null, 1L, 1L, "编码", "品牌",
                "类别", "材质", "规格", "1米", "米", 1L, "仓库", "批号", 1, "件",
                new BigDecimal("1.0"), 1, new BigDecimal("1.123456789"), new BigDecimal("1.00"),
                new BigDecimal("1.00"));
        assertThat(digitsViolationPaths(request)).containsExactly("weightTon");
    }

    @Test
    void paymentAndReceipt_rejectOverPrecisionAmount() {
        PaymentRequest payment = new PaymentRequest(null, "供应商", null, "采购付款", "编码", "供应商A",
                null, null, null, null, null, null, null, 1L, LocalDate.of(2026, 1, 1), "电汇",
                new BigDecimal("1.123"), "已审核", "张三", null, List.of(), true);
        assertThat(digitsViolationPaths(payment)).containsExactly("amount");

        ReceiptRequest receipt = new ReceiptRequest(null, "客户", null, "编码", "客户A", null, null, null,
                "客户A", null, null, null, null, 1L, null, LocalDate.of(2026, 1, 1), "电汇",
                new BigDecimal("1234567890123.00"), "已审核", "张三", null, List.of(), true);
        assertThat(digitsViolationPaths(receipt)).containsExactly("amount");
    }

    @Test
    void allocationAndLedgerAdjustmentAndChargeItem_rejectOverPrecisionAmount() {
        PaymentAllocationRequest paymentAllocation =
                new PaymentAllocationRequest(1L, 1L, new BigDecimal("1.123"));
        assertThat(digitsViolationPaths(paymentAllocation)).containsExactly("allocatedAmount");

        ReceiptAllocationRequest receiptAllocation =
                new ReceiptAllocationRequest(1L, 1L, new BigDecimal("1.123"));
        assertThat(digitsViolationPaths(receiptAllocation)).containsExactly("allocatedAmount");

        LedgerAdjustmentRequest adjustment = new LedgerAdjustmentRequest("A001", "增加", "供应商", null, "编码",
                "供应商A", 1L, null, null, null, LocalDate.of(2026, 1, 1), new BigDecimal("1.123"),
                "手工调整", "增加余额", "已审核", "张三", null);
        assertThat(digitsViolationPaths(adjustment)).containsExactly("amount");

        DocumentChargeItemRequest chargeItem =
                new DocumentChargeItemRequest(null, "运费", null, new BigDecimal("1.123"), "元", null);
        assertThat(digitsViolationPaths(chargeItem)).containsExactly("amount");
    }

    @Test
    void nestedItemViolation_reportsIndexedFieldPath() {
        PurchaseOrderItemRequest badItem = purchaseOrderItem(new BigDecimal("1.0"), new BigDecimal("1.00"),
                new BigDecimal("1234567890123.00"));
        PurchaseOrderRequest request = new PurchaseOrderRequest("PO001", "供应商编码", "供应商A",
                LocalDateTime.of(2026, 1, 1, 0, 0), "采购员", 1L, "待审核", null, List.of(badItem));

        Set<String> paths = digitsViolationPaths(request);
        assertThat(paths).containsExactly("items[0].amount");
    }

    private PurchaseOrderItemRequest purchaseOrderItem(BigDecimal weightTon,
                                                       BigDecimal unitPrice,
                                                       BigDecimal amount) {
        return new PurchaseOrderItemRequest(null, null, "编码", "品牌", "类别", "材质", "规格", "1米", "米",
                null, "仓库", "批号", 1, "件", new BigDecimal("1.0"), 1, weightTon, unitPrice, amount);
    }
}
