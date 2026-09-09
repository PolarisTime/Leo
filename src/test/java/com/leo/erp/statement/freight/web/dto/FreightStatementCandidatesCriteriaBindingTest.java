package com.leo.erp.statement.freight.web.dto;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.ServletRequestDataBinder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FreightStatementCandidatesCriteria 绑定语义测试：参数名、可选性、日期格式。
 */
class FreightStatementCandidatesCriteriaBindingTest {

    private DefaultFormattingConversionService conversionService;

    @BeforeEach
    void setUp() {
        conversionService = new DefaultFormattingConversionService();
    }

    @Test
    void bindsAllQueryParamsByName() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("keyword", "kw");
        request.setParameter("carrierId", "100");
        request.setParameter("carrierCode", "C001");
        request.setParameter("carrierName", "承运商A");
        request.setParameter("settlementCompanyId", "30");
        request.setParameter("startDate", "2026-08-01");
        request.setParameter("endDate", "2026-08-31");
        request.setParameter("currentStatementId", "5");

        ServletRequestDataBinder binder = new ServletRequestDataBinder(new FreightStatementCandidatesCriteria(), "criteria");
        binder.setConversionService(conversionService);
        binder.bind(request);
        assertThat(binder.getBindingResult().getAllErrors())
                .withFailMessage("绑定失败: " + binder.getBindingResult().getAllErrors())
                .isEmpty();

        FreightStatementCandidatesCriteria criteria = (FreightStatementCandidatesCriteria) binder.getTarget();
        assertThat(criteria.getKeyword()).isEqualTo("kw");
        assertThat(criteria.getCarrierId()).isEqualTo(100L);
        assertThat(criteria.getCarrierCode()).isEqualTo("C001");
        assertThat(criteria.getCarrierName()).isEqualTo("承运商A");
        assertThat(criteria.getSettlementCompanyId()).isEqualTo(30L);
        assertThat(criteria.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(criteria.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(criteria.getCurrentStatementId()).isEqualTo(5L);
    }

    @Test
    void allFieldsOptional() {
        ServletRequestDataBinder binder = new ServletRequestDataBinder(new FreightStatementCandidatesCriteria(), "criteria");
        binder.setConversionService(conversionService);
        binder.bind(new MockHttpServletRequest());

        assertThat(binder.getBindingResult().getAllErrors()).isEmpty();

        FreightStatementCandidatesCriteria criteria = (FreightStatementCandidatesCriteria) binder.getTarget();
        assertThat(criteria.getKeyword()).isNull();
        assertThat(criteria.getCarrierId()).isNull();
        assertThat(criteria.getCarrierCode()).isNull();
        assertThat(criteria.getCarrierName()).isNull();
        assertThat(criteria.getSettlementCompanyId()).isNull();
        assertThat(criteria.getStartDate()).isNull();
        assertThat(criteria.getEndDate()).isNull();
        assertThat(criteria.getCurrentStatementId()).isNull();
    }

    @Test
    void isoLocalDateFormat() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("startDate", "2026-03-01");
        request.setParameter("endDate", "2026-09-30");

        ServletRequestDataBinder binder = new ServletRequestDataBinder(new FreightStatementCandidatesCriteria(), "criteria");
        binder.setConversionService(conversionService);
        binder.bind(request);

        FreightStatementCandidatesCriteria criteria = (FreightStatementCandidatesCriteria) binder.getTarget();
        assertThat(criteria.getStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(criteria.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void nonIsoDateProducesTypeMismatch() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("startDate", "2026.03.01");

        ServletRequestDataBinder binder = new ServletRequestDataBinder(new FreightStatementCandidatesCriteria(), "criteria");
        binder.setConversionService(conversionService);
        binder.bind(request);

        assertThat(binder.getBindingResult().getFieldErrorCount("startDate")).isEqualTo(1);
        assertThat(binder.getBindingResult().getFieldError("startDate").getCode()).isEqualTo("typeMismatch");
    }
}
