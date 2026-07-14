package com.leo.erp.finance.purchaseflow.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.excel.service.ExcelExportService;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.finance.purchaseflow.repository.PurchaseFinanceFlowQueryRepository;
import com.leo.erp.finance.purchaseflow.web.dto.PurchaseFinanceDocumentFlowResponse;
import com.leo.erp.finance.purchaseflow.web.dto.PurchaseFinanceFlowExportRow;
import com.leo.erp.finance.purchaseflow.web.dto.PurchaseFinanceFlowFilter;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseFinanceFlowQueryService {

    private static final java.util.Set<String> DOCUMENT_TYPES = java.util.Set.of(
            "采购订单",
            "采购入库单",
            "供应商对账单",
            "采购收票单",
            "采购付款单",
            "收款单",
            "付款冲销单",
            "收款冲销单",
            "历史台账调整单"
    );

    private final PurchaseFinanceFlowQueryRepository repository;
    private final ExcelExportService excelExportService;

    public PurchaseFinanceFlowQueryService(PurchaseFinanceFlowQueryRepository repository,
                                           ExcelExportService excelExportService) {
        this.repository = repository;
        this.excelExportService = excelExportService;
    }

    @Transactional(readOnly = true)
    public PurchaseFinanceDocumentFlowResponse query(
            PurchaseFinanceFlowFilter filter,
            PageQuery pageQuery
    ) {
        PurchaseFinanceFlowFilter normalizedFilter = normalizeAndValidate(filter);
        return new PurchaseFinanceDocumentFlowResponse(
                repository.summary(normalizedFilter),
                repository.page(normalizedFilter, pageQuery)
        );
    }

    @Transactional(readOnly = true)
    public FileDownloadResponse exportExcel(PurchaseFinanceFlowFilter filter) {
        PurchaseFinanceFlowFilter normalizedFilter = normalizeAndValidate(filter);
        var rows = repository.listForExport(normalizedFilter)
                .stream()
                .map(PurchaseFinanceFlowExportRow::from)
                .toList();
        return new FileDownloadResponse(
                "采购财务单据流.xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                excelExportService.export(rows, PurchaseFinanceFlowExportRow.class)
        );
    }

    private PurchaseFinanceFlowFilter normalizeAndValidate(PurchaseFinanceFlowFilter filter) {
        if (filter == null || filter.settlementCompanyId() == null || filter.supplierId() == null
                || filter.settlementCompanyId() <= 0 || filter.supplierId() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结算主体和供应商不能为空");
        }
        if (filter.purchaseOrderId() != null && filter.purchaseOrderId() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "采购订单ID不合法");
        }
        if (filter.startDate() != null && filter.endDate() != null
                && filter.startDate().isAfter(filter.endDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "开始日期不能晚于结束日期");
        }
        String documentType = trimToNull(filter.documentType());
        if (documentType != null && !DOCUMENT_TYPES.contains(documentType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "单据类型不合法");
        }
        return new PurchaseFinanceFlowFilter(
                filter.settlementCompanyId(),
                filter.supplierId(),
                documentType,
                trimToNull(filter.status()),
                filter.startDate(),
                filter.endDate(),
                trimToNull(filter.materialKeyword()),
                filter.purchaseOrderId()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
