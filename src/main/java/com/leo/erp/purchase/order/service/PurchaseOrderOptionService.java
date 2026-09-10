package com.leo.erp.purchase.order.service;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderOptionResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 采购订单下拉选项(供比价报价单关联/展示订货吨数)。 */
@Service
public class PurchaseOrderOptionService {

    private static final int MAX_OPTIONS = 200;
    private static final String NORMAL_STATUS = "正常";

    private final PurchaseOrderRepository repository;

    public PurchaseOrderOptionService(PurchaseOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderOptionResponse> listOptions(String keyword, String status) {
        Specification<PurchaseOrder> specification = (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("deletedFlag")));
            if (status != null && !status.isBlank()) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(builder.or(
                        builder.like(root.get("orderNo"), like),
                        builder.like(root.get("supplierName"), like)));
            }
            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return repository.findAll(specification, PageRequest.of(0, MAX_OPTIONS, Sort.by(Sort.Direction.DESC, "id")))
                .stream()
                .map(order -> new PurchaseOrderOptionResponse(
                        order.getId(), order.getOrderNo(), order.getSupplierName(),
                        order.getTotalWeight(), order.getStatus(), order.getOrderDate()))
                .toList();
    }

    /** 订购吨数合计(NORMAL 状态), 供"已开吨"汇总预留。 */
    @Transactional(readOnly = true)
    public java.math.BigDecimal sumOrderedWeight(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return java.math.BigDecimal.ZERO;
        }
        Specification<PurchaseOrder> specification = (root, query, builder) -> builder.and(
                builder.isFalse(root.get("deletedFlag")),
                root.get("id").in(orderIds));
        return repository.findAll(specification).stream()
                .map(PurchaseOrder::getTotalWeight)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }
}
