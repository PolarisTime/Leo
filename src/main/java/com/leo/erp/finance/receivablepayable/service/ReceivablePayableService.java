package com.leo.erp.finance.receivablepayable.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.excel.service.ExcelExportService;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.finance.receivablepayable.repository.ReceivablePayableQueryRepository;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableDetailResponse;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableExportRow;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableResponse;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
public class ReceivablePayableService {

    private static final Set<String> ALLOWED_DIRECTIONS = Set.of("应收", "应付");
    private static final Set<String> ALLOWED_COUNTERPARTY_TYPES = Set.of("客户", "供应商", "物流商");
    private static final Set<String> ALLOWED_STATUSES = Set.of("未结清", "已结清");
    private static final Set<String> ALLOWED_RECONCILIATION_STATUSES = Set.of("未对账", "已对账");

    private final ReceivablePayableQueryRepository queryRepository;
    private final ExcelExportService excelExportService;

    public ReceivablePayableService(ReceivablePayableQueryRepository queryRepository,
                                    ExcelExportService excelExportService) {
        this.queryRepository = queryRepository;
        this.excelExportService = excelExportService;
    }

    @Transactional(readOnly = true)
    public Page<ReceivablePayableResponse> page(PageQuery query,
                                                String businessDirection,
                                                String counterpartyType,
                                                String reconciliationStatus,
                                                String status,
                                                String keyword) {
        return page(query, businessDirection, counterpartyType, null, reconciliationStatus, status, keyword);
    }

    @Transactional(readOnly = true)
    public Page<ReceivablePayableResponse> page(PageQuery query,
                                                String businessDirection,
                                                String counterpartyType,
                                                Long settlementCompanyId,
                                                String reconciliationStatus,
                                                String status,
                                                String keyword) {
        String normalizedDirection = validateDirection(businessDirection);
        String normalizedCounterpartyType = validateCounterpartyType(counterpartyType);
        String normalizedReconciliationStatus = validateReconciliationStatus(reconciliationStatus);
        String normalizedStatus = validateStatus(status);
        return queryRepository.page(
                query,
                normalizedDirection,
                normalizedCounterpartyType,
                settlementCompanyId,
                normalizedReconciliationStatus,
                normalizedStatus,
                keyword
        );
    }

    @Transactional(readOnly = true)
    public ReceivablePayableDetailResponse detail(String id) {
        SummaryKey key = parseSummaryKey(id);
        ReceivablePayableResponse summary = queryRepository.findSummary(
                key.direction(),
                key.counterpartyType(),
                key.counterpartyKey(),
                key.settlementCompanyKey(),
                key.reconciliationStatus()
        );
        if (summary == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "应收应付汇总不存在");
        }
        return new ReceivablePayableDetailResponse(
                summary.id(),
                summary.direction(),
                summary.counterpartyType(),
                summary.counterpartyCode(),
                summary.counterpartyName(),
                summary.settlementCompanyId(),
                summary.settlementCompanyName(),
                summary.reconciliationStatus(),
                safe(summary.recognizedAmount()),
                safe(summary.settledAmount()),
                safe(summary.balanceAmount()),
                safe(summary.days0To30Amount()),
                safe(summary.days31To60Amount()),
                safe(summary.days61To90Amount()),
                safe(summary.daysOver90Amount()),
                summary.entryCount(),
                summary.status(),
                summary.remark(),
                queryRepository.detailItems(
                        key.direction(),
                        key.counterpartyType(),
                        key.counterpartyKey(),
                        key.settlementCompanyKey(),
                        key.reconciliationStatus()
                )
        );
    }

    @Transactional(readOnly = true)
    public FileDownloadResponse exportExcel(String businessDirection,
                                            String counterpartyType,
                                            String reconciliationStatus,
                                            String status,
                                            String keyword) {
        return exportExcel(businessDirection, counterpartyType, null, reconciliationStatus, status, keyword);
    }

    @Transactional(readOnly = true)
    public FileDownloadResponse exportExcel(String businessDirection,
                                            String counterpartyType,
                                            Long settlementCompanyId,
                                            String reconciliationStatus,
                                            String status,
                                            String keyword) {
        String normalizedDirection = validateDirection(businessDirection);
        String normalizedCounterpartyType = validateCounterpartyType(counterpartyType);
        String normalizedReconciliationStatus = validateReconciliationStatus(reconciliationStatus);
        String normalizedStatus = validateStatus(status);
        List<ReceivablePayableExportRow> rows = queryRepository.listForExport(
                        normalizedDirection,
                        normalizedCounterpartyType,
                        settlementCompanyId,
                        normalizedReconciliationStatus,
                        normalizedStatus,
                        keyword
                )
                .stream()
                .map(ReceivablePayableExportRow::from)
                .toList();
        return new FileDownloadResponse(
                "应收应付汇总.xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                excelExportService.export(rows, ReceivablePayableExportRow.class)
        );
    }

    private String validateDirection(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (!ALLOWED_DIRECTIONS.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "direction 不合法");
        }
        return normalized;
    }

    private String validateCounterpartyType(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (!ALLOWED_COUNTERPARTY_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "counterpartyType 不合法");
        }
        return normalized;
    }

    private String validateStatus(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status 不合法");
        }
        return normalized;
    }

    private String validateReconciliationStatus(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (!ALLOWED_RECONCILIATION_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "reconciliationStatus 不合法");
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SummaryKey parseSummaryKey(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应收应付汇总ID不能为空");
        }
        String[] parts = id.split(":", 5);
        if (parts.length != 5) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应收应付汇总ID不合法");
        }
        String direction = validateDirection(parts[0]);
        String counterpartyType = validateCounterpartyType(parts[1]);
        String reconciliationStatus = validateReconciliationStatus(parts[2]);
        String settlementCompanyKey = normalizeSettlementCompanyKey(parts[3]);
        String counterpartyKey = parts[4].trim();
        if (direction == null
                || counterpartyType == null
                || reconciliationStatus == null
                || settlementCompanyKey == null
                || !isValidCounterpartyKey(counterpartyKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应收应付汇总ID不合法");
        }
        if (("客户".equals(counterpartyType) && !"应收".equals(direction))
                || (("供应商".equals(counterpartyType) || "物流商".equals(counterpartyType)) && !"应付".equals(direction))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应收应付汇总ID方向不合法");
        }
        return new SummaryKey(
                direction,
                counterpartyType,
                reconciliationStatus,
                settlementCompanyKey,
                normalizeCounterpartyKey(counterpartyKey)
        );
    }

    private boolean isValidCounterpartyKey(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim();
        return normalized.matches("[A-Za-z0-9._-]{1,64}")
                || normalized.matches("name:[a-fA-F0-9]{32}");
    }

    private String normalizeCounterpartyKey(String value) {
        String normalized = value.trim();
        if (normalized.matches("name:[a-fA-F0-9]{32}")) {
            return "name:" + normalized.substring("name:".length()).toLowerCase();
        }
        return normalized;
    }

    private String normalizeSettlementCompanyKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if ("none".equals(normalized)) {
            return normalized;
        }
        if (!normalized.matches("[1-9][0-9]{0,18}")) {
            return null;
        }
        try {
            return String.valueOf(Long.parseLong(normalized));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record SummaryKey(String direction,
                              String counterpartyType,
                              String reconciliationStatus,
                              String settlementCompanyKey,
                              String counterpartyKey) {
    }
}
