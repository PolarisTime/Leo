package com.leo.erp.statement.freight.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FreightStatementSourceServiceTest {

    @Test
    void shouldReturnFreightBillCandidates() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBill();
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        when(freightBillRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sourceBill)));

        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        List<FreightStatementCandidateResponse> candidates = service
                .candidatePage(PageQuery.of(0, 20, null, null), PageFilter.of("FB", "已审核", null, null))
                .getContent();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).billNo()).isEqualTo("FB-001");
        assertThat(candidates.get(0).carrierName()).isEqualTo("物流甲");
        assertThat(candidates.get(0).customerName()).isEqualTo("客户甲");
        assertThat(candidates.get(0).status()).isEqualTo(StatusConstants.AUDITED);
    }

    private FreightBill sourceBill() {
        FreightBill bill = new FreightBill();
        bill.setId(1L);
        bill.setBillNo("FB-001");
        bill.setCarrierName("物流甲");
        bill.setCustomerName("客户甲");
        bill.setProjectName("项目A");
        bill.setBillTime(LocalDate.of(2026, 5, 6));
        bill.setTotalWeight(new BigDecimal("1.000"));
        bill.setTotalFreight(new BigDecimal("100.00"));
        bill.setStatus(StatusConstants.AUDITED);
        return bill;
    }
}
