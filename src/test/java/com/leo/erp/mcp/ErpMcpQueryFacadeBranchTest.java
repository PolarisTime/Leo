package com.leo.erp.mcp;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.master.carrier.service.CarrierService;
import com.leo.erp.master.carrier.web.dto.CarrierResponse;
import com.leo.erp.master.customer.service.CustomerService;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import com.leo.erp.master.material.service.MaterialCategoryService;
import com.leo.erp.master.material.service.MaterialService;
import com.leo.erp.master.material.web.dto.MaterialCategoryResponse;
import com.leo.erp.master.material.web.dto.MaterialResponse;
import com.leo.erp.master.project.service.ProjectService;
import com.leo.erp.master.project.web.dto.ProjectResponse;
import com.leo.erp.master.supplier.service.SupplierService;
import com.leo.erp.master.supplier.web.dto.SupplierResponse;
import com.leo.erp.master.warehouse.service.WarehouseService;
import com.leo.erp.master.warehouse.web.dto.WarehouseResponse;
import com.leo.erp.purchase.inbound.service.PurchaseInboundService;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import com.leo.erp.purchase.order.service.PurchaseOrderService;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import com.leo.erp.report.inventory.service.InventoryReportService;
import com.leo.erp.report.inventory.web.dto.InventoryReportResponse;
import com.leo.erp.sales.order.service.SalesOrderService;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.sales.outbound.service.SalesOutboundService;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import com.leo.erp.search.service.GlobalSearchService;
import com.leo.erp.search.web.GlobalSearchResponse;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErpMcpQueryFacadeBranchTest {

    @Test
    void shouldDefaultAndCapGlobalSearchLimit() {
        Fixture fixture = new Fixture();
        List<GlobalSearchResponse> defaultResult = List.of(
                new GlobalSearchResponse("material", "钢材", "T1", "M001", "summary", false)
        );
        when(fixture.globalSearchService.search("钢", 20, List.of("material"))).thenReturn(defaultResult);
        when(fixture.globalSearchService.search("钢", 50, List.of("material"))).thenReturn(List.of());

        assertThat(fixture.facade.globalSearch("钢", List.of("material"), null)).isSameAs(defaultResult);
        assertThat(fixture.facade.globalSearch("钢", List.of("material"), 500)).isEmpty();

        verify(fixture.globalSearchService).search("钢", 20, List.of("material"));
        verify(fixture.globalSearchService).search("钢", 50, List.of("material"));
    }

    @Test
    void shouldTrimModuleKeyAndUseDefaultPageSize() {
        Fixture fixture = new Fixture();
        fixture.runPermissionSuppliers();
        AtomicReference<PageQuery> queryRef = new AtomicReference<>();
        when(fixture.materialService.page(any(PageQuery.class), eq("钢"), isNull(), isNull()))
                .thenAnswer(invocation -> {
                    PageQuery query = invocation.getArgument(0);
                    queryRef.set(query);
                    return pageOf(List.of(material(1L, "螺纹钢")), query);
                });

        PageResponse<?> response = fixture.facade.queryRecords(" material ", "钢", null, null, null);

        assertThat(response.content()).hasSize(1);
        assertThat(queryRef.get().page()).isZero();
        assertThat(queryRef.get().size()).isEqualTo(20);
        verify(fixture.permissionExecutor).read(eq("material"), any());
    }

    @Test
    void shouldRouteAllSupportedPageReaders() {
        Fixture fixture = new Fixture();
        fixture.runPermissionSuppliers();
        stubEmptyPages(fixture);

        assertThat(fixture.facade.queryRecords("material-category", "kw", "正常", 0, 10).content()).isEmpty();
        assertThat(fixture.facade.queryRecords("supplier", "kw", "正常", 0, 10).content()).isEmpty();
        assertThat(fixture.facade.queryRecords("customer", "kw", "正常", 0, 10).content()).isEmpty();
        assertThat(fixture.facade.queryRecords("project", "kw", "正常", 0, 10).content()).isEmpty();
        assertThat(fixture.facade.queryRecords("warehouse", "kw", "正常", 0, 10).content()).isEmpty();
        assertThat(fixture.facade.queryRecords("carrier", "kw", "正常", 0, 10).content()).isEmpty();
        assertThat(fixture.facade.queryRecords("purchase-order", "kw", "草稿", 0, 10).content()).isEmpty();
        assertThat(fixture.facade.queryRecords("purchase-inbound", "kw", "待审核", 0, 10).content()).isEmpty();
        assertThat(fixture.facade.queryRecords("sales-order", "kw", "已审核", 0, 10).content()).isEmpty();
        assertThat(fixture.facade.queryRecords("sales-outbound", "kw", "已出库", 0, 10).content()).isEmpty();
        assertThat(fixture.facade.queryRecords("inventory-report", "kw", null, 0, 10).content()).isEmpty();

        verify(fixture.warehouseService).page(any(PageQuery.class), eq("kw"), isNull(), eq("正常"));
        verify(fixture.inventoryReportService).page(any(PageQuery.class), eq("kw"), isNull(), isNull(), isNull());
    }

    @Test
    void shouldPassPageFiltersForOrderReaders() {
        Fixture fixture = new Fixture();
        fixture.runPermissionSuppliers();
        AtomicReference<PageFilter> purchaseFilter = new AtomicReference<>();
        AtomicReference<PageFilter> salesFilter = new AtomicReference<>();
        when(fixture.purchaseOrderService.page(any(PageQuery.class), any(PageFilter.class)))
                .thenAnswer(invocation -> {
                    purchaseFilter.set(invocation.getArgument(1));
                    return pageOf(List.<PurchaseOrderResponse>of(), invocation.getArgument(0));
                });
        when(fixture.salesOrderService.page(any(PageQuery.class), any(PageFilter.class)))
                .thenAnswer(invocation -> {
                    salesFilter.set(invocation.getArgument(1));
                    return pageOf(List.<SalesOrderResponse>of(), invocation.getArgument(0));
                });

        fixture.facade.queryRecords("purchase-order", "PO", "待审核", 0, 10);
        fixture.facade.queryRecords("sales-order", "SO", "已审核", 0, 10);

        assertThat(purchaseFilter.get().keyword()).isEqualTo("PO");
        assertThat(purchaseFilter.get().status()).isEqualTo("待审核");
        assertThat(salesFilter.get().keyword()).isEqualTo("SO");
        assertThat(salesFilter.get().status()).isEqualTo("已审核");
    }

    @Test
    void shouldReadRecordDetailAndValidateInputs() {
        Fixture fixture = new Fixture();
        fixture.runPermissionSuppliers();
        MaterialResponse material = material(11L, "线材");
        when(fixture.materialService.detail(11L)).thenReturn(material);

        assertThat(fixture.facade.getRecord(" material ", 11L)).isSameAs(material);

        assertThatThrownBy(() -> fixture.facade.getRecord("unknown", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MCP 不支持读取该模块详情");
        assertThatThrownBy(() -> fixture.facade.getRecord("material", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("recordId 必须大于0");
        assertThatThrownBy(() -> fixture.facade.getRecord("material", 0L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("recordId 必须大于0");
        assertThatThrownBy(() -> fixture.facade.getRecord(" ", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少模块或选项标识");
    }

    @Test
    void shouldDistinctAndFilterMaterialGradeOptions() {
        Fixture fixture = new Fixture();
        fixture.runPermissionSuppliers();
        AtomicReference<PageQuery> queryRef = new AtomicReference<>();
        when(fixture.materialService.page(any(PageQuery.class), eq("HRB"), isNull(), isNull()))
                .thenAnswer(invocation -> {
                    PageQuery query = invocation.getArgument(0);
                    queryRef.set(query);
                    return pageOf(
                            List.of(
                                    material(1L, "HRB400"),
                                    material(2L, "HRB400"),
                                    material(3L, " ")
                            ),
                            query
                    );
                });

        Object result = fixture.facade.listOptions("material-grade", "HRB", null);

        assertThat(result).isInstanceOf(PageResponse.class);
        PageResponse<?> page = (PageResponse<?>) result;
        assertThat(page.content()).isEqualTo(List.of(Map.of("label", "HRB400", "value", "HRB400")));
        assertThat(queryRef.get().size()).isEqualTo(20);
        verify(fixture.permissionExecutor).read(eq("material"), any());
    }

    @Test
    void shouldBuildCustomerOptionLabelWithDifferentProjectName() {
        Fixture fixture = new Fixture();
        fixture.runPermissionSuppliers();
        when(fixture.customerService.page(any(PageQuery.class), eq("华东"), eq("正常")))
                .thenAnswer(invocation -> pageOf(
                        List.of(customer(1L, "上海客户", "上海项目"), customer(2L, "同名客户", "同名客户")),
                        invocation.getArgument(0)
                ));

        PageResponse<?> result = (PageResponse<?>) fixture.facade.listOptions(" customer ", "华东", 10);

        assertThat(result.content()).isEqualTo(List.of(
                Map.of("id", 1L, "label", "上海客户 / 上海项目", "value", "上海客户"),
                Map.of("id", 2L, "label", "同名客户", "value", "同名客户")
        ));
    }

    @Test
    void shouldBuildCustomerOptionLabelWhenProjectNameIsNull() {
        Fixture fixture = new Fixture();
        fixture.runPermissionSuppliers();
        when(fixture.customerService.page(any(PageQuery.class), eq("客户"), eq("正常")))
                .thenAnswer(invocation -> pageOf(List.of(customer(1L, "客户A", null)), invocation.getArgument(0)));

        PageResponse<?> result = (PageResponse<?>) fixture.facade.listOptions("customer", "客户", 10);

        assertThat(result.content()).isEqualTo(List.of(
                Map.of("id", 1L, "label", "客户A", "value", "客户A")
        ));
    }

    @Test
    void shouldRouteOtherOptionReadersWithNormalStatus() {
        Fixture fixture = new Fixture();
        fixture.runPermissionSuppliers();
        when(fixture.materialCategoryService.page(any(PageQuery.class), eq("板"), eq("正常")))
                .thenAnswer(invocation -> pageOf(List.of(materialCategory("板材")), invocation.getArgument(0)));
        when(fixture.supplierService.page(any(PageQuery.class), eq("供"), eq("正常")))
                .thenAnswer(invocation -> pageOf(List.of(supplier(3L, "供应商A")), invocation.getArgument(0)));
        when(fixture.warehouseService.page(any(PageQuery.class), eq("仓"), isNull(), eq("正常")))
                .thenAnswer(invocation -> pageOf(List.of(warehouse(4L, "一号仓")), invocation.getArgument(0)));
        when(fixture.carrierService.page(any(PageQuery.class), eq("物"), eq("正常")))
                .thenAnswer(invocation -> pageOf(List.of(carrier(5L, "物流A")), invocation.getArgument(0)));

        assertThat(((PageResponse<?>) fixture.facade.listOptions("material-category", "板", 10)).content())
                .isEqualTo(List.of(Map.of("label", "板材", "value", "板材")));
        assertThat(((PageResponse<?>) fixture.facade.listOptions("supplier", "供", 10)).content())
                .isEqualTo(List.of(Map.of("id", 3L, "label", "供应商A", "value", "供应商A")));
        assertThat(((PageResponse<?>) fixture.facade.listOptions("warehouse", "仓", 10)).content())
                .isEqualTo(List.of(Map.of("id", 4L, "label", "一号仓", "value", "一号仓")));
        assertThat(((PageResponse<?>) fixture.facade.listOptions("carrier", "物", 10)).content())
                .isEqualTo(List.of(Map.of("id", 5L, "label", "物流A", "value", "物流A")));
    }

    @Test
    void shouldRejectUnknownOptionAndInvalidPageSize() {
        Fixture fixture = new Fixture();

        assertThatThrownBy(() -> fixture.facade.listOptions("unknown", null, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MCP 不支持读取该选项");
        assertThatThrownBy(() -> fixture.facade.listOptions("supplier", null, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("size 必须在1到200之间");
    }

    @Test
    void shouldPreviewPrintPayloadWithMissingCollectionsAndTrimTemplateId() {
        Fixture fixture = new Fixture();
        fixture.runPermissionSuppliers();
        Map<String, Object> rawPayload = new LinkedHashMap<>();
        rawPayload.put("templateType", "A4");
        rawPayload.put("data", "not-map");
        rawPayload.put("items", "not-list");
        when(fixture.printScriptService.generateFromRecord(eq("tpl-1"), eq("sales-order"), eq(12L), any()))
                .thenReturn(rawPayload);

        Map<String, Object> result = fixture.facade.printPayloadPreview("sales-order", 12L, " tpl-1 ");

        assertThat(result)
                .containsEntry("templateType", "A4")
                .containsEntry("dataKeys", List.of())
                .containsEntry("itemCount", 0)
                .doesNotContainKeys("templateName", "businessNo", "recordId", "moduleKey");
        verify(fixture.permissionExecutor).print(eq("sales-order"), any());
    }

    @Test
    void shouldValidatePrintPayloadInputs() {
        Fixture fixture = new Fixture();

        assertThatThrownBy(() -> fixture.facade.printPayloadPreview(null, 1L, "tpl"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少模块或选项标识");
        assertThatThrownBy(() -> fixture.facade.printPayloadPreview("sales-order", null, "tpl"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("recordId 必须大于0");
        assertThatThrownBy(() -> fixture.facade.printPayloadPreview("sales-order", 0L, "tpl"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("recordId 必须大于0");
        assertThatThrownBy(() -> fixture.facade.printPayloadPreview("sales-order", 1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("templateId 不能为空");
        assertThatThrownBy(() -> fixture.facade.printPayloadPreview("sales-order", 1L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("templateId 不能为空");
    }

    @Test
    void shouldHandleOptionNullFallbackAndEmptyCombinations() {
        Fixture fixture = new Fixture();

        Map<String, Object> labelFromValue = ReflectionTestUtils.invokeMethod(
                fixture.facade, "option", (Long) null, null, "V");
        Map<String, Object> valueFromLabel = ReflectionTestUtils.invokeMethod(
                fixture.facade, "option", 7L, "L", null);
        Map<String, Object> emptyOption = ReflectionTestUtils.invokeMethod(
                fixture.facade, "option", (Long) null, " ", null);

        assertThat(labelFromValue).containsExactly(
                Map.entry("label", "V"),
                Map.entry("value", "V")
        );
        assertThat(valueFromLabel).containsExactly(
                Map.entry("id", 7L),
                Map.entry("label", "L"),
                Map.entry("value", "L")
        );
        assertThat(emptyOption).isNull();
    }

    private static void stubEmptyPages(Fixture fixture) {
        when(fixture.materialCategoryService.page(any(PageQuery.class), eq("kw"), eq("正常")))
                .thenAnswer(invocation -> pageOf(List.<MaterialCategoryResponse>of(), invocation.getArgument(0)));
        when(fixture.supplierService.page(any(PageQuery.class), eq("kw"), eq("正常")))
                .thenAnswer(invocation -> pageOf(List.<SupplierResponse>of(), invocation.getArgument(0)));
        when(fixture.customerService.page(any(PageQuery.class), eq("kw"), eq("正常")))
                .thenAnswer(invocation -> pageOf(List.<CustomerResponse>of(), invocation.getArgument(0)));
        when(fixture.projectService.page(any(PageQuery.class), eq("kw"), eq("正常")))
                .thenAnswer(invocation -> pageOf(List.<ProjectResponse>of(), invocation.getArgument(0)));
        when(fixture.warehouseService.page(any(PageQuery.class), eq("kw"), isNull(), eq("正常")))
                .thenAnswer(invocation -> pageOf(List.<WarehouseResponse>of(), invocation.getArgument(0)));
        when(fixture.carrierService.page(any(PageQuery.class), eq("kw"), eq("正常")))
                .thenAnswer(invocation -> pageOf(List.<CarrierResponse>of(), invocation.getArgument(0)));
        when(fixture.purchaseOrderService.page(any(PageQuery.class), any(PageFilter.class)))
                .thenAnswer(invocation -> pageOf(List.<PurchaseOrderResponse>of(), invocation.getArgument(0)));
        when(fixture.purchaseInboundService.page(any(PageQuery.class), any(PageFilter.class)))
                .thenAnswer(invocation -> pageOf(List.<PurchaseInboundResponse>of(), invocation.getArgument(0)));
        when(fixture.salesOrderService.page(any(PageQuery.class), any(PageFilter.class)))
                .thenAnswer(invocation -> pageOf(List.<SalesOrderResponse>of(), invocation.getArgument(0)));
        when(fixture.salesOutboundService.page(any(PageQuery.class), any(PageFilter.class)))
                .thenAnswer(invocation -> pageOf(List.<SalesOutboundResponse>of(), invocation.getArgument(0)));
        when(fixture.inventoryReportService.page(any(PageQuery.class), eq("kw"), isNull(), isNull(), isNull()))
                .thenAnswer(invocation -> pageOf(List.<InventoryReportResponse>of(), invocation.getArgument(0)));
    }

    private static MaterialResponse material(Long id, String material) {
        return new MaterialResponse(
                id,
                "M" + id,
                "品牌",
                material,
                "型材",
                "10",
                "6m",
                "吨",
                "件",
                BigDecimal.ONE,
                1,
                BigDecimal.TEN,
                false,
                null
        );
    }

    private static MaterialCategoryResponse materialCategory(String categoryName) {
        return new MaterialCategoryResponse(1L, "C1", categoryName, 1, false, null, null, "正常", null);
    }

    private static CustomerResponse customer(Long id, String customerName, String projectName) {
        return new CustomerResponse(
                id,
                "C" + id,
                customerName,
                "",
                "",
                "",
                "",
                projectName,
                "",
                "",
                "正常",
                null
        );
    }

    private static SupplierResponse supplier(Long id, String supplierName) {
        return new SupplierResponse(id, "S" + id, supplierName, "", "", "", "正常", null);
    }

    private static WarehouseResponse warehouse(Long id, String warehouseName) {
        return new WarehouseResponse(id, "W" + id, warehouseName, "普通", "", "", "", "正常", null);
    }

    private static CarrierResponse carrier(Long id, String carrierName) {
        return new CarrierResponse(id, "L" + id, carrierName, "", "", "", List.of(), "按吨", "正常", null);
    }

    private static <T> Page<T> pageOf(List<T> content, PageQuery query) {
        return new PageImpl<>(content, PageRequest.of(query.page(), query.size()), content.size());
    }

    private static final class Fixture {
        private final ErpMcpPermissionExecutor permissionExecutor = mock(ErpMcpPermissionExecutor.class);
        private final GlobalSearchService globalSearchService = mock(GlobalSearchService.class);
        private final PrintScriptService printScriptService = mock(PrintScriptService.class);
        private final MaterialService materialService = mock(MaterialService.class);
        private final MaterialCategoryService materialCategoryService = mock(MaterialCategoryService.class);
        private final SupplierService supplierService = mock(SupplierService.class);
        private final CustomerService customerService = mock(CustomerService.class);
        private final ProjectService projectService = mock(ProjectService.class);
        private final WarehouseService warehouseService = mock(WarehouseService.class);
        private final CarrierService carrierService = mock(CarrierService.class);
        private final PurchaseOrderService purchaseOrderService = mock(PurchaseOrderService.class);
        private final PurchaseInboundService purchaseInboundService = mock(PurchaseInboundService.class);
        private final SalesOrderService salesOrderService = mock(SalesOrderService.class);
        private final SalesOutboundService salesOutboundService = mock(SalesOutboundService.class);
        private final InventoryReportService inventoryReportService = mock(InventoryReportService.class);
        private final ErpMcpQueryFacade facade = new ErpMcpQueryFacade(
                permissionExecutor,
                globalSearchService,
                printScriptService,
                materialService,
                materialCategoryService,
                supplierService,
                customerService,
                projectService,
                warehouseService,
                carrierService,
                purchaseOrderService,
                purchaseInboundService,
                salesOrderService,
                salesOutboundService,
                inventoryReportService
        );

        @SuppressWarnings("unchecked")
        private void runPermissionSuppliers() {
            when(permissionExecutor.read(any(), any()))
                    .thenAnswer(invocation -> invocation.getArgument(1, Supplier.class).get());
            when(permissionExecutor.print(any(), any()))
                    .thenAnswer(invocation -> invocation.getArgument(1, Supplier.class).get());
        }
    }
}
