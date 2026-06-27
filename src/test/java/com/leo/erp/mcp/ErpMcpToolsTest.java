package com.leo.erp.mcp;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.error.BusinessException;
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
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErpMcpToolsTest {

    @Test
    void shouldNotCreateToolsWhenMcpServerDisabled() {
        new ApplicationContextRunner()
                .withBean(ErpMcpQueryFacade.class, () -> mock(ErpMcpQueryFacade.class))
                .withUserConfiguration(ErpMcpTools.class)
                .run(context -> assertThat(context).doesNotHaveBean(ErpMcpTools.class));
    }

    @Test
    void shouldCreateToolsWhenMcpServerEnabled() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.ai.mcp.server.enabled=true")
                .withBean(ErpMcpQueryFacade.class, () -> mock(ErpMcpQueryFacade.class))
                .withUserConfiguration(ErpMcpTools.class)
                .run(context -> assertThat(context).hasSingleBean(ErpMcpTools.class));
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

    private ErpMcpQueryFacade facadeWith(ErpMcpPermissionExecutor permissionExecutor, MaterialService materialService) {
        return new ErpMcpQueryFacade(
                permissionExecutor,
                mock(GlobalSearchService.class),
                mock(PrintScriptService.class),
                materialService,
                mock(MaterialCategoryService.class),
                mock(SupplierService.class),
                mock(CustomerService.class),
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
}
