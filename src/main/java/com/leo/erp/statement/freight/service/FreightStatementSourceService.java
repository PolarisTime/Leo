package com.leo.erp.statement.freight.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.BillSnapshot;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.CandidateCriteria;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.CandidateSnapshot;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.ItemSnapshot;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.time.LocalDate;

@Service
public class FreightStatementSourceService {

    private final FreightStatementRepository repository;
    private final FreightBillStatementSourceQuery sourceQuery;

    public FreightStatementSourceService(FreightStatementRepository repository,
                                         FreightBillStatementSourceQuery sourceQuery) {
        this.repository = repository;
        this.sourceQuery = sourceQuery;
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
        FreightBillStatementSourceQuery.CandidatePage page = sourceQuery.findCandidates(new CandidateCriteria(
                query.page(),
                query.size(),
                query.sortBy(),
                query.direction(),
                filter.keyword(),
                filter.carrierId(),
                carrierCode,
                filter.name(),
                filter.settlementCompanyId(),
                filter.startDate(),
                filter.endDate(),
                List.copyOf(occupiedBillIds)
        ));
        return new PageImpl<>(
                page.content().stream().map(this::toCandidateResponse).toList(),
                query.toPageable("id"),
                page.totalElements()
        );
    }

    SourceApplyResult applyItems(FreightStatement entity,
                                 FreightStatementCommand command,
                                 LongSupplier nextIdSupplier) {
        validateStableSourceIds(command.items());
        List<BillSnapshot> sourceBills = loadSourceBills(command, entity.getId());
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
        Map<Long, Set<Long>> requestedItemIdsByBill = command.items().stream()
                .collect(Collectors.groupingBy(
                        FreightStatementItemCommand::sourceFreightBillId,
                        LinkedHashMap::new,
                        Collectors.mapping(FreightStatementItemCommand::sourceFreightBillItemId,
                                Collectors.toCollection(LinkedHashSet::new))));
        for (BillSnapshot sourceBill : sourceBills) {
            Set<Long> expectedItemIds = sourceBill.items().stream()
                    .map(ItemSnapshot::id)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<Long> requestedItemIds = requestedItemIdsByBill.getOrDefault(sourceBill.id(), Set.of());
            if (!expectedItemIds.equals(requestedItemIds)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "物流对账单必须整单导入来源物流单" + sourceBill.billNo() + "的全部明细");
            }
        }
        for (int i = 0; i < command.items().size(); i++) {
            FreightStatementItemCommand source = command.items().get(i);
            BillSnapshot sourceBill = resolveSourceBill(sourceBills, source, i + 1);
            ItemSnapshot sourceBillItem = resolveSourceBillItem(sourceBill, source, i + 1);
            validateRequestedIdentity(source, sourceBill, sourceBillItem, i + 1);
            FreightStatementItem item = items.get(i);
            item.setFreightStatement(entity);
            item.setLineNo(i + 1);
            item.setSourceNo(sourceBill.billNo());
            item.setSourceFreightBillId(sourceBill.id());
            item.setSourceFreightBillItemId(sourceBillItem.id());
            item.setSettlementCompanyId(sourceBillItem.settlementCompanyId());
            item.setSettlementCompanyName(sourceBillItem.settlementCompanyName());
            item.setCustomerId(sourceBillItem.customerId());
            item.setCustomerName(sourceBillItem.customerName());
            item.setProjectId(sourceBillItem.projectId());
            item.setProjectName(sourceBillItem.projectName());
            item.setMaterialId(sourceBillItem.materialId());
            item.setMaterialCode(sourceBillItem.materialCode());
            item.setMaterialName(sourceBillItem.materialName());
            item.setBrand(sourceBillItem.brand());
            item.setCategory(sourceBillItem.category());
            item.setMaterial(sourceBillItem.material());
            item.setSpec(sourceBillItem.spec());
            item.setLength(sourceBillItem.length());
            item.setQuantity(sourceBillItem.quantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(sourceBillItem.quantityUnit()));
            item.setPieceWeightTon(sourceBillItem.pieceWeightTon());
            item.setPiecesPerBundle(sourceBillItem.piecesPerBundle());
            item.setBatchNo(sourceBillItem.batchNo());
            BigDecimal weightTon = TradeItemCalculator.scaleWeightTon(sourceBillItem.weightTon());
            item.setWeightTon(weightTon);
            item.setWarehouseId(sourceBillItem.warehouseId());
            item.setWarehouseName(sourceBillItem.warehouseName());
            totalWeight = totalWeight.add(weightTon);
        }
        entity.getItems().sort(java.util.Comparator.comparing(FreightStatementItem::getLineNo));
        BigDecimal totalFreight = sourceBills.stream()
                .map(BillSnapshot::totalFreight)
                .map(TradeItemCalculator::scaleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate startDate = sourceBills.stream()
                .map(BillSnapshot::billTime)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单日期不能为空"));
        LocalDate endDate = sourceBills.stream()
                .map(BillSnapshot::billTime)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单日期不能为空"));
        return new SourceApplyResult(totalWeight, totalFreight, startDate, endDate);
    }

