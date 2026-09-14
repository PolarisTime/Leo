package com.leo.erp.inventory.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.inventory.api.InventorySourceDocumentType;
import com.leo.erp.inventory.api.InventoryTransactionCommand;
import com.leo.erp.inventory.api.InventoryTransactionInput;
import com.leo.erp.inventory.api.InventoryTransactionType;
import com.leo.erp.inventory.repository.InventoryTransactionRepository;
import com.leo.erp.inventory.web.dto.InventoryBackfillResponse;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.repository.SalesReturnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 库存期初回填：扫描所有已审核/完成的采购入库、销售出库、销售退货来源明细，
 * 对尚无有效库存事务的来源明细按业务日期升序补记库存事务。
 *
 * <p>整批在单一事务内完成，复用 {@link InventoryTransactionCommand} 的移动加权平均记账逻辑；
 * 回填天然幂等：已有有效事务的来源明细直接跳过，重复调用只会累加 skipped，不会重复记账。
 * 记账前获取全局咨询锁，串行化并发回填，避免同一来源明细被重复记账。
 */
@Service
public class InventoryBackfillService {

    private static final Set<String> POSTED_PURCHASE_INBOUND_STATUSES =
            Set.of(StatusConstants.AUDITED, StatusConstants.INBOUND_COMPLETED);

    private final PurchaseInboundRepository purchaseInboundRepository;
    private final SalesOutboundRepository salesOutboundRepository;
    private final SalesReturnRepository salesReturnRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryTransactionLockService lockService;
    private final InventoryTransactionCommand inventoryCommand;

    public InventoryBackfillService(PurchaseInboundRepository purchaseInboundRepository,
                                    SalesOutboundRepository salesOutboundRepository,
                                    SalesReturnRepository salesReturnRepository,
                                    InventoryTransactionRepository transactionRepository,
                                    InventoryTransactionLockService lockService,
                                    InventoryTransactionCommand inventoryCommand) {
        this.purchaseInboundRepository = purchaseInboundRepository;
        this.salesOutboundRepository = salesOutboundRepository;
        this.salesReturnRepository = salesReturnRepository;
        this.transactionRepository = transactionRepository;
        this.lockService = lockService;
        this.inventoryCommand = inventoryCommand;
    }

