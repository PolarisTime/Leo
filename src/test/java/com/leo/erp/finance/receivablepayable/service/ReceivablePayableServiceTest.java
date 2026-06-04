package com.leo.erp.finance.receivablepayable.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.excel.service.ExcelExportService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.finance.receivablepayable.repository.ReceivablePayableQueryRepository;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReceivablePayableServiceTest {

    private static final String VALID_COMPOSITE_KEY = "应收:客户:abcdefabcdefabcdefabcdef12345678";

    @Test
    void shouldReturnPage_whenCallingPage() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.page(new PageQuery(0, 10, "id", "desc"), null, null, null, null)).thenReturn(org.springframework.data.domain.Page.empty());
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.page(new PageQuery(0, 10, "id", "desc"), null, null, null, null);
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnPage_whenFiltersProvided() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.page(new PageQuery(0, 10, "id", "desc"), "应收", "客户", "未结清", "test")).thenReturn(org.springframework.data.domain.Page.empty());
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.page(new PageQuery(0, 10, "id", "desc"), "应收", "客户", "未结清", "test");

        assertThat(result).isNotNull();
    }

    @Test
    void shouldThrowException_whenDirectionInvalid() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.page(new PageQuery(0, 10, "id", "desc"), "invalid", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("direction 不合法");
    }

    @Test
    void shouldThrowException_whenCounterpartyTypeInvalid() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.page(new PageQuery(0, 10, "id", "desc"), null, "invalid", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("counterpartyType 不合法");
    }

    @Test
    void shouldThrowException_whenStatusInvalid() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.page(new PageQuery(0, 10, "id", "desc"), null, null, "invalid", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("status 不合法");
    }

    @Test
    void shouldReturnDetail_whenValidCompositeKey() {
        var summary = buildResponse(VALID_COMPOSITE_KEY, "应收", "客户", "客户A");
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.findSummary(anyString(), anyString(), anyString())).thenReturn(summary);
        when(queryRepository.detailItems(anyString(), anyString(), anyString())).thenReturn(List.of());
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.detail(VALID_COMPOSITE_KEY);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(VALID_COMPOSITE_KEY);
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
    void shouldThrowException_whenDetailIdDirectionMismatch() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.detail("应付:客户:abcdefabcdefabcdefabcdef12345678"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID方向不合法");
    }

    @Test
    void shouldThrowException_whenDetailSummaryNotFound() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.findSummary(anyString(), anyString(), anyString())).thenReturn(null);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.detail(VALID_COMPOSITE_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总不存在");
    }

    @Test
    void shouldExportExcel_whenValidParams() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.listForExport(anyString(), anyString(), anyString(), anyString())).thenReturn(List.of(
                buildResponse("id1", "应收", "客户", "客户A")
        ));
        var excelExportService = mock(ExcelExportService.class);
        when(excelExportService.export(anyList(), ArgumentMatchers.any(Class.class))).thenReturn(new byte[0]);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.exportExcel(null, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.filename()).contains("应收应付汇总");
    }

    @Test
    void shouldThrowException_whenExportWithInvalidDirection() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.exportExcel("invalid", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("direction 不合法");
    }

    @Test
    void shouldThrowException_whenExportWithInvalidCounterpartyType() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.exportExcel(null, "invalid", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("counterpartyType 不合法");
    }

    @Test
    void shouldThrowException_whenExportWithInvalidStatus() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.exportExcel(null, null, "invalid", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("status 不合法");
    }

    @Test
    void shouldThrowException_whenDetailIdKeyNotHex() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.detail("应收:客户:not-a-valid-hex-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID不合法");
    }

    @Test
    void shouldThrowException_whenDetailIdSupplierDirectionMismatch() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.detail("应收:供应商:abcdefabcdefabcdefabcdef12345678"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID方向不合法");
    }

    @Test
    void shouldThrowException_whenDetailIdFreightDirectionMismatch() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        assertThatThrownBy(() -> service.detail("应收:物流商:abcdefabcdefabcdefabcdef12345678"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID方向不合法");
    }

    @Test
    void shouldReturnDetail_whenSupplierCompositeKeyValid() {
        var supplierKey = "应付:供应商:abcdefabcdefabcdefabcdef12345678";
        var summary = buildResponse(supplierKey, "应付", "供应商", "供应商A");
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.findSummary(anyString(), anyString(), anyString())).thenReturn(summary);
        when(queryRepository.detailItems(anyString(), anyString(), anyString())).thenReturn(List.of());
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.detail(supplierKey);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(supplierKey);
    }

    @Test
    void shouldReturnDetail_whenFreightCompositeKeyValid() {
        var freightKey = "应付:物流商:abcdefabcdefabcdefabcdef12345678";
        var summary = buildResponse(freightKey, "应付", "物流商", "物流商A");
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.findSummary(anyString(), anyString(), anyString())).thenReturn(summary);
        when(queryRepository.detailItems(anyString(), anyString(), anyString())).thenReturn(List.of());
        var excelExportService = mock(ExcelExportService.class);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.detail(freightKey);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(freightKey);
    }

    @Test
    void shouldExportExcel_whenFiltersProvided() {
        var queryRepository = mock(ReceivablePayableQueryRepository.class);
        when(queryRepository.listForExport(anyString(), anyString(), anyString(), anyString())).thenReturn(List.of(
                buildResponse("id1", "应收", "客户", "客户A")
        ));
        var excelExportService = mock(ExcelExportService.class);
        when(excelExportService.export(anyList(), ArgumentMatchers.any(Class.class))).thenReturn(new byte[0]);
        var service = new ReceivablePayableService(queryRepository, excelExportService);

        var result = service.exportExcel("应收", "客户", "未结清", "test");

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
                counterpartyName,
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
