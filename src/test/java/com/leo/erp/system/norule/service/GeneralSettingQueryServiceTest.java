package com.leo.erp.system.norule.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.setting.PageUploadRuleQueryService;
import com.leo.erp.common.setting.PageUploadRuleSummary;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.leo.erp.system.norule.mapper.NoRuleMapper;
import com.leo.erp.system.norule.web.dto.GeneralSettingResponse;
import com.leo.erp.system.norule.web.dto.NoRuleResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
                        org.assertj.core.groups.Tuple.tuple("UPLOAD_RULE", "sales-orders", "PAGE_UPLOAD_SALES_ORDERS")
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
                                "sales-orders",
                                "销售订单",
                                "PAGE_UPLOAD_SALES_ORDERS",
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

    private NoRuleRepository noRuleRepository(List<NoRule> rules) {
        return (NoRuleRepository) Proxy.newProxyInstance(
                NoRuleRepository.class.getClassLoader(),
                new Class[]{NoRuleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> rules;
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
                new PageUploadRuleSummary(
                        2L,
                        "sales-orders",
                        "销售订单",
                        "PAGE_UPLOAD_SALES_ORDERS",
                        "销售订单上传命名规则",
                        "{yyyyMMddHHmmss}_{random8}",
                        "正常",
                        "适用于销售订单页面上传",
                        "upload_sales_order.pdf"
                )
        );
    }
}
