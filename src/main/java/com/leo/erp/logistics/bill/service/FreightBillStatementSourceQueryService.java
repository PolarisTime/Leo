package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class FreightBillStatementSourceQueryService implements FreightBillStatementSourceQuery {

    private static final String[] CANDIDATE_SEARCH_FIELDS = {
            "billNo",
            "carrierCode",
            "carrierName",
            "vehiclePlate",
            "customerName",
            "projectName"
    };

    private final FreightBillRepository freightBillRepository;

    public FreightBillStatementSourceQueryService(FreightBillRepository freightBillRepository) {
        this.freightBillRepository = freightBillRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CandidatePage findCandidates(CandidateCriteria criteria) {
        Set<Long> excludedBillIds = new LinkedHashSet<>(criteria.excludedBillIds());
        Specification<FreightBill> specification = Specs.<FreightBill>notDeleted()
                .and(Specs.keywordLike(criteria.keyword(), CANDIDATE_SEARCH_FIELDS))
                .and(Specs.equalValueIfPresent("carrierId", criteria.carrierId()))
                .and(Specs.equalIfPresent("carrierCode", criteria.carrierCode()))
                .and(Specs.equalIfPresent("carrierName", criteria.carrierName()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", criteria.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", StatusConstants.AUDITED))
                .and(Specs.betweenIfPresent("billTime", criteria.startDate(), criteria.endDate()))
                .and(excludeIds(excludedBillIds));
        Page<CandidateSnapshot> page = freightBillRepository.findAll(specification, toPageable(criteria))
                .map(this::toCandidateSnapshot);
        return new CandidatePage(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<BillSnapshot> findByBillIds(Collection<Long> billIds) {
        Set<Long> requestedBillIds = billIds == null
                ? Set.of()
                : billIds.stream()
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (requestedBillIds.isEmpty()) {
            return List.of();
        }
        return freightBillRepository.findByIdInAndDeletedFlagFalse(requestedBillIds).stream()
                .map(this::toBillSnapshot)
                .toList();
    }

    private BillSnapshot toBillSnapshot(FreightBill bill) {
        return new BillSnapshot(
                bill.getId(),
                bill.getBillNo(),
                bill.getCarrierId(),
                bill.getCarrierCode(),
                bill.getCarrierName(),
                bill.getSettlementCompanyId(),
                bill.getSettlementCompanyName(),
                bill.getBillTime(),
                bill.getTotalFreight(),
                bill.getStatus(),
                bill.getItems().stream().map(this::toItemSnapshot).toList()
        );
    }

    private ItemSnapshot toItemSnapshot(FreightBillItem item) {
        return new ItemSnapshot(
                item.getId(),
                item.getSettlementCompanyId(),
                item.getSettlementCompanyName(),
                item.getCustomerId(),
                item.getCustomerName(),
                item.getProjectId(),
                item.getProjectName(),
                item.getMaterialId(),
                item.getMaterialCode(),
                item.getMaterialName(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getBatchNo(),
                item.getWeightTon(),
                item.getWarehouseId(),
                item.getWarehouseName()
        );
    }

    private CandidateSnapshot toCandidateSnapshot(FreightBill bill) {
        return new CandidateSnapshot(
                bill.getId(),
                bill.getBillNo(),
                bill.getCarrierCode(),
                bill.getCarrierName(),
                bill.getSettlementCompanyId(),
                bill.getSettlementCompanyName(),
                bill.getCustomerName(),
                bill.getProjectName(),
                bill.getBillTime(),
                bill.getTotalWeight(),
                bill.getTotalFreight(),
                bill.getStatus(),
                bill.getCarrierId()
        );
    }

    private Pageable toPageable(CandidateCriteria criteria) {
        String property = criteria.sortBy() == null || criteria.sortBy().isBlank() ? "id" : criteria.sortBy();
        Sort.Direction direction = "asc".equalsIgnoreCase(criteria.direction())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(criteria.page(), criteria.size(), Sort.by(direction, property));
    }

    private Specification<FreightBill> excludeIds(Set<Long> excludedIds) {
        return (root, query, builder) -> excludedIds.isEmpty()
                ? builder.conjunction()
                : builder.not(root.get("id").in(excludedIds));
    }
}
