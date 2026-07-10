package com.leo.erp.report.pendinginvoicereceipt.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.report.pendinginvoicereceipt.repository.PendingInvoiceReceiptReportQueryRepository;
import com.leo.erp.report.pendinginvoicereceipt.web.dto.PendingInvoiceReceiptReportResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class PendingInvoiceReceiptReportService {

    private final PendingInvoiceReceiptReportQueryRepository queryRepository;

    public PendingInvoiceReceiptReportService(PendingInvoiceReceiptReportQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional(readOnly = true)
    public Page<PendingInvoiceReceiptReportResponse> page(PageQuery query,
                                                          String keyword,
                                                          String supplierName,
                                                          LocalDate startDate,
                                                          LocalDate endDate) {
        return queryRepository.page(query, keyword, supplierName, startDate, endDate);
    }
}
