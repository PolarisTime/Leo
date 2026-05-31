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
    private static final Set<String> ALLOWED_STATUSES = Set.of("待确认", "已确认", "待审核", "已审核");

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
                                                String status,
                                                String keyword) {
        String normalizedDirection = validateDirection(businessDirection);
        String normalizedCounterpartyType = validateCounterpartyType(counterpartyType);
        String normalizedStatus = validateStatus(status);
        return queryRepository.page(query, normalizedDirection, normalizedCounterpartyType, normalizedStatus, keyword);
    }

    @Transactional(readOnly = true)
    public ReceivablePayableDetailResponse detail(String id) {
        SummaryKey key = parseSummaryKey(id);
        ReceivablePayableResponse summary = queryRepository.findSummary(
                key.direction(),
                key.counterpartyType(),
                key.counterpartyKey()
        );
        if (summary == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "应收应付汇总不存在");
        }
        return new ReceivablePayableDetailResponse(
                summary.id(),
                summary.direction(),
                summary.counterpartyType(),
                summary.counterpartyName(),
                safe(summary.openingAmount()),
                safe(summary.currentAmount()),
                safe(summary.settledAmount()),
                safe(summary.balanceAmount()),
                summary.documentCount(),
                summary.status(),
                summary.remark(),
                queryRepository.detailItems(
                        key.direction(),
                        key.counterpartyType(),
                        key.counterpartyKey()
                )
        );
    }

    @Transactional(readOnly = true)
    public FileDownloadResponse exportExcel(String businessDirection,
                                            String counterpartyType,
                                            String status,
                                            String keyword) {
        String normalizedDirection = validateDirection(businessDirection);
        String normalizedCounterpartyType = validateCounterpartyType(counterpartyType);
        String normalizedStatus = validateStatus(status);
        List<ReceivablePayableExportRow> rows = queryRepository.listForExport(
                        normalizedDirection,
                        normalizedCounterpartyType,
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

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SummaryKey parseSummaryKey(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应收应付汇总ID不能为空");
        }
        String[] parts = id.split(":", 3);
        if (parts.length != 3) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应收应付汇总ID不合法");
        }
        String direction = validateDirection(parts[0]);
        String counterpartyType = validateCounterpartyType(parts[1]);
        String counterpartyKey = parts[2] == null ? "" : parts[2].trim();
        if (direction == null || counterpartyType == null || !counterpartyKey.matches("[a-fA-F0-9]{32}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应收应付汇总ID不合法");
        }
        if (("客户".equals(counterpartyType) && !"应收".equals(direction))
                || (("供应商".equals(counterpartyType) || "物流商".equals(counterpartyType)) && !"应付".equals(direction))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应收应付汇总ID方向不合法");
        }
        return new SummaryKey(direction, counterpartyType, counterpartyKey.toLowerCase());
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record SummaryKey(String direction, String counterpartyType, String counterpartyKey) {
    }
}
