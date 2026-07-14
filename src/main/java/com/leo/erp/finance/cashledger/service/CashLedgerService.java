package com.leo.erp.finance.cashledger.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.excel.service.ExcelExportService;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.finance.cashledger.repository.CashLedgerFilter;
import com.leo.erp.finance.cashledger.repository.CashLedgerQueryRepository;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerExportRow;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerPageResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Service
public class CashLedgerService {

    private static final Set<String> COUNTERPARTY_TYPES = Set.of("客户", "供应商", "物流商");
    private static final Set<String> FLOW_TYPES = Set.of(
            "RECEIPT",
            "PAYMENT",
            "PAYMENT_REVERSAL",
            "RECEIPT_REVERSAL"
    );

    private final CashLedgerQueryRepository queryRepository;
    private final ExcelExportService excelExportService;

    public CashLedgerService(CashLedgerQueryRepository queryRepository,
                             ExcelExportService excelExportService) {
        this.queryRepository = queryRepository;
        this.excelExportService = excelExportService;
    }

    @Transactional(readOnly = true)
    public CashLedgerPageResponse page(PageQuery query,
                                       Long settlementCompanyId,
                                       LocalDate startDate,
                                       LocalDate endDate,
                                       String counterpartyType,
                                       Long counterpartyId,
                                       String flowType,
                                       String keyword) {
        return queryRepository.page(
                normalizeFilter(
                        settlementCompanyId,
                        startDate,
                        endDate,
                        counterpartyType,
                        counterpartyId,
                        flowType,
                        keyword
                ),
                query
        );
    }

    @Transactional(readOnly = true)
    public FileDownloadResponse exportExcel(Long settlementCompanyId,
                                            LocalDate startDate,
                                            LocalDate endDate,
                                            String counterpartyType,
                                            Long counterpartyId,
                                            String flowType,
                                            String keyword) {
        CashLedgerFilter filter = normalizeFilter(
                settlementCompanyId,
                startDate,
                endDate,
                counterpartyType,
                counterpartyId,
                flowType,
                keyword
        );
        var rows = queryRepository.listForExport(filter).stream()
                .map(CashLedgerExportRow::from)
                .toList();
        return new FileDownloadResponse(
                "资金流水.xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                excelExportService.export(rows, CashLedgerExportRow.class)
        );
    }

    private CashLedgerFilter normalizeFilter(Long settlementCompanyId,
                                              LocalDate startDate,
                                              LocalDate endDate,
                                              String counterpartyType,
                                              Long counterpartyId,
                                              String flowType,
                                              String keyword) {
        if (settlementCompanyId == null || settlementCompanyId <= 0L) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结算主体ID必须为正数");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "开始日期不能晚于结束日期");
        }
        String normalizedCounterpartyType = trimToNull(counterpartyType);
        if (normalizedCounterpartyType != null && !COUNTERPARTY_TYPES.contains(normalizedCounterpartyType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "往来类型不合法");
        }
        if (counterpartyId != null && counterpartyId <= 0L) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "往来单位ID必须为正数");
        }
        if (counterpartyId != null && normalizedCounterpartyType == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "指定往来单位ID时必须同时指定往来类型");
        }
        String normalizedFlowType = normalizeFlowType(flowType);
        return new CashLedgerFilter(
                settlementCompanyId,
                startDate,
                endDate,
                normalizedCounterpartyType,
                counterpartyId,
                normalizedFlowType,
                trimToNull(keyword)
        );
    }

    private String normalizeFlowType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!FLOW_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "流水类型不合法");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