    @Transactional
    public InventoryBackfillResponse backfill() {
        lockService.lockBackfill();
        List<PostedDocument> documents = collectPostedDocuments();
        documents.sort(Comparator
                .comparing(PostedDocument::occurredAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingInt(PostedDocument::typeOrder));

        int purchaseInCreated = 0;
        int salesOutCreated = 0;
        int salesReturnCreated = 0;
        int skipped = 0;
        for (PostedDocument document : documents) {
            boolean anyCreated = false;
            for (InventoryTransactionInput.Line line : document.input().lines()) {
                if (line.materialId() == null || line.quantity() <= 0) {
                    skipped++;
                    continue;
                }
                if (transactionRepository
                        .existsBySourceDocumentTypeAndSourceItemIdAndTransactionTypeAndDeletedFlagFalse(
                                document.input().sourceDocumentType(),
                                line.sourceItemId(),
                                document.transactionType().name())) {
                    skipped++;
                    continue;
                }
                switch (document.transactionType()) {
                    case PURCHASE_IN -> purchaseInCreated++;
                    case SALES_OUT -> salesOutCreated++;
                    case SALES_RETURN_IN -> salesReturnCreated++;
                    default -> {
                    }
                }
                anyCreated = true;
            }
            if (anyCreated) {
                record(document);
            }
        }
        return new InventoryBackfillResponse(purchaseInCreated, salesOutCreated, salesReturnCreated, skipped);
    }

    private List<PostedDocument> collectPostedDocuments() {
        List<PostedDocument> documents = new ArrayList<>();
        for (PurchaseInbound inbound : purchaseInboundRepository
                .findAllByStatusInAndDeletedFlagFalse(POSTED_PURCHASE_INBOUND_STATUSES)) {
            documents.add(new PostedDocument(
                    inbound.getInboundDate(), 0, InventoryTransactionType.PURCHASE_IN, purchaseInput(inbound)));
        }
        for (SalesOutbound outbound : salesOutboundRepository
                .findAllByStatusAndDeletedFlagFalse(StatusConstants.AUDITED)) {
            documents.add(new PostedDocument(
                    outbound.getOutboundDate(), 1, InventoryTransactionType.SALES_OUT, salesOutInput(outbound)));
        }
        for (SalesReturn salesReturn : salesReturnRepository.findByStatus(StatusConstants.AUDITED)) {
            documents.add(new PostedDocument(
                    salesReturn.getReturnDate(), 2, InventoryTransactionType.SALES_RETURN_IN,
                    salesReturnInput(salesReturn)));
        }
        return documents;
    }

    private void record(PostedDocument document) {
        switch (document.transactionType()) {
            case PURCHASE_IN -> inventoryCommand.recordPurchaseIn(document.input());
            case SALES_OUT -> inventoryCommand.recordSalesOut(document.input());
            case SALES_RETURN_IN -> inventoryCommand.recordSalesReturnIn(document.input());
            default -> throw new IllegalStateException("不支持的库存回填类型: " + document.transactionType());
        }
    }

    private InventoryTransactionInput purchaseInput(PurchaseInbound inbound) {
        List<InventoryTransactionInput.Line> lines = inbound.getItems().stream()
                .filter(Objects::nonNull)
                .map(item -> new InventoryTransactionInput.Line(
                        item.getId(),
                        item.getMaterialId(),
                        item.getMaterialCode(),
                        item.getWarehouseId(),
                        item.getWarehouseName(),
                        item.getBatchNo(),
                        item.getQuantity() == null ? 0 : item.getQuantity(),
                        item.getQuantityUnit(),
                        item.getUnitPrice()
                ))
                .toList();
        return new InventoryTransactionInput(
                InventorySourceDocumentType.PURCHASE_INBOUND.name(),
                inbound.getId(),
                inbound.getInboundNo(),
                inbound.getInboundDate(),
                inbound.getWarehouseId(),
                inbound.getWarehouseName(),
                lines
        );
    }

    private InventoryTransactionInput salesOutInput(SalesOutbound outbound) {
        List<InventoryTransactionInput.Line> lines = outbound.getItems().stream()
                .filter(Objects::nonNull)
                .map(item -> new InventoryTransactionInput.Line(
                        item.getId(),
                        item.getMaterialId(),
                        item.getMaterialCode(),
                        item.getWarehouseId(),
                        item.getWarehouseName(),
                        item.getBatchNo(),
                        item.getQuantity() == null ? 0 : item.getQuantity(),
                        item.getQuantityUnit(),
                        item.getUnitPrice()
                ))
                .toList();
        return new InventoryTransactionInput(
                InventorySourceDocumentType.SALES_OUTBOUND.name(),
                outbound.getId(),
                outbound.getOutboundNo(),
                outbound.getOutboundDate(),
                outbound.getWarehouseId(),
                outbound.getWarehouseName(),
                lines
        );
    }

    private InventoryTransactionInput salesReturnInput(SalesReturn salesReturn) {
        List<InventoryTransactionInput.Line> lines = salesReturn.getItems().stream()
                .filter(Objects::nonNull)
                .map(item -> new InventoryTransactionInput.Line(
                        item.getId(),
                        item.getMaterialId(),
                        item.getMaterialCode(),
                        item.getWarehouseId(),
                        item.getWarehouseName(),
                        item.getBatchNo(),
                        item.getQuantity() == null ? 0 : item.getQuantity(),
                        item.getQuantityUnit(),
                        item.getUnitPrice()
                ))
                .toList();
        return new InventoryTransactionInput(
                InventorySourceDocumentType.SALES_RETURN.name(),
                salesReturn.getId(),
                salesReturn.getReturnNo(),
                salesReturn.getReturnDate(),
                salesReturn.getWarehouseId(),
                salesReturn.getWarehouseName(),
                lines
        );
    }

    private record PostedDocument(LocalDate occurredAt,
                                  int typeOrder,
                                  InventoryTransactionType transactionType,
                                  InventoryTransactionInput input) {
    }
}
