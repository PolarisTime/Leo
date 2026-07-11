package com.leo.erp.finance.receivablepayable.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.excel.service.ExcelExportService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.finance.receivablepayable.repository.ReceivablePayableQueryRepository;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableDetailItemResponse;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceivablePayableServiceTest {

    private static final String VALID_COMPOSITE_KEY = "应收:客户:已对账:1001:abcdefabcdefabcdefabcdef12345678";

    @Test
    void shouldReturnPage_whenCallingPage() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.page(new PageQuery(0, 10, "id", "desc"), null, null, null, null, null, null))
                .thenReturn(org.springframework.data.domain.Page.empty());
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.page(new PageQuery(0, 10, "id", "desc"), null, null, null, null, null);
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnPage_whenFiltersProvided() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.page(
                new PageQuery(0, 10, "id", "desc"),
                "应收",
                "客户",
                null,
                "已对账",
                "未结清",
                "test"
        ))
                .thenReturn(org.springframework.data.domain.Page.empty());
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.page(new PageQuery(0, 10, "id", "desc"), "应收", "客户", "已对账", "未结清", "test");

        assertThat(result).isNotNull();
    }

    @Test
    void shouldPassSettlementCompanyFilterToRepository() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.page(any(), eq("应付"), eq("供应商"), eq(1001L), eq("未对账"), eq("未结清"), eq("SUP001")))
                .thenReturn(org.springframework.data.domain.Page.empty());
        var service = new ReceivablePayableService(queryRepository, mock(ExcelExportService.class));

        service.page(new PageQuery(0, 10, "id", "desc"), "应付", "供应商", 1001L, "未对账", "未结清", "SUP001");

        verify(queryRepository).page(any(), eq("应付"), eq("供应商"), eq(1001L), eq("未对账"), eq("未结清"), eq("SUP001"));
    }

    @Test
    void shouldTrimBlankFiltersToNull() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.page(any(), eq(null), eq(null), eq(null), eq(null), eq(null), eq("keyword")))
                .thenReturn(org.springframework.data.domain.Page.empty());
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.page(new PageQuery(0, 10, "id", "desc"), " ", " ", " ", " ", "keyword");

        assertThat(result).isNotNull();
        verify(queryRepository).page(any(), eq(null), eq(null), eq(null), eq(null), eq(null), eq("keyword"));
    }

    @Test
    void shouldThrowException_whenDirectionInvalid() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.page(new PageQuery(0, 10, "id", "desc"), "invalid", null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("direction 不合法");
    }

    @Test
    void shouldThrowException_whenCounterpartyTypeInvalid() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.page(new PageQuery(0, 10, "id", "desc"), null, "invalid", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("counterpartyType 不合法");
    }

    @Test
    void shouldThrowException_whenReconciliationStatusInvalid() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.page(new PageQuery(0, 10, "id", "desc"), null, null, "invalid", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reconciliationStatus 不合法");
    }

    @Test
    void shouldThrowException_whenStatusInvalid() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.page(new PageQuery(0, 10, "id", "desc"), null, null, null, "invalid", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("status 不合法");
    }

    @Test
    void shouldReturnDetail_whenValidCompositeKey() {
        var summary = buildResponse(VALID_COMPOSITE_KEY, "应收", "客户", "客户A");
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.findSummary(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(summary);
        when(queryRepository.detailItems(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(List.of());
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.detail(VALID_COMPOSITE_KEY);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(VALID_COMPOSITE_KEY);
        assertThat(result.settlementCompanyId()).isEqualTo(1001L);
        assertThat(result.settlementCompanyName()).isEqualTo("结算主体甲");
    }

    @Test
    void shouldReturnDetailWithSafeAmounts_whenSummaryAmountsArePresent() {
        var summary = buildResponse(VALID_COMPOSITE_KEY, "应收", "客户", "客户A");
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.findSummary(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(summary);
        when(queryRepository.detailItems(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(List.of());
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.detail(VALID_COMPOSITE_KEY);

        assertThat(result.recognizedAmount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(result.settledAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.balanceAmount()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void shouldReturnSameDetailWhenCalledRepeatedly() {
        var summary = buildResponse(VALID_COMPOSITE_KEY, "应收", "客户", "客户A");
        var item = new ReceivablePayableDetailItemResponse(
                "item-1",
                "RECOGNITION",
                "销售订单",
                100L,
                "SO-001",
                "SO-001-1",
                "项目A",
                "已对账",
                null,
                null,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                0,
                "完成销售",
                null
        );
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.findSummary(eq("应收"), eq("客户"), eq("abcdefabcdefabcdefabcdef12345678"), eq("1001"), eq("已对账")))
                .thenReturn(summary);
        when(queryRepository.detailItems(eq("应收"), eq("客户"), eq("abcdefabcdefabcdefabcdef12345678"), eq("1001"), eq("已对账")))
                .thenReturn(List.of(item));
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var first = service.detail(VALID_COMPOSITE_KEY);
        var second = service.detail(VALID_COMPOSITE_KEY);

        assertThat(second).isEqualTo(first);
        assertThat(second.items()).containsExactly(item);
        verify(queryRepository, times(2))
                .findSummary("应收", "客户", "abcdefabcdefabcdefabcdef12345678", "1001", "已对账");
        verify(queryRepository, times(2))
                .detailItems("应收", "客户", "abcdefabcdefabcdefabcdef12345678", "1001", "已对账");
    }

    @Test
    void shouldThrowException_whenDetailIdNull() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.detail(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID不能为空");
    }

    @Test
    void shouldThrowException_whenDetailIdBlank() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.detail(""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID不能为空");
    }

    @Test
    void shouldThrowException_whenDetailIdInvalidFormat() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.detail("invalid"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID不合法");
    }

    @Test
    void shouldThrowException_whenDetailIdIncompleteParts() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.detail("应收:客户"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID不合法");
    }

    @Test
    void shouldThrowException_whenDetailIdHasBlankParts() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.detail("应收:客户:已对账:none: "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID不合法");
        assertThatThrownBy(() -> service.detail("应收:客户:已对账: :CUS001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID不合法");
        assertThatThrownBy(() -> service.detail(" :客户:已对账:none:CUS001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID不合法");
        assertThatThrownBy(() -> service.detail("应收: :已对账:none:CUS001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID不合法");
        assertThatThrownBy(() -> service.detail("应收:客户: :none:CUS001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID不合法");
    }

    @Test
    void shouldRejectNullCounterpartyKey() throws Exception {
        var service = new ReceivablePayableService(mock(ReceivablePayableQueryRepository.class),
                mock(ExcelExportService.class));
        Method method = ReceivablePayableService.class.getDeclaredMethod("isValidCounterpartyKey", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, new Object[]{null})).isEqualTo(false);
    }

    @Test
    void shouldThrowException_whenDetailIdDirectionMismatch() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.detail("应付:客户:已对账:none:abcdefabcdefabcdefabcdef12345678"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID方向不合法");
    }

    @Test
    void shouldThrowException_whenDetailSummaryNotFound() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.findSummary(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.detail(VALID_COMPOSITE_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总不存在");
    }

    @Test
    void shouldExportExcel_whenValidParams() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.listForExport(any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                buildResponse("id1", "应收", "客户", "客户A")
        ));
        var excelExportService = mock(ExcelExportService.class);
        when(excelExportService.export(anyList(), ArgumentMatchers.any(Class.class))).thenReturn(new byte[0]);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.exportExcel(null, null, null, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.filename()).contains("应收应付汇总");
    }

    @Test
    void shouldThrowException_whenExportWithInvalidDirection() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.exportExcel("invalid", null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("direction 不合法");
    }

    @Test
    void shouldThrowException_whenExportWithInvalidCounterpartyType() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.exportExcel(null, "invalid", null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("counterpartyType 不合法");
    }

    @Test
    void shouldThrowException_whenExportWithInvalidReconciliationStatus() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.exportExcel(null, null, null, "invalid", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reconciliationStatus 不合法");
    }

    @Test
    void shouldThrowException_whenExportWithInvalidStatus() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.exportExcel(null, null, null, null, "invalid", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("status 不合法");
    }

    @Test
    void shouldThrowException_whenDetailIdKeyNotHex() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.detail("应收:客户:已对账:none:bad key!"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID不合法");
    }

    @Test
    void shouldThrowException_whenSettlementCompanyKeyInvalid() {
        var service = new ReceivablePayableService(
                mock(ReceivablePayableQueryRepository.class),
                mock(ExcelExportService.class)
        );

        assertThatThrownBy(() -> service.detail("应付:供应商:未对账:company-x:SUP001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID不合法");
    }

    @Test
    void shouldReturnDetail_whenCounterpartyCodeKeyValid() {
        var codeKey = "应收:客户:未对账:none:CUS001";
        var summary = buildResponse(codeKey, "应收", "客户", "客户A");
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.findSummary(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(summary);
        when(queryRepository.detailItems(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(List.of());
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.detail(codeKey);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(codeKey);
    }

    @Test
    void shouldReturnDetail_whenLegacyNameHashKeyValid() {
        var nameHashKey = "应收:客户:未对账:none:name:abcdefabcdefabcdefabcdef12345678";
        var summary = buildResponse(nameHashKey, "应收", "客户", "客户A");
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.findSummary(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(summary);
        when(queryRepository.detailItems(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(List.of());
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.detail(nameHashKey);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(nameHashKey);
    }

    @Test
    void shouldNormalizeNameHashKeyToLowerCase() {
        var nameHashKey = "应收:客户:未对账:none:name:ABCDEFABCDEFABCDEFABCDEF12345678";
        var summary = buildResponse(nameHashKey, "应收", "客户", "客户A");
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.findSummary(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(summary);
        when(queryRepository.detailItems(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(List.of());
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        service.detail(nameHashKey);

        verify(queryRepository).findSummary("应收", "客户", "name:abcdefabcdefabcdefabcdef12345678", "none", "未对账");
    }

    @Test
    void shouldNormalizeSafeNullAmountToZero() throws Exception {
        var service = new ReceivablePayableService(mock(ReceivablePayableQueryRepository.class),
                mock(ExcelExportService.class));
        Method method = ReceivablePayableService.class.getDeclaredMethod("safe", BigDecimal.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, new Object[]{null})).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void shouldThrowException_whenDetailIdSupplierDirectionMismatch() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.detail("应收:供应商:已对账:none:abcdefabcdefabcdefabcdef12345678"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID方向不合法");
    }

    @Test
    void shouldThrowException_whenDetailIdFreightDirectionMismatch() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.detail("应收:物流商:已对账:none:abcdefabcdefabcdefabcdef12345678"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID方向不合法");
    }

    @Test
    void shouldReturnDetail_whenSupplierCompositeKeyValid() {
        var supplierKey = "应付:供应商:已对账:1001:abcdefabcdefabcdefabcdef12345678";
        var summary = buildResponse(supplierKey, "应付", "供应商", "供应商A");
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.findSummary(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(summary);
        when(queryRepository.detailItems(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(List.of());
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.detail(supplierKey);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(supplierKey);
    }

    @Test
    void shouldReturnDetail_whenFreightCompositeKeyValid() {
        var freightKey = "应付:物流商:已对账:none:abcdefabcdefabcdefabcdef12345678";
        var summary = buildResponse(freightKey, "应付", "物流商", "物流商A");
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.findSummary(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(summary);
        when(queryRepository.detailItems(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(List.of());
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.detail(freightKey);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(freightKey);
    }

    @Test
    void shouldExportExcel_whenFiltersProvided() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.listForExport(any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                buildResponse("id1", "应收", "客户", "客户A")
        ));
        var excelExportService = mock(ExcelExportService.class);
        when(excelExportService.export(anyList(), ArgumentMatchers.any(Class.class))).thenReturn(new byte[0]);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.exportExcel("应收", "客户", 1001L, "已对账", "未结清", "test");

        assertThat(result).isNotNull();
        assertThat(result.filename()).contains("应收应付汇总");
    }

    private ReceivablePayableResponse buildResponse(String id,
                                                    String direction,
                                                    String counterpartyType,
                                                    String counterpartyName) {
        return new ReceivablePayableResponse(
                id,
                direction,
                counterpartyType,
                "CP-001",
                counterpartyName,
                1001L,
                "结算主体甲",
                "已对账",
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1L,
                "未结清",
                null
        );
    }
}
