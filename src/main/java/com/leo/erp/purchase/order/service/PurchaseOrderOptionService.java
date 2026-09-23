package com.leo.erp.purchase.order.service;

import com.leo.erp.common.persistence.Specs;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderOptionResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** 采购订单下拉选项(供比价报价单关联/展示订货吨数)。 */
@Service
public class PurchaseOrderOptionService implements PurchaseOrderOptionQuery {

    private static final int MAX_OPTIONS = 200;

    private final PurchaseOrderRepository repository;

    public PurchaseOrderOptionService(PurchaseOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderOptionResponse> listOptions(String keyword, String status) {
        return listActiveOptions(keyword, status).stream()
                .map(snapshot -> new PurchaseOrderOptionResponse(
                        snapshot.id(), snapshot.orderNo(), snapshot.supplierName(),
                        snapshot.totalWeight(), snapshot.status(), snapshot.orderDate()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderOptionSnapshot> listActiveOptions(String keyword, String status) {
        Specification<PurchaseOrder> specification = (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(Specs.notDeletedPredicate(root, builder));
            if (status != null && !status.isBlank()) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(Specs.<PurchaseOrder>keywordLike(keyword, "orderNo", "supplierName")
                        .toPredicate(root, query, builder));
            }
            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return repository.findAll(specification, PageRequest.of(0, MAX_OPTIONS, Sort.by(Sort.Direction.DESC, "id")))
                .stream()
                .map(PurchaseOrderOptionService::toSnapshot)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderOptionSnapshot> listActiveByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        return repository.findByIdInAndDeletedFlagFalse(distinctIds).stream()
                .map(PurchaseOrderOptionService::toSnapshot)
                .toList();
    }

    private static PurchaseOrderOptionSnapshot toSnapshot(PurchaseOrder order) {
        return new PurchaseOrderOptionSnapshot(
                order.getId(),
                order.getOrderNo(),
                order.getSupplierName(),
                order.getTotalWeight(),
                order.getStatus(),
                order.getOrderDate());
    }
}
