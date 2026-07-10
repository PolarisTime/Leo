package com.leo.erp.report.pendinginvoicereceipt.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.report.pendinginvoicereceipt.repository.PendingInvoiceReceiptReportQueryRepository;
import com.leo.erp.report.pendinginvoicereceipt.web.dto.PendingInvoiceReceiptReportResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingInvoiceReceiptReportServiceTest {

    @Mock
    private PendingInvoiceReceiptReportQueryRepository queryRepository;

    @InjectMocks
    private PendingInvoiceReceiptReportService service;

    @Test
    void pageDelegatesCompleteFilterAndPageWindowToQueryRepository() {
        PageQuery query = PageQuery.of(3, 50, "pendingInvoiceAmount", "asc");
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 30);
        var expected = new PageImpl<PendingInvoiceReceiptReportResponse>(
                List.of(), PageRequest.of(3, 50), 1_005);
        when(queryRepository.page(query, "钢材", "供应商A", startDate, endDate)).thenReturn(expected);

        var actual = service.page(query, "钢材", "供应商A", startDate, endDate);

        assertThat(actual).isSameAs(expected);
        verify(queryRepository).page(query, "钢材", "供应商A", startDate, endDate);
    }
}
