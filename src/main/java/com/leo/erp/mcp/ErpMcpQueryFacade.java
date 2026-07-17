package com.leo.erp.mcp;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.carrier.service.CarrierService;
import com.leo.erp.master.customer.service.CustomerService;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
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
import com.leo.erp.system.printtemplate.service.PrintRenderOptions;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
class ErpMcpQueryFacade {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 100;
    private static final int DEFAULT_SEARCH_LIMIT = 20;
    private static final int MAX_SEARCH_LIMIT = 50;

    private final ErpMcpPermissionExecutor permissionExecutor;
    private final GlobalSearchService globalSearchService;
    private final PrintScriptService printScriptService;
    private final Map<String, PageReader> pageReaders;
    private final Map<String, DetailReader> detailReaders;
    private final Map<String, OptionReader> optionReaders;

    ErpMcpQueryFacade(ErpMcpPermissionExecutor permissionExecutor,
                      GlobalSearchService globalSearchService,
                      PrintScriptService printScriptService,
                      MaterialService materialService,
                      MaterialCategoryService materialCategoryService,
                      SupplierService supplierService,
                      CustomerService customerService,
                      ProjectService projectService,
                      WarehouseService warehouseService,
                      CarrierService carrierService,
                      PurchaseOrderService purchaseOrderService,
                      PurchaseInboundService purchaseInboundService,
                      SalesOrderService salesOrderService,
                      SalesOutboundService salesOutboundService,
                      InventoryReportService inventoryReportService) {
        this.permissionExecutor = permissionExecutor;
        this.globalSearchService = globalSearchService;
        this.printScriptService = printScriptService;
        this.pageReaders = pageReaders(
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
        this.detailReaders = detailReaders(
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
                salesOutboundService
        );
        this.optionReaders = optionReaders(
                materialService,
                materialCategoryService,
                supplierService,
                customerService,
                warehouseService,
                carrierService
        );
    }

    List<GlobalSearchResponse> globalSearch(String keyword, List<String> moduleKeys, Integer limit) {
        return globalSearchService.search(keyword, limit(limit, DEFAULT_SEARCH_LIMIT, MAX_SEARCH_LIMIT), moduleKeys);
    }

    PageResponse<?> queryRecords(String moduleKey, String keyword, String status, Integer page, Integer size) {
        String normalizedModuleKey = normalizeKey(moduleKey);
        PageReader reader = pageReaders.get(normalizedModuleKey);
        if (reader == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "MCP 不支持查询该模块: " + normalizedModuleKey);
        }
        PageQuery query = mcpPageQuery(page, size);
        return permissionExecutor.read(normalizedModuleKey, () -> reader.read(query, keyword, status));
    }

