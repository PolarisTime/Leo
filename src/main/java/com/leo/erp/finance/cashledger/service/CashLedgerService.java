package com.leo.erp.finance.cashledger.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.excel.service.ExcelExportService;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.finance.cashledger.repository.CashLedgerFilter;
import com.leo.erp.finance.cashledger.repository.CashLedgerQueryRepository;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerExportRow;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerLineResponse;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerPageResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
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

    /**
     * 单次资金流水导出的行数上限。
     *
     * <p>导出路径是「全量行 → JVM List&lt;Row&gt; → XLSX 字节数组」，行对象 + XLSX 模型 + 成品字节
     * 会同时驻留堆内；不设上限时，一个「今天全量」的导出就可能打爆堆内存，并外溢成全站 GC/超时。
     * 与 {@code ExportConcurrencyGuard} 的并发闸门一起构成两道「保命」防线：
     * 闸门限制同时导出数，本上限限制单次导出的规模。</p>
     */
    public static final int MAX_EXPORT_ROWS = 200_000;

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
        // 先按「上限 + 1」取行：多出来的那一行是溢出哨兵，命中即 422 提示缩小范围，
        // 绝不把超出上限的行读进堆（也不去生成注定要丢掉的 XLSX）。
        List<CashLedgerLineResponse> lines = queryRepository.listForExport(filter, MAX_EXPORT_ROWS);
        if (lines.size() > MAX_EXPORT_ROWS) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "导出数据量超过上限 " + MAX_EXPORT_ROWS + " 行，请缩小日期范围或增加筛选条件后重试"
            );
        }
        var rows = lines.stream()
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
