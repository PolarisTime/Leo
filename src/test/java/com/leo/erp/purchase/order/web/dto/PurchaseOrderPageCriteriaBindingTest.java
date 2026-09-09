package com.leo.erp.purchase.order.web.dto;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.ServletRequestDataBinder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PurchaseOrderPageCriteria 绑定语义测试：参数名、可选性、日期格式。
 */
class PurchaseOrderPageCriteriaBindingTest {

    private DefaultFormattingConversionService conversionService;

    @BeforeEach
    void setUp() {
        conversionService = new DefaultFormattingConversionService();
    }

    @Test
    void bindsAllQueryParamsByName() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("keyword", "kw");
        request.setParameter("supplierId", "10");
        request.setParameter("supplierName", "供应商A");
        request.setParameter("settlementCompanyId", "30");
        request.setParameter("status", "DRAFT");
        request.setParameter("startDate", "2026-08-01");
        request.setParameter("endDate", "2026-08-31");
        request.setParameter("pendingOnly", "true");
        request.setParameter("referenced", "false");
        request.setParameter("referencedBy", "purchase-inbound");

        ServletRequestDataBinder binder = new ServletRequestDataBinder(new PurchaseOrderPageCriteria(), "criteria");
        binder.setConversionService(conversionService);
        binder.bind(request);
        assertThat(binder.getBindingResult().getAllErrors())
                .withFailMessage("绑定失败: " + binder.getBindingResult().getAllErrors())
                .isEmpty();

        PurchaseOrderPageCriteria criteria = (PurchaseOrderPageCriteria) binder.getTarget();
        assertThat(criteria.getKeyword()).isEqualTo("kw");
        assertThat(criteria.getSupplierId()).isEqualTo(10L);
        assertThat(criteria.getSupplierName()).isEqualTo("供应商A");
        assertThat(criteria.getSettlementCompanyId()).isEqualTo(30L);
        assertThat(criteria.getStatus()).isEqualTo("DRAFT");
        assertThat(criteria.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(criteria.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(criteria.getPendingOnly()).isTrue();
        assertThat(criteria.getReferenced()).isFalse();
        assertThat(criteria.getReferencedBy()).isEqualTo("purchase-inbound");
    }

    @Test
    void allFieldsOptional() {
        ServletRequestDataBinder binder = new ServletRequestDataBinder(new PurchaseOrderPageCriteria(), "criteria");
        binder.setConversionService(conversionService);
        binder.bind(new MockHttpServletRequest());

        assertThat(binder.getBindingResult().getAllErrors()).isEmpty();

        PurchaseOrderPageCriteria criteria = (PurchaseOrderPageCriteria) binder.getTarget();
        assertThat(criteria.getKeyword()).isNull();
        assertThat(criteria.getSupplierId()).isNull();
        assertThat(criteria.getSupplierName()).isNull();
        assertThat(criteria.getSettlementCompanyId()).isNull();
        assertThat(criteria.getStatus()).isNull();
        assertThat(criteria.getStartDate()).isNull();
        assertThat(criteria.getEndDate()).isNull();
        assertThat(criteria.getPendingOnly()).isNull();
        assertThat(criteria.getReferenced()).isNull();
        assertThat(criteria.getReferencedBy()).isNull();
    }

    @Test
    void isoLocalDateFormat() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("startDate", "2026-02-28");
        request.setParameter("endDate", "2026-11-30");

        ServletRequestDataBinder binder = new ServletRequestDataBinder(new PurchaseOrderPageCriteria(), "criteria");
        binder.setConversionService(conversionService);
        binder.bind(request);

        PurchaseOrderPageCriteria criteria = (PurchaseOrderPageCriteria) binder.getTarget();
        assertThat(criteria.getStartDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(criteria.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void nonIsoDateProducesTypeMismatch() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("endDate", "31-12-2026");

        ServletRequestDataBinder binder = new ServletRequestDataBinder(new PurchaseOrderPageCriteria(), "criteria");
        binder.setConversionService(conversionService);
        binder.bind(request);

        assertThat(binder.getBindingResult().getFieldErrorCount("endDate")).isEqualTo(1);
        assertThat(binder.getBindingResult().getFieldError("endDate").getCode()).isEqualTo("typeMismatch");
    }

    @Test
    void booleanBindingAcceptsTrueFalseStrings() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("pendingOnly", "true");
        request.setParameter("referenced", "TRUE");

        ServletRequestDataBinder binder = new ServletRequestDataBinder(new PurchaseOrderPageCriteria(), "criteria");
        binder.setConversionService(conversionService);
        binder.bind(request);

        PurchaseOrderPageCriteria criteria = (PurchaseOrderPageCriteria) binder.getTarget();
        assertThat(criteria.getPendingOnly()).isTrue();
        assertThat(criteria.getReferenced()).isTrue();
    }
}
