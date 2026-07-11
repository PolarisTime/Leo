package com.leo.erp.finance.supplierrefundreceipt.repository;

import com.leo.erp.finance.supplierrefundreceipt.domain.entity.SupplierRefundReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface SupplierRefundReceiptRepository extends JpaRepository<SupplierRefundReceipt, Long>,
        JpaSpecificationExecutor<SupplierRefundReceipt> {

    boolean existsByRefundReceiptNoAndDeletedFlagFalse(String refundReceiptNo);

    boolean existsByPurchaseRefundIdAndDeletedFlagFalse(Long purchaseRefundId);

    @Query("""
            select count(receipt.id)
            from SupplierRefundReceipt receipt
            where receipt.deletedFlag = false
              and receipt.purchaseRefundId in (
                  select refund.id
                  from PurchaseRefund refund
                  where refund.sourcePurchaseOrderId = :sourcePurchaseOrderId
              )
            """)
    long countActiveBySourcePurchaseOrderId(@Param("sourcePurchaseOrderId") Long sourcePurchaseOrderId);

    Optional<SupplierRefundReceipt> findByIdAndDeletedFlagFalse(Long id);

    @Query("""
            select coalesce(sum(receipt.amount), 0)
            from SupplierRefundReceipt receipt
            where receipt.purchaseRefundId = :purchaseRefundId
              and receipt.deletedFlag = false
              and receipt.status = '已收款'
              and receipt.id <> :excludedReceiptId
            """)
    BigDecimal sumReceivedAmountByPurchaseRefundIdExcludingReceiptId(
            @Param("purchaseRefundId") Long purchaseRefundId,
            @Param("excludedReceiptId") Long excludedReceiptId
    );
}
