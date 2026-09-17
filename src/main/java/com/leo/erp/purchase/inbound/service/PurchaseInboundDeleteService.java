package com.leo.erp.purchase.inbound.service;

import com.leo.erp.purchase.api.PurchaseOrderReferenceGuard;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class PurchaseInboundDeleteService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseInboundDeleteService.class);

    private final PurchaseInboundWeightWriteBackService weightWriteBackService;
    private final PurchaseInboundItemRepository purchaseInboundItemRepository;
    private final PurchaseOrderReferenceGuard purchaseOrderReferenceGuard;

    public PurchaseInboundDeleteService(PurchaseInboundWeightWriteBackService weightWriteBackService,
                                        PurchaseInboundItemRepository purchaseInboundItemRepository,
                                        PurchaseOrderReferenceGuard purchaseOrderReferenceGuard) {
        this.weightWriteBackService = weightWriteBackService;
        this.purchaseInboundItemRepository = purchaseInboundItemRepository;
        this.purchaseOrderReferenceGuard = purchaseOrderReferenceGuard;
    }

    void afterDelete(PurchaseInbound inbound) {
        weightWriteBackService.synchronizeAfterSave(inbound);
        releaseUnreferencedItems(inbound);
    }

    /**
     * 软删采购入库单后物理释放其明细行对采购订单明细的 RESTRICT 引用，
     * 使来源采购订单明细恢复可编辑/可删除；仍被销售订单明细物理引用的行保留，
     * 由来源守卫给出明确业务提示。
     */
    private void releaseUnreferencedItems(PurchaseInbound inbound) {
        List<Long> itemIds = inbound.getItems().stream()
                .map(PurchaseInboundItem::getId)
                .filter(Objects::nonNull)
                .toList();
        if (itemIds.isEmpty()) {
            return;
        }
        Set<Long> referencedItemIds = purchaseOrderReferenceGuard.findReferencedInboundItemIds(itemIds);
        List<PurchaseInboundItem> releasableItems = inbound.getItems().stream()
                .filter(item -> item.getId() != null && !referencedItemIds.contains(item.getId()))
                .toList();
        if (releasableItems.isEmpty()) {
            log.info(
                    "{} kept all item references: id={}, retained={}",
                    inbound.getClass().getSimpleName(),
                    inbound.getId(),
                    itemIds.size()
            );
            return;
        }
        inbound.getItems().removeAll(releasableItems);
        purchaseInboundItemRepository.flush();
        log.info(
                "{} released item references: id={}, released={}, retained={}",
                inbound.getClass().getSimpleName(),
                inbound.getId(),
                releasableItems.size(),
                itemIds.size() - releasableItems.size()
        );
    }
}
