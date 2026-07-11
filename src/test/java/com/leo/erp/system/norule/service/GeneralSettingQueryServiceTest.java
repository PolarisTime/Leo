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
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    void shouldOrderPurchaseRefundRulesBetweenInboundAndSalesOrderRules() {
        NoRule purchaseInbound = noRule(1L, "RULE_PI", "采购入库编号规则", "采购入库", "PI2026000001", "正常");
        NoRule purchaseRefund = noRule(2L, "RULE_PR", "采购退款单编号规则", "采购退款单", "PR2026000001", "正常");
        NoRule supplierRefundReceipt = noRule(
                3L,
                "RULE_SRR",
                "供应商退款到账单编号规则",
                "供应商退款到账单",
                "SRR2026000001",
                "正常"
        );
        NoRule salesOrder = noRule(4L, "RULE_SO", "销售订单编号规则", "销售订单", "SO2026000001", "正常");
        GeneralSettingQueryService service = new GeneralSettingQueryService(
                noRuleRepository(List.of(salesOrder, supplierRefundReceipt, purchaseRefund, purchaseInbound)),
                mapper(),
                () -> List.of()
        );

        List<GeneralSettingResponse> records = service
                .page(PageQuery.of(0, 20, null, null), null, null)
                .getContent();

        assertThat(settingCodes(records)).containsExactly("RULE_PI", "RULE_PR", "RULE_SRR", "RULE_SO");
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
