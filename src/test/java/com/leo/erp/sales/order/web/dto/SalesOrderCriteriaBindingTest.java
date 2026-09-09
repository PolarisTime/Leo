package com.leo.erp.sales.order.web.dto;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.format.support.DefaultFormattingConversionService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * criteria 绑定语义测试：参数名、可选性、日期格式。
 */
class SalesOrderCriteriaBindingTest {

    private DefaultFormattingConversionService conversionService;

    @BeforeEach
    void setUp() {
        conversionService = new DefaultFormattingConversionService();
    }

    private <T> T bind(T criteria, MockHttpServletRequest request) {
        ServletRequestDataBinder binder = new ServletRequestDataBinder(criteria, "criteria");
        binder.setConversionService(conversionService);
        binder.bind(request);
        assertThat(binder.getBindingResult().getAllErrors())
                .withFailMessage("绑定失败: " + binder.getBindingResult().getAllErrors())
                .isEmpty();
        return criteria;
    }

    @Test
    void salesOrderPageCriteria_bindsAllQueryParamsByName() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("keyword", "kw");
        request.setParameter("customerId", "10");
        request.setParameter("customerName", "客户A");
        request.setParameter("projectId", "20");
        request.setParameter("projectName", "项目A");
        request.setParameter("settlementCompanyId", "30");
        request.setParameter("productKeyword", "M001");
        request.setParameter("status", "DRAFT");
        request.setParameter("startDate", "2026-08-01");
        request.setParameter("endDate", "2026-08-31");
        request.setParameter("pendingOnly", "true");
        request.setParameter("referenced", "false");
        request.setParameter("referencedBy", "sales-outbound");

        SalesOrderPageCriteria criteria = bind(new SalesOrderPageCriteria(), request);

