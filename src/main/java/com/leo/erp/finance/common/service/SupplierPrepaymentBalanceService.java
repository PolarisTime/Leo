package com.leo.erp.finance.common.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptPurposes;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class SupplierPrepaymentBalanceService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SupplierLedgerLockService supplierLedgerLockService;

    public SupplierPrepaymentBalanceService(NamedParameterJdbcTemplate jdbcTemplate,
                                            SupplierLedgerLockService supplierLedgerLockService) {
        this.jdbcTemplate = jdbcTemplate;
        this.supplierLedgerLockService = supplierLedgerLockService;
    }

    public void validateSupplierReceipt(Receipt receipt, String nextStatus) {
        if (!StatusConstants.AUDITED.equals(nextStatus)
                || !ReceiptPurposes.isSupplierReceipt(receipt.getReceiptPurpose())) {
            return;
        }
        if (receipt.getCounterpartyId() == null || receipt.getSettlementCompanyId() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商预付款退款缺少供应商或结算主体身份");
        }
        supplierLedgerLockService.lock(receipt.getSettlementCompanyId(), receipt.getCounterpartyId());
        if (!ReceiptPurposes.SUPPLIER_PREPAYMENT_REFUND.equals(receipt.getReceiptPurpose())) {
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("settlementCompanyId", receipt.getSettlementCompanyId())
                .addValue("supplierId", receipt.getCounterpartyId());
        BigDecimal prepaidBalance = jdbcTemplate.queryForObject("""
                SELECT GREATEST(
                    COALESCE((
                        SELECT SUM(payment.amount)
                        FROM fm_payment payment
                        WHERE payment.deleted_flag = FALSE
                          AND payment.status = '已审核'
                          AND payment.counterparty_type = '供应商'
                          AND payment.settlement_company_id = :settlementCompanyId
                          AND payment.counterparty_id = :supplierId
                    ), 0)
                    - COALESCE((
                        SELECT SUM(reversal.amount)
                        FROM fm_cash_reversal reversal
                        WHERE reversal.deleted_flag = FALSE
                          AND reversal.status = '已审核'
                          AND reversal.original_payment_id IS NOT NULL
                          AND reversal.settlement_company_id = :settlementCompanyId
                          AND reversal.counterparty_id = :supplierId
                    ), 0)
                    - COALESCE((
                        SELECT SUM(item.amount + COALESCE(item.weight_adjustment_amount, 0))
                        FROM po_purchase_inbound inbound
                        JOIN po_purchase_inbound_item item ON item.inbound_id = inbound.id
                        JOIN po_purchase_order_item source_item
                          ON source_item.id = item.source_purchase_order_item_id
                        JOIN po_purchase_order source_order
                          ON source_order.id = source_item.order_id
                        WHERE inbound.deleted_flag = FALSE
                          AND inbound.status IN ('已审核', '完成入库')
                          AND source_order.deleted_flag = FALSE
                          AND source_order.status = '完成采购'
                          AND inbound.settlement_company_id = :settlementCompanyId
                          AND inbound.supplier_id = :supplierId
                    ), 0)
                    - COALESCE((
                        SELECT SUM(supplier_receipt.amount)
                        FROM fm_receipt supplier_receipt
                        WHERE supplier_receipt.deleted_flag = FALSE
                          AND supplier_receipt.status = '已审核'
                          AND supplier_receipt.counterparty_type = '供应商'
                          AND supplier_receipt.settlement_company_id = :settlementCompanyId
                          AND supplier_receipt.counterparty_id = :supplierId
                    ), 0)
                    + COALESCE((
                        SELECT SUM(reversal.amount)
                        FROM fm_cash_reversal reversal
                        WHERE reversal.deleted_flag = FALSE
                          AND reversal.status = '已审核'
                          AND reversal.original_receipt_id IS NOT NULL
                          AND reversal.settlement_company_id = :settlementCompanyId
                          AND reversal.counterparty_id = :supplierId
                    ), 0),
                    0
                )
                """, params, BigDecimal.class);
        BigDecimal available = prepaidBalance == null ? BigDecimal.ZERO : prepaidBalance;
        if (receipt.getAmount().compareTo(available) > 0) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "供应商预付款退款金额不能超过当前预付款余额 " + available
            );
        }
    }
}