    Object getRecord(String moduleKey, Long recordId) {
        String normalizedModuleKey = normalizeKey(moduleKey);
        DetailReader reader = detailReaders.get(normalizedModuleKey);
        if (reader == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "MCP 不支持读取该模块详情: " + normalizedModuleKey);
        }
        if (recordId == null || recordId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "recordId 必须大于0");
        }
        return permissionExecutor.read(normalizedModuleKey, () -> reader.read(recordId));
    }

    Object listOptions(String optionType, String keyword, Integer limit) {
        String normalizedOptionType = normalizeKey(optionType);
        OptionReader reader = optionReaders.get(normalizedOptionType);
        if (reader == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "MCP 不支持读取该选项: " + normalizedOptionType);
        }
        PageQuery query = mcpPageQuery(0, limit);
        return permissionExecutor.read(reader.moduleKey(), () -> reader.read(query, keyword));
    }

    Map<String, Object> printPayloadPreview(String moduleKey, Long recordId, String templateId) {
        String normalizedModuleKey = normalizeKey(moduleKey);
        if (recordId == null || recordId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "recordId 必须大于0");
        }
        if (templateId == null || templateId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "templateId 不能为空");
        }
        return permissionExecutor.print(normalizedModuleKey, () -> minimizePrintPayload(printScriptService.generateFromRecord(
                templateId.trim(),
                normalizedModuleKey,
                recordId,
                PrintRenderOptions.defaults()
        )));
    }

    private Map<String, PageReader> pageReaders(MaterialService materialService,
                                                MaterialCategoryService materialCategoryService,
                                                SupplierService supplierService,
                                                CustomerService customerService,
                                                ProjectService projectService,
                                                WarehouseService warehouseService,
                                                CarrierService carrierService,
                                                PurchaseOrderService purchaseOrderService,
                                                PurchaseInboundService purchaseInboundService,
                                                SalesOrderService salesOrderService,
                                                SalesOutboundService salesOutboundService,
                                                InventoryReportService inventoryReportService) {
        Map<String, PageReader> readers = new LinkedHashMap<>();
        readers.put("material", (query, keyword, status) -> PageResponse.from(materialService.page(query, keyword, null, null)));
        readers.put("material-category", (query, keyword, status) ->
                PageResponse.from(materialCategoryService.page(query, keyword, status)));
        readers.put("supplier", (query, keyword, status) -> PageResponse.from(supplierService.page(query, keyword, status)));
        readers.put("customer", (query, keyword, status) -> PageResponse.from(customerService.page(query, keyword, status)));
        readers.put("project", (query, keyword, status) -> PageResponse.from(projectService.page(query, keyword, status)));
        readers.put("warehouse", (query, keyword, status) ->
                PageResponse.from(warehouseService.page(query, keyword, null, status)));
        readers.put("carrier", (query, keyword, status) -> PageResponse.from(carrierService.page(query, keyword, status)));
        readers.put("purchase-order", (query, keyword, status) ->
                PageResponse.from(purchaseOrderService.page(query, PageFilter.of(keyword, status, null, null))));
        readers.put("purchase-inbound", (query, keyword, status) ->
                PageResponse.from(purchaseInboundService.page(query, PageFilter.of(keyword, status, null, null))));
        readers.put("sales-order", (query, keyword, status) -> PageResponse.from(salesOrderService.page(
                query,
                new PageFilter(keyword, status, null, null, null, null, null, null, null, null, null, null, null, null)
        )));
        readers.put("sales-outbound", (query, keyword, status) -> PageResponse.from(salesOutboundService.page(
                query,
                new PageFilter(keyword, status, null, null, null, null, null, null, null, null, null, null, null, null)
        )));
        readers.put("inventory-report", (query, keyword, status) ->
                PageResponse.from(inventoryReportService.page(query, keyword, null, null, null)));
        return Map.copyOf(readers);
    }

    private Map<String, DetailReader> detailReaders(MaterialService materialService,
                                                    MaterialCategoryService materialCategoryService,
                                                    SupplierService supplierService,
                                                    CustomerService customerService,
                                                    ProjectService projectService,
                                                    WarehouseService warehouseService,
                                                    CarrierService carrierService,
                                                    PurchaseOrderService purchaseOrderService,
                                                    PurchaseInboundService purchaseInboundService,
                                                    SalesOrderService salesOrderService,
                                                    SalesOutboundService salesOutboundService) {
        Map<String, DetailReader> readers = new LinkedHashMap<>();
        readers.put("material", materialService::detail);
        readers.put("material-category", materialCategoryService::detail);
        readers.put("supplier", supplierService::detail);
        readers.put("customer", customerService::detail);
        readers.put("project", projectService::detail);
        readers.put("warehouse", warehouseService::detail);
        readers.put("carrier", carrierService::detail);
        readers.put("purchase-order", purchaseOrderService::detail);
        readers.put("purchase-inbound", purchaseInboundService::detail);
        readers.put("sales-order", salesOrderService::detail);
        readers.put("sales-outbound", salesOutboundService::detail);
        return Map.copyOf(readers);
    }

    private Map<String, OptionReader> optionReaders(MaterialService materialService,
                                                    MaterialCategoryService materialCategoryService,
                                                    SupplierService supplierService,
                                                    CustomerService customerService,
                                                    WarehouseService warehouseService,
                                                    CarrierService carrierService) {
        Map<String, OptionReader> readers = new LinkedHashMap<>();
        readers.put("material-category", new OptionReader("material", (query, keyword) ->
                mapPage(PageResponse.from(materialCategoryService.page(query, keyword, "正常")),
                        item -> option(null, item.categoryName(), item.categoryName()))));
        readers.put("material-grade", new OptionReader("material", (query, keyword) ->
                mapPage(PageResponse.from(materialService.page(query, keyword, null, null)),
                        item -> option(null, item.material(), item.material()))));
        readers.put("supplier", new OptionReader("supplier", (query, keyword) ->
                mapPage(PageResponse.from(supplierService.page(query, keyword, "正常")),
                        item -> option(item.id(), item.supplierName(), item.supplierName()))));
        readers.put("customer", new OptionReader("customer", (query, keyword) ->
                mapPage(PageResponse.from(customerService.page(query, keyword, "正常")),
                        item -> option(item.id(), customerLabel(item), item.customerName()))));
        readers.put("warehouse", new OptionReader("warehouse", (query, keyword) ->
                mapPage(PageResponse.from(warehouseService.page(query, keyword, null, "正常")),
                        item -> option(item.id(), item.warehouseName(), item.warehouseName()))));
        readers.put("carrier", new OptionReader("carrier", (query, keyword) ->
                mapPage(PageResponse.from(carrierService.page(query, keyword, "正常")),
                        item -> option(item.id(), item.carrierName(), item.carrierName()))));
        return Map.copyOf(readers);
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少模块或选项标识");
        }
        return value.trim();
    }

    private PageQuery mcpPageQuery(Integer page, Integer size) {
        int resolvedPage = page == null ? 0 : page;
        if (resolvedPage > MAX_PAGE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "page 不能超过" + MAX_PAGE);
        }
        return PageQuery.of(resolvedPage, limit(size, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE), null, null);
    }

    private int limit(Integer value, int defaultValue, int maxValue) {
        if (value == null) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }

    private <S, T> PageResponse<T> mapPage(PageResponse<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.content().stream()
                        .map(mapper)
                        .filter(item -> item != null)
                        .distinct()
                        .toList(),
                page.totalElements(),
                page.totalPages(),
                page.currentPage(),
                page.pageSize(),
                page.hasMore()
        );
    }

    private Map<String, Object> minimizePrintPayload(Map<String, Object> payload) {
        Map<String, Object> preview = new LinkedHashMap<>();
        copyIfPresent(payload, preview, "templateName");
        copyIfPresent(payload, preview, "templateType");
        copyIfPresent(payload, preview, "businessNo");
        copyIfPresent(payload, preview, "recordId");
        copyIfPresent(payload, preview, "moduleKey");
        preview.put("dataKeys", keysOf(payload.get("data")));
        preview.put("itemCount", itemCount(payload.get("items")));
        return preview;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private List<String> keysOf(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return List.of();
        }
        return map.keySet().stream()
                .map(String::valueOf)
                .toList();
    }

    private int itemCount(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private Map<String, Object> option(Long id, String label, String value) {
        if ((label == null || label.isBlank()) && (value == null || value.isBlank())) {
            return null;
        }
        Map<String, Object> option = new LinkedHashMap<>();
        if (id != null) {
            option.put("id", id);
        }
        option.put("label", label == null ? value : label);
        option.put("value", value == null ? label : value);
        return option;
    }

    private String customerLabel(CustomerResponse item) {
        String projectName = item.projectName() == null ? "" : item.projectName().trim();
        if (projectName.isEmpty() || projectName.equals(item.customerName())) {
            return item.customerName();
        }
        return item.customerName() + " / " + projectName;
    }

    @FunctionalInterface
    private interface PageReader {
        PageResponse<?> read(PageQuery query, String keyword, String status);
    }

    @FunctionalInterface
    private interface DetailReader {
        Object read(Long recordId);
    }

    private record OptionReader(String moduleKey, OptionPageReader reader) {
        Object read(PageQuery query, String keyword) {
            return reader.read(query, keyword);
        }
    }

    @FunctionalInterface
    private interface OptionPageReader {
        Object read(PageQuery query, String keyword);
    }
}
