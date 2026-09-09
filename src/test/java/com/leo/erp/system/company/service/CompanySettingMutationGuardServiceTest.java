package com.leo.erp.system.company.service;

import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompanySettingMutationGuardServiceTest {

    @Mock
    private MasterDataReferenceGuard referenceGuard;

    @Test
    void assertDeletable_shouldBeNoOpWhenGuardMissing() {
        CompanySettingMutationGuardService service = new CompanySettingMutationGuardService(null);
        CompanySetting entity = company(1L);

        assertThatCode(() -> service.assertDeletable(entity))
                .doesNotThrowAnyException();
    }

    @Test
    void assertDeletable_shouldCheckAllReferenceTables() {
        CompanySettingMutationGuardService service = new CompanySettingMutationGuardService(referenceGuard);
        CompanySetting entity = company(1L);

        service.assertDeletable(entity);

        ArgumentCaptor<List<MasterDataReferenceGuard.ReferenceCheck>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(referenceGuard).assertNoReferences(org.mockito.ArgumentMatchers.eq("该结算主体"), captor.capture());
        List<String> tables = captor.getValue().stream()
                .map(MasterDataReferenceGuard.ReferenceCheck::tableName)
                .toList();
        assertThat(tables).contains(
                "md_carrier",
                "md_customer",
                "md_project",
                "po_purchase_order",
                "so_sales_order",
                "lg_freight_bill",
                "st_customer_statement",
                "fm_payment",
                "sys_print_template"
        );
    }

    @Test
    void assertDeletable_shouldPropagateGuardException() {
        CompanySettingMutationGuardService service = new CompanySettingMutationGuardService(referenceGuard);
        doThrow(new RuntimeException("存在引用"))
                .when(referenceGuard).assertNoReferences(anyString(), anyList());

        assertThatCode(() -> service.assertDeletable(company(1L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("存在引用");
    }

    @Test
    void assertDeletable_shouldNotBypassGuardWhenEntityIdNull() {
        CompanySettingMutationGuardService service = new CompanySettingMutationGuardService(referenceGuard);

        service.assertDeletable(company(null));

        verify(referenceGuard).assertNoReferences(anyString(), any());
    }

    @Test
    void assertDeletable_shouldNeverInvokeWhenGuardMissingEvenIfEntityIdNull() {
        CompanySettingMutationGuardService service = new CompanySettingMutationGuardService(null);

        assertThatCode(() -> service.assertDeletable(company(null)))
                .doesNotThrowAnyException();

        verify(referenceGuard, never()).assertNoReferences(anyString(), anyList());
    }

    private CompanySetting company(Long id) {
        CompanySetting entity = new CompanySetting();
        entity.setId(id);
        entity.setCompanyName("公司A");
        return entity;
    }
}
