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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存期初回填：扫描所有已审核/完成的采购入库、销售出库、销售退货来源明细，
 * 对尚无有效库存事务的来源明细按业务日期升序补记库存事务。
 *
 * <p>整批在单一事务内完成，复用 {@link InventoryTransactionCommand} 的移动加权平均记账逻辑；
 * 回填天然幂等：记账前按批次批量预加载已有有效事务幂等键，在内存中做差集判断，
 * 重复调用只会累加 skipped，不会重复记账，也避免逐行 {@code exists} 查询。
 *
 * <p>来源单据按 (业务日期, id) keyset 分页扫描并多路归并，避免一次性全量装载；
 * 每个批次记账前收集全部 (materialId, warehouseId) 维度全局升序加锁，
 * 与常规记账共用同一加锁顺序，消除 AB-BA 死锁。
 */
@Service
public class InventoryBackfillService {

    private static final Set<String> POSTED_PURCHASE_INBOUND_STATUSES =
            Set.of(StatusConstants.AUDITED, StatusConstants.INBOUND_COMPLETED);
    private static final int SCAN_BATCH_SIZE = 500;
    private static final LocalDate MIN_BUSINESS_DATE = LocalDate.of(1, 1, 1);
    private static final long MIN_CURSOR_ID = 0L;
    private static final Comparator<PostedDocument> DOCUMENT_ORDER = Comparator
            .comparing(PostedDocument::occurredAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparingInt(PostedDocument::typeOrder)
            .thenComparing(PostedDocument::sourceDocumentId,
                    Comparator.nullsFirst(Comparator.naturalOrder()));

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
        Counters counters = new Counters();
        List<PostedDocument> batch = new ArrayList<>(SCAN_BATCH_SIZE);
        List<SourceStream<PostedDocument>> streams = List.of(
                purchaseInboundStream(),
                salesOutboundStream(),
                salesReturnStream());

        // 三路 keyset 流按 (业务日期, 类型, id) 归并，边归并边分批处理，内存占用与批大小成比例。
        while (true) {
            SourceStream<PostedDocument> selected = null;
            PostedDocument next = null;
            for (SourceStream<PostedDocument> stream : streams) {
                PostedDocument head = stream.peek();
                if (head == null) {
                    continue;
                }
                if (next == null || DOCUMENT_ORDER.compare(head, next) < 0) {
                    next = head;
                    selected = stream;
                }
            }
            if (selected == null) {
                break;
            }
            selected.poll();
            batch.add(next);
            if (batch.size() >= SCAN_BATCH_SIZE) {
                flushBatch(batch, counters);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            flushBatch(batch, counters);
        }
        return counters.toResponse();
    }

    /**
     * 处理单个批次：预加载已有事务幂等键做内存差集，全局排序维度加锁后再逐单记账。
     */
    private void flushBatch(List<PostedDocument> batch, Counters counters) {
        Set<Long> sourceItemIds = batch.stream()
                .flatMap(document -> document.input().lines().stream())
                .map(InventoryTransactionInput.Line::sourceItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> activeKeys = new HashSet<>();
        if (!sourceItemIds.isEmpty()) {
            transactionRepository.findActiveSourceKeysBySourceItemIdIn(sourceItemIds).stream()
                    .map(key -> idempotencyKey(
                            key.getSourceDocumentType(), key.getSourceItemId(), key.getTransactionType()))
                    .forEach(activeKeys::add);
        }

        List<InventoryTransactionLockService.Dimension> dimensions = batch.stream()
                .flatMap(document -> document.input().lines().stream()
                        .filter(line -> line.materialId() != null && line.quantity() > 0)
                        .map(line -> new InventoryTransactionLockService.Dimension(
                                line.materialId(), resolveWarehouseId(line, document.input()))))
                .toList();
        lockService.lockAll(dimensions);

        for (PostedDocument document : batch) {
            List<InventoryTransactionInput.Line> missing = new ArrayList<>();
            for (InventoryTransactionInput.Line line : document.input().lines()) {
                if (line.materialId() == null || line.quantity() <= 0) {
                    counters.skipped++;
                    continue;
                }
                String key = idempotencyKey(
                        document.input().sourceDocumentType(), line.sourceItemId(), document.transactionType().name());
                if (activeKeys.contains(key)) {
                    counters.skipped++;
                    continue;
                }
                activeKeys.add(key);
                missing.add(line);
            }
            if (missing.isEmpty()) {
                continue;
            }
            record(document.withLines(missing));
            counters.created(document.transactionType(), missing.size());
        }
    }

    private SourceStream<PostedDocument> purchaseInboundStream() {
        return new SourceStream<>(
                cursor -> {
                    List<PurchaseInbound> page = purchaseInboundRepository.findPostedAfter(
                            POSTED_PURCHASE_INBOUND_STATUSES, cursor.date(), cursor.id(),
                            PageRequest.of(0, SCAN_BATCH_SIZE));
                    loadInboundItems(page);
                    return page.stream()
                            .map(inbound -> new PostedDocument(inbound.getInboundDate(), 0,
                                    InventoryTransactionType.PURCHASE_IN, purchaseInput(inbound)))
                            .toList();
                },
                PostedDocument::occurredAt,
                PostedDocument::sourceDocumentId);
    }

    private SourceStream<PostedDocument> salesOutboundStream() {
        return new SourceStream<>(
                cursor -> {
                    List<SalesOutbound> page = salesOutboundRepository.findPostedAfter(
                            StatusConstants.AUDITED, cursor.date(), cursor.id(),
                            PageRequest.of(0, SCAN_BATCH_SIZE));
                    loadOutboundItems(page);
                    return page.stream()
                            .map(outbound -> new PostedDocument(outbound.getOutboundDate(), 1,
                                    InventoryTransactionType.SALES_OUT, salesOutInput(outbound)))
                            .toList();
                },
                PostedDocument::occurredAt,
                PostedDocument::sourceDocumentId);
    }

    private SourceStream<PostedDocument> salesReturnStream() {
        return new SourceStream<>(
                cursor -> {
                    List<SalesReturn> page = salesReturnRepository.findPostedAfter(
                            StatusConstants.AUDITED, cursor.date(), cursor.id(),
                            PageRequest.of(0, SCAN_BATCH_SIZE));
                    loadReturnItems(page);
                    return page.stream()
                            .map(salesReturn -> new PostedDocument(salesReturn.getReturnDate(), 2,
                                    InventoryTransactionType.SALES_RETURN_IN, salesReturnInput(salesReturn)))
                            .toList();
                },
                PostedDocument::occurredAt,
                PostedDocument::sourceDocumentId);
    }

    private void loadInboundItems(List<PurchaseInbound> page) {
        if (page.isEmpty()) {
            return;
        }
        purchaseInboundRepository.findAllByIdIn(page.stream().map(PurchaseInbound::getId).toList());
    }

    private void loadOutboundItems(List<SalesOutbound> page) {
        if (page.isEmpty()) {
            return;
        }
        salesOutboundRepository.findAllByIdIn(page.stream().map(SalesOutbound::getId).toList());
    }

    private void loadReturnItems(List<SalesReturn> page) {
        if (page.isEmpty()) {
            return;
        }
        salesReturnRepository.findAllByIdIn(page.stream().map(SalesReturn::getId).toList());
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

    private static Long resolveWarehouseId(InventoryTransactionInput.Line line, InventoryTransactionInput input) {
        return line.warehouseId() != null ? line.warehouseId() : input.defaultWarehouseId();
    }

    private static String idempotencyKey(String sourceDocumentType, Long sourceItemId, String transactionType) {
        return sourceDocumentType + '|' + sourceItemId + '|' + transactionType;
    }

    /**
     * 单来源单据的 (业务日期, id) keyset 分页游标流：按需拉取一页，内存只保留当前页缓冲。
     */
    private final class SourceStream<T> {

        private final Function<Cursor, List<T>> fetcher;
        private final Function<T, LocalDate> dateExtractor;
        private final Function<T, Long> idExtractor;
        private final Deque<T> buffer = new ArrayDeque<>();
        private LocalDate lastDate = MIN_BUSINESS_DATE;
        private long lastId = MIN_CURSOR_ID;
        private boolean exhausted;

        private SourceStream(Function<Cursor, List<T>> fetcher,
                             Function<T, LocalDate> dateExtractor,
                             Function<T, Long> idExtractor) {
            this.fetcher = fetcher;
            this.dateExtractor = dateExtractor;
            this.idExtractor = idExtractor;
        }

        private T peek() {
            ensureBuffered();
            return buffer.peekFirst();
        }

        private T poll() {
            ensureBuffered();
            return buffer.pollFirst();
        }

        private void ensureBuffered() {
            if (!buffer.isEmpty() || exhausted) {
                return;
            }
            List<T> page = fetcher.apply(new Cursor(lastDate, lastId));
            if (page == null || page.isEmpty()) {
                exhausted = true;
                return;
            }
            buffer.addAll(page);
            T last = page.get(page.size() - 1);
            lastDate = dateExtractor.apply(last);
            lastId = idExtractor.apply(last);
            if (page.size() < SCAN_BATCH_SIZE) {
                exhausted = true;
            }
        }
    }

    private record Cursor(LocalDate date, long id) {
    }

    private record PostedDocument(LocalDate occurredAt,
                                  int typeOrder,
                                  InventoryTransactionType transactionType,
                                  InventoryTransactionInput input) {

        private Long sourceDocumentId() {
            return input.sourceDocumentId();
        }

        private PostedDocument withLines(List<InventoryTransactionInput.Line> lines) {
            return new PostedDocument(occurredAt, typeOrder, transactionType, new InventoryTransactionInput(
                    input.sourceDocumentType(),
                    input.sourceDocumentId(),
                    input.sourceDocumentNo(),
                    input.occurredAt(),
                    input.defaultWarehouseId(),
                    input.defaultWarehouseName(),
                    lines));
        }
    }

    private static final class Counters {

        private int purchaseInCreated;
        private int salesOutCreated;
        private int salesReturnCreated;
        private int skipped;

        private void created(InventoryTransactionType type, int count) {
            switch (type) {
                case PURCHASE_IN -> purchaseInCreated += count;
                case SALES_OUT -> salesOutCreated += count;
                case SALES_RETURN_IN -> salesReturnCreated += count;
                default -> {
                }
            }
        }

        private InventoryBackfillResponse toResponse() {
            return new InventoryBackfillResponse(purchaseInCreated, salesOutCreated, salesReturnCreated, skipped);
        }
    }
}