        assertThat(criteria.getKeyword()).isEqualTo("kw");
        assertThat(criteria.getCustomerId()).isEqualTo(10L);
        assertThat(criteria.getCustomerName()).isEqualTo("客户A");
        assertThat(criteria.getProjectId()).isEqualTo(20L);
        assertThat(criteria.getProjectName()).isEqualTo("项目A");
        assertThat(criteria.getSettlementCompanyId()).isEqualTo(30L);
        assertThat(criteria.getProductKeyword()).isEqualTo("M001");
        assertThat(criteria.getStatus()).isEqualTo("DRAFT");
        assertThat(criteria.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(criteria.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(criteria.getPendingOnly()).isTrue();
        assertThat(criteria.getReferenced()).isFalse();
        assertThat(criteria.getReferencedBy()).isEqualTo("sales-outbound");
    }

    @Test
    void salesOrderPageCriteria_allFieldsOptional() {
        SalesOrderPageCriteria criteria = bind(new SalesOrderPageCriteria(), new MockHttpServletRequest());

        assertThat(criteria.getKeyword()).isNull();
        assertThat(criteria.getCustomerId()).isNull();
        assertThat(criteria.getCustomerName()).isNull();
        assertThat(criteria.getProjectId()).isNull();
        assertThat(criteria.getProjectName()).isNull();
        assertThat(criteria.getSettlementCompanyId()).isNull();
        assertThat(criteria.getProductKeyword()).isNull();
        assertThat(criteria.getStatus()).isNull();
        assertThat(criteria.getStartDate()).isNull();
        assertThat(criteria.getEndDate()).isNull();
        assertThat(criteria.getPendingOnly()).isNull();
        assertThat(criteria.getReferenced()).isNull();
        assertThat(criteria.getReferencedBy()).isNull();
    }

    @Test
    void salesOrderPageCriteria_isoLocalDateFormat() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("startDate", "2026-01-31");
        request.setParameter("endDate", "2026-12-01");

        SalesOrderPageCriteria criteria = bind(new SalesOrderPageCriteria(), request);

        assertThat(criteria.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(criteria.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void salesOrderPageCriteria_nonIsoDateProducesTypeMismatch() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("startDate", "2026/08/01");

        ServletRequestDataBinder binder = new ServletRequestDataBinder(new SalesOrderPageCriteria(), "criteria");
        binder.setConversionService(conversionService);
        binder.bind(request);

        assertThat(binder.getBindingResult().getFieldErrorCount("startDate")).isEqualTo(1);
        assertThat(binder.getBindingResult().getFieldError("startDate").getCode()).isEqualTo("typeMismatch");
    }

    @Test
    void salesOrderPageCriteria_nonNumericIdProducesTypeMismatch() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("customerId", "abc");

        ServletRequestDataBinder binder = new ServletRequestDataBinder(new SalesOrderPageCriteria(), "criteria");
        binder.setConversionService(conversionService);
        binder.bind(request);

        assertThat(binder.getBindingResult().getFieldErrorCount("customerId")).isEqualTo(1);
        assertThat(binder.getBindingResult().getFieldError("customerId").getCode()).isEqualTo("typeMismatch");
    }

    @Test
    void salesOrderOutboundImportCandidatesCriteria_bindsAllQueryParamsByName() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("keyword", "kw");
        request.setParameter("customerId", "10");
        request.setParameter("customerName", "客户A");
        request.setParameter("projectId", "20");
        request.setParameter("projectName", "项目A");
        request.setParameter("settlementCompanyId", "30");
        request.setParameter("status", "DRAFT");
        request.setParameter("startDate", "2026-08-01");
        request.setParameter("endDate", "2026-08-31");
        request.setParameter("currentRecordId", "5");

        SalesOrderOutboundImportCandidatesCriteria criteria = bind(new SalesOrderOutboundImportCandidatesCriteria(), request);

        assertThat(criteria.getKeyword()).isEqualTo("kw");
        assertThat(criteria.getCustomerId()).isEqualTo(10L);
        assertThat(criteria.getCustomerName()).isEqualTo("客户A");
        assertThat(criteria.getProjectId()).isEqualTo(20L);
        assertThat(criteria.getProjectName()).isEqualTo("项目A");
        assertThat(criteria.getSettlementCompanyId()).isEqualTo(30L);
        assertThat(criteria.getStatus()).isEqualTo("DRAFT");
        assertThat(criteria.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(criteria.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(criteria.getCurrentRecordId()).isEqualTo(5L);
    }

    @Test
    void salesOrderOutboundImportCandidatesCriteria_allFieldsOptional() {
        SalesOrderOutboundImportCandidatesCriteria criteria =
                bind(new SalesOrderOutboundImportCandidatesCriteria(), new MockHttpServletRequest());

        assertThat(criteria.getKeyword()).isNull();
        assertThat(criteria.getCustomerId()).isNull();
        assertThat(criteria.getCustomerName()).isNull();
        assertThat(criteria.getProjectId()).isNull();
        assertThat(criteria.getProjectName()).isNull();
        assertThat(criteria.getSettlementCompanyId()).isNull();
        assertThat(criteria.getStatus()).isNull();
        assertThat(criteria.getStartDate()).isNull();
        assertThat(criteria.getEndDate()).isNull();
        assertThat(criteria.getCurrentRecordId()).isNull();
    }

    @Test
    void criteria_survivesUnknownQueryParams() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("keyword", "kw");
        request.setParameter("unknownParam", "noise");
        request.setParameter("class", "java.lang.Runtime");

        SalesOrderPageCriteria criteria = bind(new SalesOrderPageCriteria(), request);

        assertThat(criteria.getKeyword()).isEqualTo("kw");
    }

    @Test
    void criteria_acceptsMutablePropertyValuesBinding() {
        MutablePropertyValues values = new MutablePropertyValues();
        values.add("status", "DRAFT");

        DataBinder binder = new DataBinder(new SalesOrderPageCriteria(), "criteria");
        binder.setConversionService(conversionService);
        binder.bind(values);

        assertThat(((SalesOrderPageCriteria) binder.getTarget()).getStatus()).isEqualTo("DRAFT");
    }
}