    private List<BillSnapshot> loadSourceBills(FreightStatementCommand command, Long currentStatementId) {
        Set<Long> requestedBillIds = command.items().stream()
                .map(FreightStatementItemCommand::sourceFreightBillId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedBillIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "物流对账单来源物流单不能为空");
        }
        Map<Long, BillSnapshot> billById = new LinkedHashMap<>();
        if (!requestedBillIds.isEmpty()) {
            sourceQuery.findByBillIds(requestedBillIds)
                    .forEach(bill -> billById.put(bill.id(), bill));
            for (Long requestedBillId : requestedBillIds) {
                if (!billById.containsKey(requestedBillId)) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                            "来源物流单ID" + requestedBillId + "不存在");
                }
            }
        }
        List<BillSnapshot> bills = List.copyOf(billById.values());
        for (BillSnapshot bill : bills) {
            if (command.settlementCompanyId() != null
                    && bill.settlementCompanyId() != null
                    && !command.settlementCompanyId().equals(bill.settlementCompanyId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单存在不同物流结算主体，不能合并生成物流对账单");
            }
            if (!StatusConstants.AUDITED.equals(bill.status())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单" + bill.billNo() + "未审核，不能生成物流对账单");
            }
        }
        assertSourceBillsNotOccupied(bills, currentStatementId);
        return bills;
    }

    private void assertSourceBillsNotOccupied(List<BillSnapshot> requestedBills, Long currentStatementId) {
        Set<Long> requestedBillIds = requestedBills.stream()
                .map(BillSnapshot::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> occupiedBillIds = toIdSet(
                repository.findMatchingOccupiedSourceFreightBillIdsExcludingCurrentStatement(
                        requestedBillIds,
                        currentStatementId
                )
        );
        for (BillSnapshot bill : requestedBills) {
            if (occupiedBillIds.contains(bill.id())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "来源物流单" + bill.billNo() + "已生成物流对账单");
            }
        }
    }

    private BillSnapshot resolveSourceBill(List<BillSnapshot> bills,
                                           FreightStatementItemCommand source,
                                           int lineNo) {
        return bills.stream()
                .filter(bill -> source.sourceFreightBillId().equals(bill.id()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源物流单不存在"));
    }

    private ItemSnapshot resolveSourceBillItem(BillSnapshot sourceBill,
                                               FreightStatementItemCommand source,
                                               int lineNo) {
        return sourceBill.items().stream()
                .filter(item -> source.sourceFreightBillItemId().equals(item.id()))
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
                                           BillSnapshot sourceBill,
                                           ItemSnapshot sourceItem,
                                           int lineNo) {
        requireSameId(requested.sourceFreightBillId(), sourceBill.id(), lineNo, "来源物流单");
        requireSameId(requested.sourceFreightBillItemId(), sourceItem.id(), lineNo, "来源物流单明细");
        requireSameId(requested.customerId(), sourceItem.customerId(), lineNo, "客户");
        requireSameId(requested.projectId(), sourceItem.projectId(), lineNo, "项目");
        requireSameId(requested.materialId(), sourceItem.materialId(), lineNo, "商品");
        requireSameId(requested.warehouseId(), sourceItem.warehouseId(), lineNo, "仓库");
    }

    private void requireSameId(Long requestedId, Long sourceId, int lineNo, String fieldName) {
        if (requestedId != null && !requestedId.equals(sourceId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行" + fieldName + "ID与来源物流单不一致");
        }
    }

    private SettlementCompanySnapshot resolveStatementSettlementCompany(List<BillSnapshot> sourceBills) {
        // 结算主体一致性按 id 判断：同一 id 下 name 快照可能存在历史写法差异（如"颖捷建材"与"嘉兴颖捷建材有限公司"），
        // 不应因 name 不一致而误判为不同结算主体。
        Set<Long> companyIds = sourceBills.stream()
                .map(BillSnapshot::settlementCompanyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> companyNames = sourceBills.stream()
                .map(BillSnapshot::settlementCompanyName)
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (companyIds.size() > 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单存在不同物流结算主体，不能合并生成物流对账单");
        }
        if (companyIds.size() == 1) {
            String name = companyNames.isEmpty() ? null : companyNames.get(0);
            return new SettlementCompanySnapshot(companyIds.iterator().next(), name);
        }
        if (companyNames.size() > 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源物流单存在不同物流结算主体，不能合并生成物流对账单");
        }
        if (companyNames.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "来源物流单物流结算主体缺失，不能生成物流对账单");
        }
        return new SettlementCompanySnapshot(null, companyNames.get(0));
    }

    private CarrierSnapshot resolveStatementCarrier(List<BillSnapshot> sourceBills,
                                                     Long requestedCarrierId,
                                                     String requestedCarrierCode) {
        List<CarrierSnapshot> snapshots = sourceBills.stream()
                .map(bill -> new CarrierSnapshot(
                        bill.carrierId(),
                        trimToNull(bill.carrierCode()),
                        trimToNull(bill.carrierName())
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
                .sorted(java.util.Comparator.comparing(BillSnapshot::billNo))
                .map(BillSnapshot::carrierName)
                .map(this::trimToNull)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "来源物流单物流商名称缺失，不能生成物流对账单"
                ));
        return new CarrierSnapshot(sourceCarrierId, sourceCarrierCode, sourceCarrierName);
    }

    private FreightStatementCandidateResponse toCandidateResponse(CandidateSnapshot bill) {
        return new FreightStatementCandidateResponse(
                bill.id(),
                bill.billNo(),
                bill.carrierCode(),
                bill.carrierName(),
                bill.settlementCompanyId(),
                bill.settlementCompanyName(),
                bill.customerName(),
                bill.projectName(),
                bill.billTime(),
                bill.totalWeight(),
                bill.totalFreight(),
                bill.status(),
                bill.carrierId()
        );
    }

    record SourceApplyResult(
            BigDecimal totalWeight,
            BigDecimal totalFreight,
            LocalDate startDate,
            LocalDate endDate
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
