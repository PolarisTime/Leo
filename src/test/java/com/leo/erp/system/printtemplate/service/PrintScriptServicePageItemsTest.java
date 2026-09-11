package com.leo.erp.system.printtemplate.service;

import com.leo.erp.attachment.api.AttachmentRecordAccess;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * PrintScriptService.pagePrintItems 分页与边界测试。
 */
@ExtendWith(MockitoExtension.class)
class PrintScriptServicePageItemsTest {

    @Mock
    private PrintTemplateRepository templateRepository;

    @Mock
    private PrintRecordDataProvider dataProvider;

    @Mock
    private PrintRecordEnricher recordEnricher;

    @Mock
    private PrintRecordLayoutPreparer layoutPreparer;

    @Mock
    private PrintLayoutLodopRenderer layoutLodopRenderer;

    @Mock
    private AttachmentRecordAccess recordAccessService;

    @Mock
    private PrintRuntimeProperties runtimeProperties;

    @InjectMocks
    private PrintScriptService service;

    private PrintRecordItem item(String id, String customerName) {
        return new PrintRecordItem(
                id, "10", "品牌A", "类目A", "", "材质A", "规格A", "", "1", "0.1", "0.1", "100", "100",
                "", "", "", customerName, "", "", "", "", ""
        );
    }

    @Test
    void pagePrintItems_shouldSliceInMemory() {
        when(dataProvider.listPrintItems("sales-order", List.of(1L)))
                .thenReturn(List.of(item("1", "甲"), item("2", "乙"), item("3", "丙")));

        Page<PrintRecordItem> page = service.pagePrintItems(
                "sales-order", List.of(1L), new PageQuery(1, 2, null, null));

        assertThat(page.getContent()).extracting(PrintRecordItem::id).containsExactly("3");
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void pagePrintItems_shouldReturnEmptyWhenPageOutOfRange() {
        when(dataProvider.listPrintItems("sales-order", List.of(1L)))
                .thenReturn(List.of(item("1", "甲")));

        Page<PrintRecordItem> page = service.pagePrintItems(
                "sales-order", List.of(1L), new PageQuery(5, 10, null, null));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void pagePrintItems_shouldSortByWhitelistedField() {
        when(dataProvider.listPrintItems("sales-order", List.of(1L)))
                .thenReturn(List.of(item("1", "c"), item("2", "a"), item("3", "b")));

        Page<PrintRecordItem> page = service.pagePrintItems(
                "sales-order", List.of(1L), new PageQuery(0, 10, "customerName", "asc"));

        assertThat(page.getContent()).extracting(PrintRecordItem::customerName)
                .containsExactly("a", "b", "c");
    }

    @Test
    void pagePrintItems_shouldRejectUnknownSortField() {
        when(dataProvider.listPrintItems("sales-order", List.of(1L)))
                .thenReturn(List.of(item("1", "甲")));

        assertThatThrownBy(() -> service.pagePrintItems(
                "sales-order", List.of(1L), new PageQuery(0, 10, "unknownField", "asc")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持按字段排序");
    }
}
