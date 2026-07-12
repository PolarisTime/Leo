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
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

@Service
public class FreightStatementSourceService {

    private static final String[] FREIGHT_BILL_CANDIDATE_SEARCH_FIELDS = {
            "billNo",
            "outboundNo",
            "carrierCode",
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
        return candidatePage(query, filter, null);
    }

    Page<FreightStatementCandidateResponse> candidatePage(PageQuery query,
                                                           PageFilter filter,
                                                           String carrierCode) {
        Set<Long> occupiedBillIds = toIdSet(
                repository.findOccupiedSourceFreightBillIdsExcludingCurrentStatement(filter.currentRecordId())
        );
        Specification<FreightBill> spec = Specs.<FreightBill>notDeleted()
                .and(Specs.keywordLike(filter.keyword(), FREIGHT_BILL_CANDIDATE_SEARCH_FIELDS))
                .and(Specs.equalValueIfPresent("carrierId", filter.carrierId()))
                .and(Specs.equalIfPresent("carrierCode", carrierCode))
                .and(Specs.equalIfPresent("carrierName", filter.name()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", StatusConstants.AUDITED))
                .and(Specs.betweenIfPresent("billTime", filter.startDate(), filter.endDate()))
                .and(StatementCandidateSupport.excludeFieldValues("id", occupiedBillIds));
        return freightBillRepository.findAll(DataScopeContext.apply(spec), query.toPageable("id"))
                .map(this::toCandidateResponse);
    }

    SourceApplyResult applyItems(FreightStatement entity,
                                 FreightStatementCommand command,
                                 LongSupplier nextIdSupplier) {
        validateStableSourceIds(command.items());
        List<FreightBill> sourceBills = loadSourceBills(command, entity.getId());
        CarrierSnapshot carrier = resolveStatementCarrier(sourceBills, command.carrierId(), command.carrierCode());
        entity.setCarrierId(carrier.id());
        entity.setCarrierCode(carrier.code());
        entity.setCarrierName(carrier.name());
        SettlementCompanySnapshot settlementCompany = resolveStatementSettlementCompany(sourceBills);
        entity.setSettlementCompanyId(settlementCompany.id());
        entity.setSettlementCompanyName(settlementCompany.name());
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
            FreightBill sourceBill = resolveSourceBill(sourceBills, source, i + 1);
            FreightBillItem sourceBillItem = resolveSourceBillItem(sourceBill, source, i + 1);
            validateRequestedIdentity(source, sourceBill, sourceBillItem, i + 1);
            FreightStatementItem item = items.get(i);
            item.setFreightStatement(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(sourceBill.getBillNo());
            item.setSourceFreightBillId(sourceBill.getId());
            item.setSourceFreightBillItemId(sourceBillItem.getId());
            item.setSourceSalesOutboundItemId(sourceBillItem.getSourceSalesOutboundItemId());
            item.setSettlementCompanyId(sourceBillItem.getSettlementCompanyId());
            item.setSettlementCompanyName(sourceBillItem.getSettlementCompanyName());
            item.setCustomerId(sourceBillItem.getCustomerId());
            item.setCustomerName(sourceBillItem.getCustomerName());
            item.setProjectId(sourceBillItem.getProjectId());
            item.setProjectName(sourceBillItem.getProjectName());
            item.setMaterialId(sourceBillItem.getMaterialId());
            item.setMaterialCode(sourceBillItem.getMaterialCode());
            item.setMaterialName(sourceBillItem.getMaterialName());
            item.setBrand(sourceBillItem.getBrand());
            item.setCategory(sourceBillItem.getCategory());
            item.setMaterial(sourceBillItem.getMaterial());
            item.setSpec(sourceBillItem.getSpec());
            item.setLength(sourceBillItem.getLength());
            item.setQuantity(sourceBillItem.getQuantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(sourceBillItem.getQuantityUnit()));
            item.setPieceWeightTon(sourceBillItem.getPieceWeightTon());
            item.setPiecesPerBundle(sourceBillItem.getPiecesPerBundle());
            item.setBatchNo(sourceBillItem.getBatchNo());
            BigDecimal weightTon = TradeItemCalculator.scaleWeightTon(sourceBillItem.getWeightTon());
            item.setWeightTon(weightTon);
            item.setWarehouseId(sourceBillItem.getWarehouseId());
            item.setWarehouseName(sourceBillItem.getWarehouseName());
            totalWeight = totalWeight.add(weightTon);
        }
        entity.getItems().sort(java.util.Comparator.comparing(FreightStatementItem::getLineNo));
        BigDecimal totalFreight = sourceBills.stream()
                .map(FreightBill::getTotalFreight)
                .map(TradeItemCalculator::scaleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SourceApplyResult(totalWeight, totalFreight);
    }

    private List<FreightBill> loadSourceBills(FreightStatementCommand command, Long currentStatementId) {
        Set<Long> requestedBillIds = command.items().stream()
                .map(FreightStatementItemCommand::sourceFreightBillId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedBillIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "物流对账单来源物流单不能为空");
        }
        Map<Long, FreightBill> billById = new LinkedHashMap<>();
        if (!requestedBillIds.isEmpty()) {
            freightBillRepository.findByIdInAndDeletedFlagFalse(requestedBillIds)
                    .forEach(bill -> billById.put(bill.getId(), bill));
            for (Long requestedBillId : requestedBillIds) {
                if (!billById.containsKey(requestedBillId)) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                            "来源物流单ID" + requestedBillId + "不存在");
                }
            }
        }
        List<FreightBill> bills = new ArrayList<>(billById.values());
        for (FreightBill bill : bills) {
            DataScopeContext.assertCanAccess(bill);
            if (command.settlementCompanyId() != null
                    && bill.getSettlementCompanyId() != null
                    && !command.settlementCompanyId().equals(bill.getSettlementCompanyId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单存在不同物流结算主体，不能合并生成物流对账单");
            }
            if (!StatusConstants.AUDITED.equals(bill.getStatus())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单" + bill.getBillNo() + "未审核，不能生成物流对账单");
            }
        }
        assertSourceBillsNotOccupied(bills, currentStatementId);
        return bills;
    }

    private void assertSourceBillsNotOccupied(List<FreightBill> requestedBills, Long currentStatementId) {
        Set<Long> requestedBillIds = requestedBills.stream()
                .map(FreightBill::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> occupiedBillIds = toIdSet(
                repository.findMatchingOccupiedSourceFreightBillIdsExcludingCurrentStatement(
                        requestedBillIds,
                        currentStatementId
                )
        );
        for (FreightBill bill : requestedBills) {
            if (occupiedBillIds.contains(bill.getId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "来源物流单" + bill.getBillNo() + "已生成物流对账单");
            }
        }
    }

    private FreightBill resolveSourceBill(List<FreightBill> bills,
                                          FreightStatementItemCommand source,
                                          int lineNo) {
        return bills.stream()
                .filter(bill -> source.sourceFreightBillId().equals(bill.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源物流单不存在"));
    }

    private FreightBillItem resolveSourceBillItem(FreightBill sourceBill, FreightStatementItemCommand source, int lineNo) {
        return sourceBill.getItems().stream()
                .filter(item -> source.sourceFreightBillItemId().equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源物流单明细不存在"));
    }

    private void validateStableSourceIds(List<FreightStatementItemCommand> items) {
        Set<Long> sourceItemIds = new LinkedHashSet<>();
        for (int index = 0; index < items.size(); index++) {
            FreightStatementItemCommand item = items.get(index);
            int lineNo = index + 1;
            if (item.sourceFreightBillId() == null) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "第" + lineNo + "行来源物流单ID不能为空"
                );
            }
            if (item.sourceFreightBillItemId() == null) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "第" + lineNo + "行来源物流单明细ID不能为空"
                );
            }
            if (!sourceItemIds.add(item.sourceFreightBillItemId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "来源物流单明细ID重复");
            }
        }
    }

    private void validateRequestedIdentity(FreightStatementItemCommand requested,
                                           FreightBill sourceBill,
                                           FreightBillItem sourceItem,
                                           int lineNo) {
        requireSameId(requested.sourceFreightBillId(), sourceBill.getId(), lineNo, "来源物流单");
        requireSameId(requested.sourceFreightBillItemId(), sourceItem.getId(), lineNo, "来源物流单明细");
        requireSameId(requested.customerId(), sourceItem.getCustomerId(), lineNo, "客户");
        requireSameId(requested.projectId(), sourceItem.getProjectId(), lineNo, "项目");
        requireSameId(requested.materialId(), sourceItem.getMaterialId(), lineNo, "商品");
        requireSameId(requested.warehouseId(), sourceItem.getWarehouseId(), lineNo, "仓库");
    }

    private void requireSameId(Long requestedId, Long sourceId, int lineNo, String fieldName) {
        if (requestedId != null && !requestedId.equals(sourceId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行" + fieldName + "ID与来源物流单不一致");
        }
    }

    private SettlementCompanySnapshot resolveStatementSettlementCompany(List<FreightBill> sourceBills) {
        List<SettlementCompanySnapshot> snapshots = sourceBills.stream()
                .map(bill -> new SettlementCompanySnapshot(bill.getSettlementCompanyId(), trimToNull(bill.getSettlementCompanyName())))
                .filter(snapshot -> snapshot.id() != null || snapshot.name() != null)
                .distinct()
                .toList();
        if (snapshots.isEmpty()) {
            return new SettlementCompanySnapshot(null, null);
        }
        if (snapshots.size() > 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单存在不同物流结算主体，不能合并生成物流对账单");
        }
        return snapshots.get(0);
    }

    private CarrierSnapshot resolveStatementCarrier(List<FreightBill> sourceBills,
                                                    Long requestedCarrierId,
                                                    String requestedCarrierCode) {
        List<CarrierSnapshot> snapshots = sourceBills.stream()
                .map(bill -> new CarrierSnapshot(
                        bill.getCarrierId(),
                        trimToNull(bill.getCarrierCode()),
                        trimToNull(bill.getCarrierName())
                ))
                .toList();
        if (snapshots.stream().anyMatch(snapshot -> snapshot.code() == null)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单物流商编码缺失，不能生成物流对账单");
        }
        Set<String> carrierCodes = snapshots.stream()
                .map(CarrierSnapshot::code)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (carrierCodes.size() != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单存在不同物流商编码，不能合并生成物流对账单");
        }
        Set<Long> carrierIds = snapshots.stream()
                .map(CarrierSnapshot::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (carrierIds.size() > 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "来源物流单存在不同物流商ID，不能合并生成物流对账单");
        }
        Long sourceCarrierId = carrierIds.stream().findFirst().orElse(null);
        if (requestedCarrierId != null && !Objects.equals(requestedCarrierId, sourceCarrierId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商ID与来源物流单不一致");
        }
        String sourceCarrierCode = carrierCodes.iterator().next();
        String normalizedRequestedCode = trimToNull(requestedCarrierCode);
        if (normalizedRequestedCode != null && !normalizedRequestedCode.equals(sourceCarrierCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商编码与来源物流单不一致");
        }
        String sourceCarrierName = sourceBills.stream()
                .sorted(java.util.Comparator.comparing(FreightBill::getBillNo))
                .map(FreightBill::getCarrierName)
                .map(this::trimToNull)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "来源物流单物流商名称缺失，不能生成物流对账单"
                ));
        return new CarrierSnapshot(sourceCarrierId, sourceCarrierCode, sourceCarrierName);
    }

    private FreightStatementCandidateResponse toCandidateResponse(FreightBill bill) {
        return new FreightStatementCandidateResponse(
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
                bill.getStatus()
                , bill.getCarrierId()
        );
    }

    record SourceApplyResult(
            BigDecimal totalWeight,
            BigDecimal totalFreight
    ) {
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Set<Long> toIdSet(Collection<Long> ids) {
        return ids == null ? Set.of() : new LinkedHashSet<>(ids);
    }

    private record SettlementCompanySnapshot(Long id, String name) {
    }

    private record CarrierSnapshot(Long id, String code, String name) {
    }
}
