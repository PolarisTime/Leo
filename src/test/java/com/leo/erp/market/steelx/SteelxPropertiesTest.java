package com.leo.erp.market.steelx;

import com.leo.erp.common.support.QuoteRegionCatalog;
import com.leo.erp.market.region.service.QuoteRegionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 取价地区目录端口契约。
 *
 * <p>{@link SteelxProperties} 作为 {@link QuoteRegionCatalog} 的实现，负责把地区映射归一成
 * 「可选取价地区」供运行时配置下发。自 md_quote_region 落地后它有两个来源：表数据优先，
 * 表为空或 BeanFactory 不可用时回退 yml 配置与 Java 默认值。</p>
 *
 * <p>这里锁定回退语义：配置属性类通常早于业务 Bean 初始化，早期版本用
 * {@code @Autowired(required=false)} setter 注入会**被静默跳过**，导致表数据形同虚设；
 * 现改为 {@link org.springframework.beans.factory.BeanFactoryAware} 延迟解析，本测试
 * 保证这两条路径都有覆盖。</p>
 */
class SteelxPropertiesTest {

    @Test
    void listSupportedQuoteRegions_shouldFallBackToBuiltInCitiesWhenUnconfigured() {
        assertThat(new SteelxProperties().listSupportedQuoteRegions())
                .containsExactly("杭州", "上海", "宁波", "嘉兴", "绍兴");
    }

    @Test
    void listSupportedQuoteRegions_shouldKeepConfiguredOrder() {
        SteelxProperties properties = new SteelxProperties();
        properties.setRegions(List.of(
                new SteelxProperties.Region("南京", "nanjing"),
                new SteelxProperties.Region("杭州", "hangzhou")));

        assertThat(properties.listSupportedQuoteRegions()).containsExactly("南京", "杭州");
    }

    @Test
    void listSupportedQuoteRegions_shouldExposeEmptyListWhenRegionsCleared() {
        SteelxProperties properties = new SteelxProperties();
        properties.setRegions(List.of());

        assertThat(properties.listSupportedQuoteRegions()).isEmpty();
    }

    @Test
    void listSupportedQuoteRegions_shouldSkipIncompleteRegionEntries() {
        SteelxProperties properties = new SteelxProperties();
        properties.setRegions(List.of(
                new SteelxProperties.Region("南京", "nanjing"),
                new SteelxProperties.Region(null, "shanghai"),
                new SteelxProperties.Region("宁波", null)));

        assertThat(properties.listSupportedQuoteRegions()).containsExactly("南京");
    }

    @Test
    void listSupportedQuoteRegions_shouldAgreeWithSupportedRegions() {
        SteelxProperties properties = new SteelxProperties();

        assertThat(properties.listSupportedQuoteRegions())
                .isEqualTo(properties.supportedRegions());
    }

    // ---------- md_quote_region 表接入 ----------

    @Test
    void listSupportedQuoteRegions_shouldPreferTableOverConfiguredRegions() throws Exception {
        SteelxProperties properties = new SteelxProperties();
        properties.setRegions(List.of(new SteelxProperties.Region("南京", "nanjing")));
        properties.setBeanFactory(beanFactoryReturning(regionServiceReturning(mapOf("成都", "chengdo"))));

        // 表里有数据时必须以表为准, 否则新增城市无法通过维护界面生效
        assertThat(properties.listSupportedQuoteRegions()).containsExactly("成都");
        assertThat(properties.regionMap()).containsOnlyKeys("成都");
        assertThat(properties.supportsRegion("成都")).isTrue();
        assertThat(properties.supportsRegion("南京")).isFalse();
    }

    @Test
    void listSupportedQuoteRegions_shouldFallBackWhenTableEmpty() throws Exception {
        SteelxProperties properties = new SteelxProperties();
        properties.setRegions(List.of(new SteelxProperties.Region("南京", "nanjing")));
        properties.setBeanFactory(beanFactoryReturning(regionServiceReturning(mapOf())));

        // 表未初始化 / 迁移未执行时行为必须与改造前一致
        assertThat(properties.listSupportedQuoteRegions()).containsExactly("南京");
    }

    @Test
    void listSupportedQuoteRegions_shouldFallBackWhenServiceUnavailable() throws Exception {
        SteelxProperties properties = new SteelxProperties();
        properties.setBeanFactory(beanFactoryReturning(null));

        assertThat(properties.listSupportedQuoteRegions())
                .containsExactly("杭州", "上海", "宁波", "嘉兴", "绍兴");
    }

    @Test
    void regionMap_shouldYieldNoRegionsWhenServiceReportsNothingAndConfigCleared() throws Exception {
        SteelxProperties properties = new SteelxProperties();
        properties.setRegions(List.of());
        properties.setBeanFactory(beanFactoryReturning(regionServiceReturning(mapOf())));

        assertThat(properties.regionMap()).isEmpty();
        assertThat(properties.listSupportedQuoteRegions()).isEmpty();
    }

    private Map<String, String> mapOf(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private QuoteRegionService regionServiceReturning(Map<String, String> mapping) {
        QuoteRegionService service = mock(QuoteRegionService.class);
        lenient().when(service.activeRegionMap()).thenReturn(mapping);
        return service;
    }

    @SuppressWarnings("unchecked")
    private BeanFactory beanFactoryReturning(QuoteRegionService service) {
        BeanFactory beanFactory = mock(BeanFactory.class);
        ObjectProvider<QuoteRegionService> provider = mock(ObjectProvider.class);
        when(beanFactory.getBeanProvider(QuoteRegionService.class)).thenReturn(provider);
        lenient().when(provider.getIfAvailable()).thenReturn(service);
        return beanFactory;
    }
}
