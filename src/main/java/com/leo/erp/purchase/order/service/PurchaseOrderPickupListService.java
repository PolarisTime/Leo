package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderPickupListResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderPickupListService {

    private static final int MAX_ORDER_COUNT = 50;
    private static final Comparator<String> TEXT_ORDER = Comparator.nullsLast(String::compareTo);
    private static final Comparator<PurchaseOrder> ORDER_COMPARATOR = Comparator
            .comparing(PurchaseOrder::getSupplierName, TEXT_ORDER)
            .thenComparing(PurchaseOrder::getSettlementCompanyName, TEXT_ORDER)
            .thenComparing(PurchaseOrder::getOrderNo, TEXT_ORDER);
    private static final Comparator<PurchaseOrderItem> ITEM_COMPARATOR = Comparator
            .comparing(PurchaseOrderItem::getWarehouseName, TEXT_ORDER)
            .thenComparing(PurchaseOrderItem::getLineNo, Comparator.nullsLast(Integer::compareTo));

    private final PurchaseOrderRepository purchaseOrderRepository;

    public PurchaseOrderPickupListService(PurchaseOrderRepository purchaseOrderRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderPickupListResponse preview(List<Long> requestedOrderIds) {
        List<Long> orderIds = normalizeOrderIds(requestedOrderIds);
        List<PurchaseOrder> orders = loadOrders(orderIds);

        Map<GroupIdentity, MutableGroup> groups = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (PurchaseOrder order : orders.stream().sorted(ORDER_COMPARATOR).toList()) {
            int addedItemCount = appendOrderItems(order, groups);
            if (addedItemCount == 0) {
                warnings.add("采购订单 " + order.getOrderNo() + " 无明细");
            }
        }

        if (groups.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "所选采购订单没有明细");
        }

        List<PurchaseOrderPickupListResponse.Group> responseGroups = groups.values().stream()
                .map(MutableGroup::toResponse)
                .toList();
        int totalQuantity = responseGroups.stream()
                .mapToInt(PurchaseOrderPickupListResponse.Group::totalQuantity)
                .sum();
        BigDecimal totalWeightTon = responseGroups.stream()
                .map(PurchaseOrderPickupListResponse.Group::totalWeightTon)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int itemCount = responseGroups.stream()
                .mapToInt(PurchaseOrderPickupListResponse.Group::itemCount)
                .sum();
        int supplierCount = (int) orders.stream()
                .map(order -> partyKey(order.getSupplierId(), order.getSupplierName()))
                .distinct()
                .count();

        return new PurchaseOrderPickupListResponse(
                orders.size(),
                supplierCount,
                itemCount,
                totalQuantity,
                TradeItemCalculator.scaleWeightTon(totalWeightTon),
                responseGroups,
                List.copyOf(warnings)
        );
    }

    private List<Long> normalizeOrderIds(List<Long> requestedOrderIds) {
        if (requestedOrderIds == null || requestedOrderIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请至少选择一张采购订单");
        }
        List<Long> orderIds = List.copyOf(new LinkedHashSet<>(requestedOrderIds));
        if (orderIds.size() > MAX_ORDER_COUNT) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "单次最多查看 50 张采购订单");
        }
        return orderIds;
    }

    private List<PurchaseOrder> loadOrders(List<Long> orderIds) {
        List<PurchaseOrder> loadedOrders = purchaseOrderRepository.findByIdInAndDeletedFlagFalse(orderIds);
        if (loadedOrders.size() != orderIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部分采购订单不存在或已删除");
        }
        Map<Long, PurchaseOrder> orderById = loadedOrders.stream()
                .collect(Collectors.toMap(PurchaseOrder::getId, Function.identity()));
        return orderIds.stream().map(orderById::get).filter(Objects::nonNull).toList();
    }

    private int appendOrderItems(
            PurchaseOrder order,
            Map<GroupIdentity, MutableGroup> groups
    ) {
        GroupIdentity identity = GroupIdentity.from(order);
        MutableGroup group = null;
        int addedItemCount = 0;
        for (PurchaseOrderItem item : order.getItems().stream().sorted(ITEM_COMPARATOR).toList()) {
            if (group == null) {
                group = groups.computeIfAbsent(identity, ignored -> new MutableGroup(order, identity.key()));
            }
            group.add(
                    order,
                    item,
                    item.getQuantity(),
                    TradeItemCalculator.scaleWeightTon(item.getWeightTon())
            );
            addedItemCount++;
        }
        return addedItemCount;
    }

    private static String partyKey(Long id, String name) {
        return id == null ? "name:" + Objects.toString(name, "") : "id:" + id;
    }

    private record GroupIdentity(String supplierKey, String settlementCompanyKey) {
        static GroupIdentity from(PurchaseOrder order) {
            return new GroupIdentity(
                    partyKey(order.getSupplierId(), order.getSupplierName()),
                    partyKey(order.getSettlementCompanyId(), order.getSettlementCompanyName())
            );
        }

        String key() {
            return supplierKey + "|" + settlementCompanyKey;
        }
    }

    private static final class MutableGroup {
        private final String key;
        private final Long supplierId;
        private final String supplierName;
        private final Long settlementCompanyId;
        private final String settlementCompanyName;
        private final Set<Long> orderIds = new LinkedHashSet<>();
        private final List<PurchaseOrderPickupListResponse.Item> items = new ArrayList<>();
        private int totalQuantity;
        private BigDecimal totalWeightTon = BigDecimal.ZERO;

        private MutableGroup(PurchaseOrder order, String key) {
            this.key = key;
            this.supplierId = order.getSupplierId();
            this.supplierName = order.getSupplierName();
            this.settlementCompanyId = order.getSettlementCompanyId();
            this.settlementCompanyName = order.getSettlementCompanyName();
        }

        private void add(
                PurchaseOrder order,
                PurchaseOrderItem item,
                int pickupQuantity,
                BigDecimal pickupWeightTon
        ) {
            orderIds.add(order.getId());
            items.add(new PurchaseOrderPickupListResponse.Item(
                    item.getId(),
                    order.getId(),
                    order.getOrderNo(),
                    item.getLineNo(),
                    item.getWarehouseId(),
                    item.getWarehouseName(),
                    item.getBrand(),
                    item.getCategory(),
                    item.getMaterial(),
                    item.getSpec(),
                    item.getLength(),
                    pickupQuantity,
                    item.getPieceWeightTon(),
                    pickupWeightTon
            ));
            totalQuantity += pickupQuantity;
            totalWeightTon = totalWeightTon.add(pickupWeightTon);
        }

        private PurchaseOrderPickupListResponse.Group toResponse() {
            return new PurchaseOrderPickupListResponse.Group(
                    key,
                    supplierId,
                    supplierName,
                    settlementCompanyId,
                    settlementCompanyName,
                    orderIds.size(),
                    items.size(),
                    totalQuantity,
                    TradeItemCalculator.scaleWeightTon(totalWeightTon),
                    List.copyOf(items)
            );
        }
    }
}
