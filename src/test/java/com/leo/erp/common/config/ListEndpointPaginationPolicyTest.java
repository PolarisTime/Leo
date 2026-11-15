package com.leo.erp.common.config;

import com.leo.erp.common.api.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约测试：所有返回 {@code List} 的 HTTP 端点必须登记在「有界列表清单」里并写明不翻页的理由。
 *
 * <p>背景：API 审计盘点出 21 个返回 {@code List<...>} 的端点（其余列表端点均为
 * {@link PageResponse}）。这些端点绝大多数是下拉选项、矩阵/子资源或一次性回显，
 * 数量天然有上界；强行改成分页会破坏下拉/矩阵的「一次取全」语义。本测试把
 * 「为什么这个端点可以不翻页」固化为代码，新增 List 端点若不登记会直接失败，
 * 避免无界列表悄悄上线。</p>
 *
 * <p>未来若某个清单项的数据量增长到需要分页，应改为返回 {@link PageResponse} 并从
 * 本清单移除，而不是在这里补一句「数据很多」。</p>
 */
class ListEndpointPaginationPolicyTest {

    /** 21 个有界 List 端点 → 不翻页的理由。 */
    private static final Map<String, String> BOUNDED_LIST_ENDPOINTS = boundedListEndpoints();

    @Test
    void everyListEndpointMustBeRegisteredWithABoundReason() throws Exception {
        Map<String, String> actual = scanListEndpoints();

        assertThat(actual.keySet())
                .as("返回 List 的 HTTP 端点清单发生变化；新增端点必须在此登记理由，或改为 PageResponse")
                .containsExactlyInAnyOrderElementsOf(BOUNDED_LIST_ENDPOINTS.keySet());
        BOUNDED_LIST_ENDPOINTS.forEach((endpoint, reason) ->
                assertThat(reason).as("端点 %s 的理由不能为空", endpoint).isNotBlank());
    }

    private static Map<String, String> boundedListEndpoints() {
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("CarrierController#options", "物流商下拉选项，按启用状态全量返回，数量受主数据规模约束");
        endpoints.put("CompanySettingController#options", "结算主体下拉选项，数量受公司设置规模约束");
        endpoints.put("CustomerController#options", "客户下拉选项，按启用状态全量返回，数量受主数据规模约束");
        endpoints.put("GlobalSearchController#search", "已有 limit 参数且服务端钳制到 MAX_TOTAL_LIMIT，结果集有硬上界");
        endpoints.put("MaterialCategoryController#options", "商品分类下拉选项，数量受分类字典约束");
        endpoints.put("MaterialController#materialBrands", "品牌字典的固定去重值，数量受品牌字典约束");
        endpoints.put("MaterialController#materialGrades", "牌号字典的固定去重值，数量受牌号字典约束");
        endpoints.put("MaterialPriceMatchController#match", "已有 limit/offset 参数，结果集有硬上界");
        endpoints.put("PermissionController#catalog", "权限目录是固定词表，随代码发布变化而非业务数据增长");
        endpoints.put("PrintTemplateController#list", "按单据类型返回全部模板，下拉需要全量；模板数量少且有单据类型过滤");
        endpoints.put("ProjectController#options", "项目下拉选项，按客户过滤，数量受项目规模约束");
        endpoints.put("ProjectController#priceRules", "单个项目的价格规则，数量受项目配置约束");
        endpoints.put("ProjectController#replacePriceRules", "PUT 替换后的回显，数量等于请求体条数");
        endpoints.put("PurchaseOrderController#options", "采购订单下拉选项，按关键字/状态过滤，数量受订单规模约束");
        endpoints.put("PurchaseOrderController#warehouseRecommendations", "按采购订单明细返回候选仓库，数量受明细行数约束");
        endpoints.put("SteelQuoteCalendarController#list", "按日期区间返回日历，前端按月/周请求，区间长度受 UI 约束");
        endpoints.put("SupplierController#options", "供应商下拉选项，按启用状态全量返回，数量受主数据规模约束");
        endpoints.put("SupplierBrandSpecController#list", "按供应商返回品牌规格，数量受品牌字典约束");
        endpoints.put("SupplierBrandSpecController#replace", "PUT 替换后的回显，数量等于请求体条数");
        endpoints.put("SupplierPriceListController#lowestQuotes", "按品牌/供应商矩阵返回最低价，数量受矩阵列约束");
        endpoints.put("WarehouseController#options", "仓库下拉选项，数量受仓库主数据规模约束");
        return endpoints;
    }

    /** 只统计真正的 HTTP 端点；controller 内的私有/辅助方法即使返回 List 也不算对外契约。 */
    private static boolean isHttpEndpoint(Method method) {
        return method.isAnnotationPresent(org.springframework.web.bind.annotation.RequestMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class);
    }

    private Map<String, String> scanListEndpoints() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Map<String, String> found = new LinkedHashMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.leo.erp")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (Modifier.isAbstract(type.getModifiers()) || type.getName().indexOf('$') >= 0) {
                continue;
            }
            for (Method method : type.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge() || !isHttpEndpoint(method)) {
                    continue;
                }
                Class<?> rawReturn = method.getReturnType();
                if (rawReturn == PageResponse.class || !List.class.isAssignableFrom(rawReturn)) {
                    continue;
                }
                found.put(type.getSimpleName() + "#" + method.getName(), method.getName());
            }
        }
        assertThat(found).as("应扫描到 List 端点，否则本契约测试没有覆盖真实接口面").isNotEmpty();
        assertThat(new TreeSet<>(found.keySet())).hasSize(found.size());
        return found;
    }
}
