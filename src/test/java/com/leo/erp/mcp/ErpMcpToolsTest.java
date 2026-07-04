package com.leo.erp.mcp;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import com.leo.erp.master.carrier.service.CarrierService;
import com.leo.erp.master.customer.service.CustomerService;
import com.leo.erp.master.material.service.MaterialCategoryService;
import com.leo.erp.master.material.service.MaterialService;
import com.leo.erp.master.project.service.ProjectService;
import com.leo.erp.master.supplier.service.SupplierService;
import com.leo.erp.master.warehouse.service.WarehouseService;
import com.leo.erp.purchase.inbound.service.PurchaseInboundService;
import com.leo.erp.purchase.order.service.PurchaseOrderService;
import com.leo.erp.report.inventory.service.InventoryReportService;
import com.leo.erp.sales.order.service.SalesOrderService;
import com.leo.erp.sales.outbound.service.SalesOutboundService;
import com.leo.erp.search.service.GlobalSearchService;
import com.leo.erp.search.web.GlobalSearchResponse;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErpMcpToolsTest {

    @Test
    void shouldNotCreateToolsWhenMcpServerDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.ai.mcp.server.enabled=false")
                .withBean(ErpMcpQueryFacade.class, () -> mock(ErpMcpQueryFacade.class))
                .withUserConfiguration(McpToolsConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(ErpMcpTools.class));
    }

    @Test
    void shouldCreateToolsWhenMcpServerEnabled() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.ai.mcp.server.enabled=true")
                .withBean(ErpMcpQueryFacade.class, () -> mock(ErpMcpQueryFacade.class))
                .withUserConfiguration(McpToolsConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(ErpMcpTools.class));
    }

    @Test
    void shouldDelegateToolCallsToQueryFacade() {
        ErpMcpQueryFacade facade = mock(ErpMcpQueryFacade.class);
        ErpMcpTools tools = new ErpMcpTools(facade);
        List<GlobalSearchResponse> searchResponses = List.of(
                new GlobalSearchResponse("sales-order", "销售单", "SO-001", "SO-001", "测试客户", false)
        );
        PageResponse<Object> pageResponse = new PageResponse<>(List.of("row"), 1, 1, 0, 20, false);
        Map<String, Object> record = Map.of("id", 1L);
        Map<String, Object> options = Map.of("content", List.of("客户A"));
        Map<String, Object> preview = Map.of("businessNo", "SO-001");
        when(facade.globalSearch("SO", List.of("sales-order"), 10)).thenReturn(searchResponses);
        doReturn(pageResponse).when(facade).queryRecords("sales-order", "SO", "已审核", 0, 20);
        when(facade.getRecord("sales-order", 1L)).thenReturn(record);
        when(facade.listOptions("customer", "客户", 20)).thenReturn(options);
        when(facade.printPayloadPreview("sales-order", 1L, "tpl")).thenReturn(preview);

        assertThat(tools.globalSearch("SO", List.of("sales-order"), 10)).isSameAs(searchResponses);
        assertThat(tools.queryRecords("sales-order", "SO", "已审核", 0, 20)).isSameAs(pageResponse);
        assertThat(tools.getRecord("sales-order", 1L)).isSameAs(record);
        assertThat(tools.listOptions("customer", "客户", 20)).isSameAs(options);
        assertThat(tools.printPayloadPreview("sales-order", 1L, "tpl")).isSameAs(preview);
        verify(facade).globalSearch("SO", List.of("sales-order"), 10);
        verify(facade).queryRecords("sales-order", "SO", "已审核", 0, 20);
        verify(facade).getRecord("sales-order", 1L);
        verify(facade).listOptions("customer", "客户", 20);
        verify(facade).printPayloadPreview("sales-order", 1L, "tpl");
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ErpMcpTools.class)
    static class McpToolsConfiguration {
    }

    @Test
    void shouldRejectUnknownQueryModule() {
        ErpMcpQueryFacade facade = facadeWith(mock(ErpMcpPermissionExecutor.class), mock(MaterialService.class));

        assertThatThrownBy(() -> facade.queryRecords("database", null, null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MCP 不支持查询该模块");
    }

    @Test
    void shouldLimitQueryPageSize() {
        ErpMcpPermissionExecutor permissionExecutor = mock(ErpMcpPermissionExecutor.class);
        MaterialService materialService = mock(MaterialService.class);
        AtomicReference<PageQuery> queryRef = new AtomicReference<>();
        when(permissionExecutor.read(any(), any())).thenAnswer(invocation -> invocation.getArgument(1, java.util.function.Supplier.class).get());
        when(materialService.page(any(PageQuery.class), isNull(), isNull(), isNull())).thenAnswer(invocation -> {
            PageQuery query = invocation.getArgument(0);
            queryRef.set(query);
            return new PageImpl<>(List.of(), PageRequest.of(query.page(), query.size()), 0);
        });

        PageResponse<?> response = facadeWith(permissionExecutor, materialService)
                .queryRecords("material", null, null, 0, 500);

        assertThat(response.pageSize()).isEqualTo(50);
        assertThat(queryRef.get().size()).isEqualTo(50);
    }

    @Test
    void shouldRejectTooLargeQueryPageOffset() {
        ErpMcpPermissionExecutor permissionExecutor = mock(ErpMcpPermissionExecutor.class);
        MaterialService materialService = mock(MaterialService.class);

        ErpMcpQueryFacade facade = facadeWith(permissionExecutor, materialService);

        assertThatThrownBy(() -> facade.queryRecords("material", null, null, 101, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("page 不能超过");
    }

    @Test
    void shouldLimitOptionsAndApplyKeywordThroughScopedPageReader() {
        ErpMcpPermissionExecutor permissionExecutor = mock(ErpMcpPermissionExecutor.class);
        CustomerService customerService = mock(CustomerService.class);
        AtomicReference<PageQuery> queryRef = new AtomicReference<>();
        AtomicReference<String> keywordRef = new AtomicReference<>();
        when(permissionExecutor.read(any(), any())).thenAnswer(invocation -> invocation.getArgument(1, java.util.function.Supplier.class).get());
        when(customerService.page(any(PageQuery.class), any(), eq("正常"))).thenAnswer(invocation -> {
            PageQuery query = invocation.getArgument(0);
            queryRef.set(query);
            keywordRef.set(invocation.getArgument(1));
            return new PageImpl<>(
                    List.of(customerResponse(1L, "华东客户"), customerResponse(2L, "华北客户")),
                    PageRequest.of(query.page(), query.size()),
                    2
            );
        });

        Object response = facadeWith(permissionExecutor, mock(MaterialService.class), customerService)
                .listOptions("customer", "华东", 500);

        assertThat(response).isInstanceOf(PageResponse.class);
        assertThat(((PageResponse<?>) response).content()).hasSize(2);
        assertThat(queryRef.get().page()).isZero();
        assertThat(queryRef.get().size()).isEqualTo(50);
        assertThat(keywordRef.get()).isEqualTo("华东");
        verify(permissionExecutor).read(eq("customer"), any());
    }

    @Test
    void shouldUsePrintPermissionAndMinimizePrintPayloadPreview() {
        ErpMcpPermissionExecutor permissionExecutor = mock(ErpMcpPermissionExecutor.class);
        PrintScriptService printScriptService = mock(PrintScriptService.class);
        when(permissionExecutor.print(any(), any())).thenAnswer(invocation -> invocation.getArgument(1, java.util.function.Supplier.class).get());
        Map<String, Object> rawPayload = new LinkedHashMap<>();
        rawPayload.put("templateName", "销售单模板");
        rawPayload.put("templateHtml", "<html>large</html>");
        rawPayload.put("templateType", "COORD");
        rawPayload.put("businessNo", "SO-001");
        rawPayload.put("recordId", 1L);
        rawPayload.put("moduleKey", "sales-order");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customerName", "测试客户");
        data.put("unitPrice", "100");
        rawPayload.put("data", data);
        rawPayload.put("items", List.of(Map.of("id", "11", "unitPrice", "100")));
        when(printScriptService.generateFromRecord(eq("1"), eq("sales-order"), eq(1L), any()))
                .thenReturn(rawPayload);

        Map<String, Object> preview = facadeWith(
                permissionExecutor,
                mock(MaterialService.class),
                mock(CustomerService.class),
                printScriptService
        ).printPayloadPreview("sales-order", 1L, "1");

        assertThat(preview)
                .containsEntry("templateName", "销售单模板")
                .containsEntry("businessNo", "SO-001")
                .containsEntry("recordId", 1L)
                .containsEntry("moduleKey", "sales-order");
        assertThat(preview).doesNotContainKeys("templateHtml", "data", "items");
        assertThat(preview.get("dataKeys")).isEqualTo(List.of("customerName", "unitPrice"));
        assertThat(preview.get("itemCount")).isEqualTo(1);
        verify(permissionExecutor).print(eq("sales-order"), any());
    }

    private ErpMcpQueryFacade facadeWith(ErpMcpPermissionExecutor permissionExecutor, MaterialService materialService) {
        return facadeWith(permissionExecutor, materialService, mock(CustomerService.class), mock(PrintScriptService.class));
    }

    private ErpMcpQueryFacade facadeWith(ErpMcpPermissionExecutor permissionExecutor,
                                         MaterialService materialService,
                                         CustomerService customerService) {
        return facadeWith(permissionExecutor, materialService, customerService, mock(PrintScriptService.class));
    }

    private ErpMcpQueryFacade facadeWith(ErpMcpPermissionExecutor permissionExecutor,
                                         MaterialService materialService,
                                         CustomerService customerService,
                                         PrintScriptService printScriptService) {
        return new ErpMcpQueryFacade(
                permissionExecutor,
                mock(GlobalSearchService.class),
                printScriptService,
                materialService,
                mock(MaterialCategoryService.class),
                mock(SupplierService.class),
                customerService,
                mock(ProjectService.class),
                mock(WarehouseService.class),
                mock(CarrierService.class),
                mock(PurchaseOrderService.class),
                mock(PurchaseInboundService.class),
                mock(SalesOrderService.class),
                mock(SalesOutboundService.class),
                mock(InventoryReportService.class)
        );
    }

    private CustomerResponse customerResponse(Long id, String customerName) {
        return new CustomerResponse(
                id,
                "C" + id,
                customerName,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "正常",
                null
        );
    }
}
