package com.leo.erp.finance;

import com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentRequest;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentResponse;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationRequest;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationResponse;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptAllocation;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationResponse;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
import com.leo.erp.finance.supplierrefundreceipt.domain.entity.SupplierRefundReceipt;
import com.leo.erp.finance.supplierrefundreceipt.web.dto.SupplierRefundReceiptRequest;
import com.leo.erp.finance.supplierrefundreceipt.web.dto.SupplierRefundReceiptResponse;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceSettlementStableIdentityContractTest {

    @Test
    void receiptShouldExposeCustomerAndTypedStatementIdentity() {
        assertEntityColumn(Receipt.class, "customerId", "customer_id");
        assertEntityColumn(Receipt.class, "sourceCustomerStatementId", "source_customer_statement_id");
        assertRecordComponent(ReceiptRequest.class, "customerId", Long.class);
        assertRecordComponent(ReceiptRequest.class, "sourceCustomerStatementId", Long.class);
        assertRecordComponent(ReceiptResponse.class, "customerId", Long.class);
        assertRecordComponent(ReceiptResponse.class, "sourceCustomerStatementId", Long.class);

        assertEntityColumn(
                ReceiptAllocation.class,
                "sourceCustomerStatementId",
                "source_customer_statement_id"
        );
        assertRecordComponent(ReceiptAllocationRequest.class, "sourceCustomerStatementId", Long.class);
        assertRecordComponent(ReceiptAllocationResponse.class, "sourceCustomerStatementId", Long.class);
    }

    @Test
    void paymentShouldExposeTypedPartyAndMutuallyExclusiveStatementIdentity() {
        assertEntityColumn(Payment.class, "counterpartyType", "counterparty_type");
        assertEntityColumn(Payment.class, "counterpartyId", "counterparty_id");
        assertRecordComponent(PaymentRequest.class, "counterpartyType", String.class);
        assertRecordComponent(PaymentRequest.class, "counterpartyId", Long.class);
        assertRecordComponent(PaymentResponse.class, "counterpartyType", String.class);
        assertRecordComponent(PaymentResponse.class, "counterpartyId", Long.class);

        assertEntityColumn(
                PaymentAllocation.class,
                "sourceSupplierStatementId",
                "source_supplier_statement_id"
        );
        assertEntityColumn(
                PaymentAllocation.class,
                "sourceFreightStatementId",
                "source_freight_statement_id"
        );
        assertRecordComponent(PaymentAllocationRequest.class, "sourceSupplierStatementId", Long.class);
        assertRecordComponent(PaymentAllocationRequest.class, "sourceFreightStatementId", Long.class);
        assertRecordComponent(PaymentAllocationResponse.class, "sourceSupplierStatementId", Long.class);
        assertRecordComponent(PaymentAllocationResponse.class, "sourceFreightStatementId", Long.class);
    }

    @Test
    void supplierRefundReceiptShouldExposeSourceSupplierIdentity() {
        assertEntityColumn(SupplierRefundReceipt.class, "supplierId", "supplier_id");
        assertRecordComponent(SupplierRefundReceiptRequest.class, "supplierId", Long.class);
        assertRecordComponent(SupplierRefundReceiptResponse.class, "supplierId", Long.class);
    }

    @Test
    void ledgerAdjustmentShouldExposeTypedCounterpartyIdentity() {
        assertEntityColumn(LedgerAdjustment.class, "counterpartyId", "counterparty_id");
        assertRecordComponent(LedgerAdjustmentRequest.class, "counterpartyId", Long.class);
        assertRecordComponent(LedgerAdjustmentResponse.class, "counterpartyId", Long.class);
    }

    private void assertRecordComponent(Class<?> recordType, String name, Class<?> type) {
        RecordComponent component = Arrays.stream(recordType.getRecordComponents())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElse(null);
        assertThat(component).as(recordType.getSimpleName() + " 应包含字段 " + name).isNotNull();
        assertThat(component.getType()).isEqualTo(type);
    }

    private void assertEntityColumn(Class<?> entityType, String fieldName, String columnName) {
        Field field = Arrays.stream(entityType.getDeclaredFields())
                .filter(candidate -> candidate.getName().equals(fieldName))
                .findFirst()
                .orElse(null);
        assertThat(field).as(entityType.getSimpleName() + " 应包含字段 " + fieldName).isNotNull();
        Column column = field.getAnnotation(Column.class);
        assertThat(column).as(fieldName + " 应声明数据库列映射").isNotNull();
        assertThat(column.name()).isEqualTo(columnName);
    }
}
