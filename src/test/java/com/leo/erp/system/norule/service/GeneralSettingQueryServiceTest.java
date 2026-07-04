package com.leo.erp.system.norule.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.setting.PageUploadRuleQueryService;
import com.leo.erp.common.setting.PageUploadRuleSummary;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.leo.erp.system.norule.mapper.NoRuleMapper;
import com.leo.erp.system.norule.web.dto.GeneralSettingResponse;
import com.leo.erp.system.norule.web.dto.NoRuleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeneralSettingQueryServiceTest {

    @Test
    void shouldIncludeUploadRulesInGeneralSettingsPage() {
        NoRule noRule = new NoRule();
        noRule.setId(1L);
        noRule.setSettingCode("RULE_SO");
        noRule.setSettingName("销售订单单号规则");
        noRule.setBillName("销售订单");
        noRule.setPrefix("SO");
        noRule.setDateRule("yyyy");
        noRule.setSerialLength(6);
        noRule.setResetRule("YEARLY");
        noRule.setSampleNo("2026SO000001");
        noRule.setStatus("正常");
        noRule.setRemark("每年重置");

        GeneralSettingQueryService service = new GeneralSettingQueryService(
                noRuleRepository(List.of(noRule)),
                mapper(),
                stubUploadRuleService()
        );

        List<GeneralSettingResponse> records = service.page(PageQuery.of(0, 20, null, null), null, null).getContent();

        assertThat(records).hasSize(2);
        assertThat(records)
                .extracting(GeneralSettingResponse::ruleType, GeneralSettingResponse::moduleKey, GeneralSettingResponse::settingCode)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("NO_RULE", null, "RULE_SO"),
                        org.assertj.core.groups.Tuple.tuple("UPLOAD_RULE", "sales-order", "PAGE_UPLOAD_SALES_ORDER")
                );
        assertThat(records.stream()
                .filter(item -> "UPLOAD_RULE".equals(item.ruleType()))
                .findFirst()
                .orElseThrow()
                .sampleNo()).isEqualTo("upload_sales_order.pdf");
    }

    @Test
    void shouldFilterGeneralSettingsByStatusAcrossMergedSources() {
        NoRule noRule = new NoRule();
        noRule.setId(1L);
        noRule.setSettingCode("RULE_SO");
        noRule.setSettingName("销售订单单号规则");
        noRule.setBillName("销售订单");
        noRule.setPrefix("SO");
        noRule.setDateRule("yyyy");
        noRule.setSerialLength(6);
        noRule.setResetRule("YEARLY");
        noRule.setSampleNo("2026SO000001");
        noRule.setStatus("正常");

        GeneralSettingQueryService service = new GeneralSettingQueryService(
                noRuleRepository(List.of(noRule)),
                mapper(),
                () -> List.of(
                        new PageUploadRuleSummary(
                                2L,
                                "sales-order",
                                "销售订单",
                                "PAGE_UPLOAD_SALES_ORDER",
                                "销售订单上传命名规则",
                                "{yyyyMMddHHmmss}_{random8}",
                                "禁用",
                                "适用于销售订单页面上传",
                                "upload_sales_order.pdf"
                        )
                )
        );

        List<GeneralSettingResponse> records = service.page(PageQuery.of(0, 20, null, null), null, "禁用").getContent();

        assertThat(records).hasSize(1);
        assertThat(records.get(0).ruleType()).isEqualTo("UPLOAD_RULE");
        assertThat(records.get(0).status()).isEqualTo("禁用");
    }

    @Test
    void shouldFilterUploadRulesByKeywordAcrossAllSearchableFields() {
        GeneralSettingQueryService service = new GeneralSettingQueryService(
                noRuleRepository(List.of()),
                mapper(),
                () -> List.of(
                        uploadRule(2L, "purchase-order", "采购订单", "PAGE_UPLOAD_PO", "采购规则", "{po}", "正常", "采购备注", "po.pdf"),
                        uploadRule(3L, "sales-order", "销售订单", "PAGE_UPLOAD_SO", "销售规则", "{so}", "正常", "销售备注", "so.pdf"),
                        uploadRule(4L, "invoice", "发票", "PAGE_UPLOAD_INV", "开票规则", "{inv}", "正常", null, "invoice.pdf")
                )
        );

        assertThat(settingCodes(service.page(PageQuery.of(0, 20, null, null), "page_upload_po", null).getContent()))
                .containsExactly("PAGE_UPLOAD_PO");
        assertThat(settingCodes(service.page(PageQuery.of(0, 20, null, null), "采购订单", null).getContent()))
                .containsExactly("PAGE_UPLOAD_PO");
        assertThat(settingCodes(service.page(PageQuery.of(0, 20, null, null), "销售规则", null).getContent()))
                .containsExactly("PAGE_UPLOAD_SO");
        assertThat(settingCodes(service.page(PageQuery.of(0, 20, null, null), "{INV}", null).getContent()))
                .containsExactly("PAGE_UPLOAD_INV");
        assertThat(settingCodes(service.page(PageQuery.of(0, 20, null, null), "采购备注", null).getContent()))
                .containsExactly("PAGE_UPLOAD_PO");
        assertThat(settingCodes(service.page(PageQuery.of(0, 20, null, null), " ", " ").getContent()))
                .containsExactly("PAGE_UPLOAD_INV", "PAGE_UPLOAD_PO", "PAGE_UPLOAD_SO");
        assertThat(service.page(PageQuery.of(0, 20, null, null), "missing", null).getContent()).isEmpty();
    }

    @Test
    void shouldPageMergedGeneralSettingsAfterSorting() {
        NoRule defaultPageSize = noRule(
                6L,
                SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING,
                "列表分页条数",
                "列表页",
                "50",
                "正常"
        );
        NoRule unknown = noRule(7L, "ZZ_UNKNOWN", "未知设置", null, null, "正常");

        GeneralSettingQueryService service = new GeneralSettingQueryService(
                noRuleRepository(List.of(unknown, defaultPageSize)),
                mapper(),
                () -> List.of(
                        uploadRule(8L, "sales-order", "销售订单", "PAGE_UPLOAD_SALES_ORDER", "销售上传", "{so}", "正常", null, "so.pdf")
                )
        );

        List<GeneralSettingResponse> pageOne = service.page(PageQuery.of(0, 2, null, null), null, " 正常 ").getContent();
        List<GeneralSettingResponse> pageTwo = service.page(PageQuery.of(1, 2, null, null), null, " 正常 ").getContent();
        List<GeneralSettingResponse> emptyPage = service.page(PageQuery.of(2, 2, null, null), null, " 正常 ").getContent();

        assertThat(settingCodes(pageOne)).containsExactly(
                SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING,
                "ZZ_UNKNOWN"
        );
        assertThat(settingCodes(pageTwo)).containsExactly("PAGE_UPLOAD_SALES_ORDER");
        assertThat(emptyPage).isEmpty();
    }

    @Test
    void shouldSortUnknownSettingsByDefaultedBillNameAndSettingCode() {
        NoRule unknownB = noRule(7L, "ZZ_B", "未知设置B", null, null, "正常");
        NoRule unknownA = noRule(8L, "ZZ_A", "未知设置A", null, null, "正常");

        GeneralSettingQueryService service = new GeneralSettingQueryService(
                noRuleRepository(List.of(unknownB, unknownA)),
                mapper(),
                () -> List.of()
        );

        List<GeneralSettingResponse> records = service.page(PageQuery.of(0, 20, null, null), null, null).getContent();

        assertThat(settingCodes(records)).containsExactly("ZZ_A", "ZZ_B");
        assertThat(records).extracting(GeneralSettingResponse::billName).containsOnlyNulls();
    }

    @Test
    void shouldLoadPublicDisplaySwitchesWithoutRedis() {
        NoRule hideAuditedSwitch = noRule(
                5L,
                SystemSwitchService.HIDE_AUDITED_LIST_RECORDS_SWITCH,
                "隐藏已审核单据",
                "列表页",
                "ON",
                "正常"
        );
        NoRule clientOnlySwitch = noRule(
                6L,
                SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING,
                "列表分页条数",
                "列表页",
                "50",
                "正常"
        );
        GeneralSettingQueryService service = new GeneralSettingQueryService(
                noRuleRepository(List.of(clientOnlySwitch, hideAuditedSwitch)),
                mapper(),
                stubUploadRuleService()
        );

        List<String> settingCodes = settingCodes(service.publicDisplaySwitches());

        assertThat(settingCodes).containsExactly(SystemSwitchService.HIDE_AUDITED_LIST_RECORDS_SWITCH);
    }

    @Test
    void shouldLoadPublicDisplayAndClientSettingsThroughSpringCachePath() {
        NoRule showSnowflakeId = noRule(
                5L,
                SystemSwitchService.SHOW_SNOWFLAKE_ID_SWITCH,
                "显示雪花ID",
                "列表页",
                "ON",
                "正常"
        );
        RedisJsonCacheSupport redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);
        GeneralSettingQueryService service = new GeneralSettingQueryService(
                noRuleRepository(List.of(showSnowflakeId)),
                mapper(),
                stubUploadRuleService(),
                redisJsonCacheSupport
        );

        assertThat(settingCodes(service.publicDisplaySwitches()))
                .containsExactly(SystemSwitchService.SHOW_SNOWFLAKE_ID_SWITCH);
        assertThat(settingCodes(service.publicClientSettings()))
                .containsExactly(SystemSwitchService.SHOW_SNOWFLAKE_ID_SWITCH);

    }

    @Test
    void shouldDeclareSpringCacheAnnotationsForPublicSettings() throws Exception {
        Method display = GeneralSettingQueryService.class.getDeclaredMethod("publicDisplaySwitches");
        Cacheable displayCacheable = display.getAnnotation(Cacheable.class);
        assertThat(displayCacheable.value()).containsExactly(CacheConfig.CACHE_STATIC);
        assertThat(displayCacheable.key()).isEqualTo("'" + GeneralSettingQueryService.PUBLIC_DISPLAY_SWITCHES_CACHE_KEY + "'");

        Method client = GeneralSettingQueryService.class.getDeclaredMethod("publicClientSettings");
        Cacheable clientCacheable = client.getAnnotation(Cacheable.class);
        assertThat(clientCacheable.value()).containsExactly(CacheConfig.CACHE_STATIC);
        assertThat(clientCacheable.key()).isEqualTo("'" + GeneralSettingQueryService.PUBLIC_CLIENT_SETTINGS_CACHE_KEY + "'");

        Method evict = GeneralSettingQueryService.class.getDeclaredMethod("evictPublicDisplaySwitchesCache");
        Caching caching = evict.getAnnotation(Caching.class);
        assertThat(caching.evict())
                .extracting(cacheEvict -> cacheEvict.key())
                .containsExactlyInAnyOrder(
                        "'" + GeneralSettingQueryService.PUBLIC_DISPLAY_SWITCHES_CACHE_KEY + "'",
                        "'" + GeneralSettingQueryService.PUBLIC_CLIENT_SETTINGS_CACHE_KEY + "'"
                );
    }

    @Test
    void shouldEvictPublicDisplaySwitchesCacheWhenRedisIsAvailable() {
        RedisJsonCacheSupport redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);
        GeneralSettingQueryService service = new GeneralSettingQueryService(
                noRuleRepository(List.of()),
                mapper(),
                stubUploadRuleService(),
                redisJsonCacheSupport
        );

        service.evictPublicDisplaySwitchesCache();

        verify(redisJsonCacheSupport).delete(List.of(
                GeneralSettingQueryService.PUBLIC_DISPLAY_SWITCHES_CACHE_KEY,
                GeneralSettingQueryService.PUBLIC_CLIENT_SETTINGS_CACHE_KEY
        ));
    }

    @Test
    void shouldIgnoreCacheEvictionWhenRedisIsUnavailable() {
        GeneralSettingQueryService service = new GeneralSettingQueryService(
                noRuleRepository(List.of()),
                mapper(),
                stubUploadRuleService()
        );

        service.evictPublicDisplaySwitchesCache();

        assertThat(service.publicDisplaySwitches()).isEmpty();
    }

    @Test
    void shouldExposeBusinessStatementSwitchesInPublicClientSettings() {
        NoRule customerSwitch = new NoRule();
        customerSwitch.setId(3L);
        customerSwitch.setSettingCode(SystemSwitchService.CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER_SWITCH);
        customerSwitch.setSettingName("客户对账单生成");
        customerSwitch.setBillName("客户对账单");
        customerSwitch.setStatus("正常");

        NoRule supplierSwitch = new NoRule();
        supplierSwitch.setId(4L);
        supplierSwitch.setSettingCode(SystemSwitchService.SUPPLIER_STATEMENT_FULL_PAYMENT_FROM_PURCHASE_SWITCH);
        supplierSwitch.setSettingName("供应商对账单生成");
        supplierSwitch.setBillName("供应商对账单");
        supplierSwitch.setStatus("正常");

        NoRule hideAuditedSwitch = new NoRule();
        hideAuditedSwitch.setId(5L);
        hideAuditedSwitch.setSettingCode("UI_HIDE_AUDITED_LIST_RECORDS");
        hideAuditedSwitch.setSettingName("隐藏已审核单据");
        hideAuditedSwitch.setBillName("列表页");
        hideAuditedSwitch.setStatus("正常");

        NoRule defaultPageSizeSetting = new NoRule();
        defaultPageSizeSetting.setId(6L);
        defaultPageSizeSetting.setSettingCode(SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING);
        defaultPageSizeSetting.setSettingName("列表分页条数");
        defaultPageSizeSetting.setBillName("列表页");
        defaultPageSizeSetting.setSampleNo("50");
        defaultPageSizeSetting.setStatus("正常");

        NoRule snowflakeBusinessNoSwitch = new NoRule();
        snowflakeBusinessNoSwitch.setId(7L);
        snowflakeBusinessNoSwitch.setSettingCode(SystemSwitchService.USE_SNOWFLAKE_ID_AS_BUSINESS_NO_SWITCH);
        snowflakeBusinessNoSwitch.setSettingName("业务单据号使用雪花ID");
        snowflakeBusinessNoSwitch.setBillName("系统开关");
        snowflakeBusinessNoSwitch.setSampleNo("ON");
        snowflakeBusinessNoSwitch.setStatus("正常");

        NoRule defaultTaxRateSetting = new NoRule();
        defaultTaxRateSetting.setId(8L);
        defaultTaxRateSetting.setSettingCode(CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE);
        defaultTaxRateSetting.setSettingName("默认税率");
        defaultTaxRateSetting.setBillName("系统参数");
        defaultTaxRateSetting.setSampleNo("0.13");
        defaultTaxRateSetting.setStatus("正常");

        GeneralSettingQueryService service = new GeneralSettingQueryService(
                noRuleRepository(List.of(
                        customerSwitch,
                        supplierSwitch,
                        hideAuditedSwitch,
                        defaultPageSizeSetting,
                        snowflakeBusinessNoSwitch,
                        defaultTaxRateSetting
                )),
                mapper(),
                stubUploadRuleService()
        );

        Set<String> settingCodes = service.publicClientSettings().stream()
                .map(GeneralSettingResponse::settingCode)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(settingCodes).contains(SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING);
        assertThat(settingCodes).contains(CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE);
        assertThat(settingCodes).contains(SystemSwitchService.USE_SNOWFLAKE_ID_AS_BUSINESS_NO_SWITCH);
        assertThat(settingCodes).contains(
                SystemSwitchService.CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER_SWITCH,
                SystemSwitchService.SUPPLIER_STATEMENT_FULL_PAYMENT_FROM_PURCHASE_SWITCH
        );
        assertThat(settingCodes).doesNotContain(
                SystemSwitchService.HIDE_AUDITED_LIST_RECORDS_SWITCH
        );
    }

    private NoRuleRepository noRuleRepository(List<NoRule> rules) {
        return (NoRuleRepository) Proxy.newProxyInstance(
                NoRuleRepository.class.getClassLoader(),
                new Class[]{NoRuleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> rules;
                    case "findBySettingCodeInAndDeletedFlagFalse" -> {
                        @SuppressWarnings("unchecked")
                        var settingCodes = (Collection<String>) args[0];
                        yield rules.stream()
                                .filter(rule -> settingCodes.contains(rule.getSettingCode()))
                                .toList();
                    }
                    case "toString" -> "NoRuleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private NoRuleMapper mapper() {
        return rule -> new NoRuleResponse(
                rule.getId(),
                rule.getSettingCode(),
                rule.getSettingName(),
                rule.getBillName(),
                rule.getPrefix(),
                rule.getDateRule(),
                rule.getSerialLength(),
                rule.getResetRule(),
                rule.getSampleNo(),
                rule.getStatus(),
                rule.getRemark()
        );
    }

    private PageUploadRuleQueryService stubUploadRuleService() {
        return () -> List.of(
                uploadRule(2L, "sales-order", "销售订单", "PAGE_UPLOAD_SALES_ORDER",
                        "销售订单上传命名规则", "{yyyyMMddHHmmss}_{random8}", "正常",
                        "适用于销售订单页面上传", "upload_sales_order.pdf")
        );
    }

    private NoRule noRule(Long id, String settingCode, String settingName, String billName, String sampleNo, String status) {
        NoRule rule = new NoRule();
        rule.setId(id);
        rule.setSettingCode(settingCode);
        rule.setSettingName(settingName);
        rule.setBillName(billName);
        rule.setSampleNo(sampleNo);
        rule.setStatus(status);
        return rule;
    }

    private PageUploadRuleSummary uploadRule(
            Long id,
            String moduleKey,
            String moduleName,
            String ruleCode,
            String ruleName,
            String renamePattern,
            String status,
            String remark,
            String previewFileName
    ) {
        return new PageUploadRuleSummary(
                id,
                moduleKey,
                moduleName,
                ruleCode,
                ruleName,
                renamePattern,
                status,
                remark,
                previewFileName
        );
    }

    private List<String> settingCodes(List<GeneralSettingResponse> records) {
        return records.stream().map(GeneralSettingResponse::settingCode).toList();
    }
}
