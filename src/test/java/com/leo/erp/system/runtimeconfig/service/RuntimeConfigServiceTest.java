package com.leo.erp.system.runtimeconfig.service;

import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.leo.erp.system.norule.service.SystemSwitchService;
import com.leo.erp.system.runtimeconfig.feature.FeatureFlagService;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeConfigResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeConfigServiceTest {

    private final NoRuleRepository repository = mock(NoRuleRepository.class);
    private final FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
    private final RuntimeConfigService service = new RuntimeConfigService(repository, featureFlagService);

    @Test
    void returnsStrongRuntimeConfigFromGeneralSettings() {
        when(repository.findBySettingCodeInAndDeletedFlagFalse(anyCollection())).thenAnswer(invocation -> {
            Collection<String> codes = invocation.getArgument(0);
            return List.of(
                    rule(SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING, "正常", "50"),
                    rule(SystemSwitchService.SHOW_SNOWFLAKE_ID_SWITCH, "正常", "ON"),
                    rule(SystemSwitchService.UI_WATERMARK_ENABLED_SWITCH, "正常", "ON"),
                    rule(RuntimeConfigService.WATERMARK_CONTENT_SETTING, "正常", "{username} {time}"),
                    rule(RuntimeConfigService.WATERMARK_FONT_SIZE_SETTING, "正常", "24"),
                    rule(RuntimeConfigService.WATERMARK_ROTATE_SETTING, "正常", "-18"),
                    rule(RuntimeConfigService.WATERMARK_COLOR_SETTING, "正常", "rgba(1,2,3,0.1)"),
                    rule(RuntimeConfigService.WATERMARK_DENSITY_SETTING, "正常", "160"),
                    rule(CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE, "正常", "0.09"),
                    rule(SystemSwitchService.CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER_SWITCH, "正常", "ON"),
                    rule(SystemSwitchService.SUPPLIER_STATEMENT_FULL_PAYMENT_FROM_PURCHASE_SWITCH, "禁用", "ON"),
                    rule(SystemSwitchService.USE_SNOWFLAKE_ID_AS_BUSINESS_NO_SWITCH, "正常", "ON"),
                    rule(RuntimeConfigService.WEIGHT_ONLY_PURCHASE_INBOUNDS_SETTING, "正常", "ON"),
                    rule(RuntimeConfigService.WEIGHT_ONLY_SALES_OUTBOUNDS_SETTING, "禁用", "ON")
            ).stream()
                    .filter(item -> codes.contains(item.getSettingCode()))
                    .toList();
        });
        when(featureFlagService.isEnabled(RuntimeConfigService.WEIGHT_ONLY_PURCHASE_INBOUNDS_FEATURE, true))
                .thenReturn(false);
        when(featureFlagService.isEnabled(RuntimeConfigService.WEIGHT_ONLY_SALES_OUTBOUNDS_FEATURE, false))
                .thenReturn(true);
        when(featureFlagService.isEnabled(RuntimeConfigService.SHOW_SNOWFLAKE_ID_FEATURE, true))
                .thenReturn(false);
        when(featureFlagService.isEnabled(RuntimeConfigService.UI_WATERMARK_ENABLED_FEATURE, true))
                .thenReturn(false);

        RuntimeConfigResponse response = service.getRuntimeConfig();

        assertThat(response.ui().defaultPageSize()).isEqualTo(50);
        assertThat(response.ui().showSnowflakeId()).isFalse();
        assertThat(response.ui().watermark().enabled()).isFalse();
        assertThat(response.ui().watermark().content()).isEqualTo("{username} {time}");
        assertThat(response.ui().watermark().fontSize()).isEqualTo(24);
        assertThat(response.ui().watermark().rotate()).isEqualTo(-18);
        assertThat(response.ui().watermark().color()).isEqualTo("rgba(1,2,3,0.1)");
        assertThat(response.ui().watermark().density()).isEqualTo(160);
        assertThat(response.business().defaultTaxRate()).isEqualByComparingTo(new BigDecimal("0.09"));
        assertThat(response.business().statement().customerReceiptAmountZero()).isTrue();
        assertThat(response.business().statement().supplierFullPayment()).isFalse();
        assertThat(response.business().businessNo().useSnowflakeId()).isTrue();
        assertThat(response.features().weightOnlyPurchaseInbound()).isFalse();
        assertThat(response.features().weightOnlySalesOutbound()).isTrue();
        verify(featureFlagService).isEnabled(RuntimeConfigService.WEIGHT_ONLY_PURCHASE_INBOUNDS_FEATURE, true);
        verify(featureFlagService).isEnabled(RuntimeConfigService.WEIGHT_ONLY_SALES_OUTBOUNDS_FEATURE, false);
        verify(featureFlagService).isEnabled(RuntimeConfigService.SHOW_SNOWFLAKE_ID_FEATURE, true);
        verify(featureFlagService).isEnabled(RuntimeConfigService.UI_WATERMARK_ENABLED_FEATURE, true);
    }

    @Test
    void fallsBackWhenSettingsAreMissingDisabledOrInvalid() {
        when(repository.findBySettingCodeInAndDeletedFlagFalse(anyCollection())).thenReturn(List.of(
                rule(SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING, "正常", "300"),
                rule(RuntimeConfigService.WATERMARK_FONT_SIZE_SETTING, "正常", "bad"),
                rule(RuntimeConfigService.WATERMARK_ROTATE_SETTING, "禁用", "0"),
                rule(RuntimeConfigService.WATERMARK_DENSITY_SETTING, "正常", "-1"),
                rule(CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE, "正常", "not-decimal"),
                rule(SystemSwitchService.SHOW_SNOWFLAKE_ID_SWITCH, "禁用", "ON")
        ));

        RuntimeConfigResponse response = service.getRuntimeConfig();

        assertThat(response.ui().defaultPageSize()).isEqualTo(20);
        assertThat(response.ui().showSnowflakeId()).isFalse();
        assertThat(response.ui().watermark().enabled()).isFalse();
        assertThat(response.ui().watermark().content()).isEqualTo("{username}  {time}");
        assertThat(response.ui().watermark().fontSize()).isEqualTo(18);
        assertThat(response.ui().watermark().rotate()).isEqualTo(-22);
        assertThat(response.ui().watermark().color()).isEqualTo("rgba(0,0,0,0.08)");
        assertThat(response.ui().watermark().density()).isEqualTo(200);
        assertThat(response.business().defaultTaxRate()).isEqualByComparingTo(new BigDecimal("0.13"));
        assertThat(response.business().statement().customerReceiptAmountZero()).isFalse();
        assertThat(response.business().statement().supplierFullPayment()).isFalse();
        assertThat(response.business().businessNo().useSnowflakeId()).isFalse();
        assertThat(response.features().weightOnlyPurchaseInbound()).isFalse();
        assertThat(response.features().weightOnlySalesOutbound()).isFalse();
    }

    private NoRule rule(String settingCode, String status, String sampleNo) {
        NoRule rule = new NoRule();
        rule.setSettingCode(settingCode);
        rule.setStatus(status);
        rule.setSampleNo(sampleNo);
        return rule;
    }
}
