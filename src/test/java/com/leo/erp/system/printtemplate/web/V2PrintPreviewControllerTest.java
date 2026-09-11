package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.system.printtemplate.service.PrintRecordItem;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import com.leo.erp.system.printtemplate.web.dto.PrintItemRowResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * V2PrintPreviewController 只读分页打印预览端点测试。
 */
@ExtendWith(MockitoExtension.class)
class V2PrintPreviewControllerTest {

    @Mock
    private PrintScriptService printScriptService;

    @InjectMocks
    private V2PrintPreviewController controller;

    private PrintRecordItem item(String id) {
        return new PrintRecordItem(id, "10", "品牌A", "类目A", "材质A", "规格A", "1", "0.1", "0.1", "100", "100");
    }

    @Test
    void items_shouldReturnPagedRows() {
        PageQuery query = new PageQuery(0, 2, null, null);
        Page<PrintRecordItem> page = new PageImpl<>(List.of(item("1"), item("2")), PageRequest.of(0, 2), 2);
        when(printScriptService.pagePrintItems("sales-order", List.of(10L), query)).thenReturn(page);

        PageResponse<PrintItemRowResponse> result = controller.items(query, "sales-order", List.of(10L));

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).id()).isEqualTo("1");
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.currentPage()).isEqualTo(0);
        assertThat(result.pageSize()).isEqualTo(2);
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    void items_shouldReturnEmptyPageWhenNoRows() {
        PageQuery query = new PageQuery(0, 10, null, null);
        Page<PrintRecordItem> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(printScriptService.pagePrintItems("sales-order", List.of(10L), query)).thenReturn(page);

        PageResponse<PrintItemRowResponse> result = controller.items(query, "sales-order", List.of(10L));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.hasMore()).isFalse();
    }
}
