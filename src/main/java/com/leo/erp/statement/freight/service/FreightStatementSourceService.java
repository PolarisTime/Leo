package com.leo.erp.statement.freight.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import com.leo.erp.statement.service.StatementCandidateSupport;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

@Service
public class FreightStatementSourceService {

    private static final String[] FREIGHT_BILL_CANDIDATE_SEARCH_FIELDS = {
            "billNo",
            "outboundNo",
            "carrierName",
            "vehiclePlate",
            "customerName",
            "projectName"
    };

    private final FreightStatementRepository repository;
    private final FreightBillRepository freightBillRepository;

    public FreightStatementSourceService(FreightStatementRepository repository,
                                         FreightBillRepository freightBillRepository) {
        this.repository = repository;
        this.freightBillRepository = freightBillRepository;
    }

    Page<FreightStatementCandidateResponse> candidatePage(PageQuery query, PageFilter filter) {
        Set<String> occupiedBillNos = collectOccupiedBillNos(null);
        Specification<FreightBill> spec = Specs.<FreightBill>notDeleted()
                .and(Specs.keywordLike(filter.keyword(), FREIGHT_BILL_CANDIDATE_SEARCH_FIELDS))
                .and(Specs.equalIfPresent("carrierName", filter.name()))
                .and(Specs.equalIfPresent("status", StatusConstants.AUDITED))
                .and(Specs.betweenIfPresent("billTime", filter.startDate(), filter.endDate()))
                .and(StatementCandidateSupport.excludeFieldValues("billNo", occupiedBillNos));
        return freightBillRepository.findAll(DataScopeContext.apply(spec), query.toPageable("id"))
                .map(this::toCandidateResponse);
    }

    SourceApplyResult applyItems(FreightStatement entity,
                                 FreightStatementCommand command,
                                 LongSupplier nextIdSupplier) {
        List<FreightBill> sourceBills = loadSourceBills(command, entity.getId());
        BigDecimal totalWeight = BigDecimal.ZERO;
        List<FreightStatementItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                command.items(),
                FreightStatementItem::getId,
                FreightStatementItemCommand::id,
                FreightStatementItem::new,
                nextIdSupplier,
                FreightStatementItem::setId
        );
        for (int i = 0; i < command.items().size(); i++) {
            FreightStatementItemCommand source = command.items().get(i);
            FreightBill sourceBill = resolveSourceBill(sourceBills, source.sourceNo(), i + 1);
            FreightStatementItem item = items.get(i);
            item.setFreightStatement(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(sourceBill.getBillNo());
            item.setCustomerName(source.customerName());
            item.setProjectName(source.projectName());
            item.setMaterialCode(source.materialCode());
            item.setMaterialName(source.materialName());
            item.setBrand(source.brand());
            item.setCategory(source.category());
            item.setMaterial(source.material());
            item.setSpec(source.spec());
            item.setLength(source.length());
            item.setQuantity(source.quantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
            item.setPieceWeightTon(source.pieceWeightTon());
            item.setPiecesPerBundle(source.piecesPerBundle());
            item.setBatchNo(source.batchNo());
            BigDecimal weightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), source.pieceWeightTon());
            item.setWeightTon(weightTon);
            item.setWarehouseName(source.warehouseName());
            totalWeight = totalWeight.add(weightTon);
        }
        entity.getItems().sort(java.util.Comparator.comparing(FreightStatementItem::getLineNo));
        BigDecimal totalFreight = sourceBills.stream()
                .map(FreightBill::getTotalFreight)
                .map(TradeItemCalculator::scaleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SourceApplyResult(totalWeight, totalFreight);
    }

    Set<String> collectOccupiedBillNos(Long currentStatementId) {
        Specification<FreightStatement> spec = Specs.notDeleted();
        if (currentStatementId != null) {
            spec = spec.and((root, query, cb) -> cb.notEqual(root.get("id"), currentStatementId));
        }
        Set<String> occupiedBillNos = new LinkedHashSet<>();
        repository.findAll(spec).stream()
                .flatMap(entity -> entity.getItems().stream())
                .map(FreightStatementItem::getSourceNo)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .forEach(occupiedBillNos::add);
        return occupiedBillNos;
    }

    private List<FreightBill> loadSourceBills(FreightStatementCommand command, Long currentStatementId) {
        Set<String> requestedBillNos = command.items().stream()
                .map(FreightStatementItemCommand::sourceNo)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedBillNos.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "物流对账单来源物流单不能为空");
        }
        List<FreightBill> bills = freightBillRepository.findByBillNoInAndDeletedFlagFalse(requestedBillNos);
        Map<String, FreightBill> billMap = bills.stream()
                .collect(Collectors.toMap(FreightBill::getBillNo, bill -> bill));
        for (String billNo : requestedBillNos) {
            if (!billMap.containsKey(billNo)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单" + billNo + "不存在");
            }
        }
        for (FreightBill bill : bills) {
            DataScopeContext.assertCanAccess(bill);
            if (!command.carrierName().trim().equals(bill.getCarrierName())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单存在不同物流商，不能合并生成物流对账单");
            }
            if (!StatusConstants.AUDITED.equals(bill.getStatus())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单" + bill.getBillNo() + "未审核，不能生成物流对账单");
            }
        }
        assertSourceBillsNotOccupied(requestedBillNos, currentStatementId);
        return bills;
    }

    private void assertSourceBillsNotOccupied(Set<String> requestedBillNos, Long currentStatementId) {
        List<FreightStatement> occupiedStatements =
                repository.findAllBySourceNosExcludingCurrentStatement(requestedBillNos, currentStatementId);
        Set<String> occupiedBillNos = occupiedStatements.stream()
                .flatMap(entity -> entity.getItems().stream())
                .map(FreightStatementItem::getSourceNo)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String billNo : requestedBillNos) {
            if (occupiedBillNos.contains(billNo)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单" + billNo + "已生成物流对账单");
            }
        }
    }

    private FreightBill resolveSourceBill(List<FreightBill> bills, String sourceNo, int lineNo) {
        String normalizedSourceNo = sourceNo == null ? "" : sourceNo.trim();
        for (FreightBill bill : bills) {
            if (bill.getBillNo().equals(normalizedSourceNo)) {
                return bill;
            }
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源物流单不存在");
    }

    private FreightStatementCandidateResponse toCandidateResponse(FreightBill bill) {
        return new FreightStatementCandidateResponse(
                bill.getId(),
                bill.getBillNo(),
                bill.getCarrierName(),
                bill.getCustomerName(),
                bill.getProjectName(),
                bill.getBillTime(),
                bill.getTotalWeight(),
                bill.getTotalFreight(),
                bill.getStatus()
        );
    }

    record SourceApplyResult(
            BigDecimal totalWeight,
            BigDecimal totalFreight
    ) {
    }
}
