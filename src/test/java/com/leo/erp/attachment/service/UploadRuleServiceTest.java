package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.UploadRule;
import com.leo.erp.attachment.repository.UploadRuleRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.setting.PageUploadRuleSummary;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadRuleServiceTest {

    @Test
    void shouldCreateModuleSpecificRuleFromLegacyTemplate() {
        Map<Long, UploadRule> store = new LinkedHashMap<>();
        UploadRule legacy = new UploadRule();
        legacy.setId(1L);
        legacy.setModuleKey("legacy-page-upload");
        legacy.setRuleCode(UploadRuleService.LEGACY_PAGE_UPLOAD_RULE_CODE);
        legacy.setRuleName("页面上传文件命名规则");
        legacy.setRenamePattern("LEGACY_{originName}");
        legacy.setStatus("正常");
        legacy.setRemark("全局旧规则");
        store.put(legacy.getId(), legacy);

        UploadRuleService service = new UploadRuleService(
                uploadRuleRepository(store),
                new FixedIdGenerator(100L),
                new AttachmentFilenameResolver(),
                new ModuleCatalog()
        );

        PageUploadRuleDetail response = service.getPageUploadRule("sales-orders");

        assertThat(response.moduleKey()).isEqualTo("sales-orders");
        assertThat(response.moduleName()).isEqualTo("销售订单");
        assertThat(response.ruleCode()).isEqualTo("PAGE_UPLOAD_SALES_ORDERS");
        assertThat(response.renamePattern()).isEqualTo("LEGACY_{originName}");
        assertThat(response.remark()).isEqualTo("全局旧规则");
        assertThat(store.values())
                .extracting(UploadRule::getModuleKey)
                .containsExactly("legacy-page-upload");
    }

    @Test
    void shouldUpdateAndApplyRuleByModuleKey() {
        Map<Long, UploadRule> store = new LinkedHashMap<>();
        UploadRule legacy = new UploadRule();
        legacy.setId(1L);
        legacy.setModuleKey("legacy-page-upload");
        legacy.setRuleCode(UploadRuleService.LEGACY_PAGE_UPLOAD_RULE_CODE);
        legacy.setRuleName("页面上传文件命名规则");
        legacy.setRenamePattern(UploadRuleService.DEFAULT_RENAME_PATTERN);
        legacy.setStatus("正常");
        store.put(legacy.getId(), legacy);

        UploadRuleService service = new UploadRuleService(
                uploadRuleRepository(store),
                new SequenceIdGenerator(200L, 201L, 202L),
                new AttachmentFilenameResolver(),
                new ModuleCatalog()
        );

        PageUploadRuleDetail updated = service.updatePageUploadRule(
                "customers",
                new UpdatePageUploadRuleCommand("CUSTOM_{originName}", "禁用", "客户页面上传")
        );
        String fileName = service.buildPageUploadFileName("customers", "invoice.pdf", "application/pdf");
        PageUploadRuleDetail salesRule = service.getPageUploadRule("sales-orders");

        assertThat(updated.moduleKey()).isEqualTo("customers");
        assertThat(updated.renamePattern()).isEqualTo("CUSTOM_{originName}");
        assertThat(updated.status()).isEqualTo("禁用");
        assertThat(updated.remark()).isEqualTo("客户页面上传");
        assertThat(fileName).isEqualTo("CUSTOM_invoice.pdf");
        assertThat(salesRule.renamePattern()).isEqualTo(UploadRuleService.DEFAULT_RENAME_PATTERN);
    }

    @Test
    void shouldListSupportedModuleRules() {
        Map<Long, UploadRule> store = new LinkedHashMap<>();
        UploadRule legacy = new UploadRule();
        legacy.setId(1L);
        legacy.setModuleKey("legacy-page-upload");
        legacy.setRuleCode(UploadRuleService.LEGACY_PAGE_UPLOAD_RULE_CODE);
        legacy.setRuleName("页面上传文件命名规则");
        legacy.setRenamePattern(UploadRuleService.DEFAULT_RENAME_PATTERN);
        legacy.setStatus("正常");
        store.put(legacy.getId(), legacy);

        UploadRuleService service = new UploadRuleService(
                uploadRuleRepository(store),
                new SequenceIdGenerator(300L, 301L, 302L, 303L, 304L, 305L, 306L, 307L, 308L, 309L,
                        310L, 311L, 312L, 313L, 314L, 315L, 316L, 317L, 318L, 319L, 320L, 321L, 322L),
                new AttachmentFilenameResolver(),
                new ModuleCatalog()
        );

        List<PageUploadRuleSummary> responses = service.listPageUploadRules();

        assertThat(responses).hasSize(new ModuleCatalog().orderedModuleKeys().size());
        assertThat(responses)
                .extracting(PageUploadRuleSummary::moduleKey)
                .contains("materials", "sales-orders", "general-settings", "role-settings");
    }

    @Test
    void shouldRejectUnknownModuleKey() {
        UploadRuleService service = new UploadRuleService(
                uploadRuleRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(1L),
                new AttachmentFilenameResolver(),
                new ModuleCatalog()
        );

        assertThatThrownBy(() -> service.getPageUploadRule("unknown-module"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("页面模块不合法");
    }

    private UploadRuleRepository uploadRuleRepository(Map<Long, UploadRule> store) {
        return (UploadRuleRepository) Proxy.newProxyInstance(
                UploadRuleRepository.class.getClassLoader(),
                new Class[]{UploadRuleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRuleCodeAndDeletedFlagFalse" -> store.values().stream()
                            .filter(rule -> !Boolean.TRUE.equals(rule.getDeletedFlag()))
                            .filter(rule -> ((String) args[0]).equals(rule.getRuleCode()))
                            .findFirst();
                    case "findByModuleKeyAndDeletedFlagFalse" -> store.values().stream()
                            .filter(rule -> !Boolean.TRUE.equals(rule.getDeletedFlag()))
                            .filter(rule -> ((String) args[0]).equals(rule.getModuleKey()))
                            .findFirst();
                    case "findAllByDeletedFlagFalseOrderByIdAsc" -> store.values().stream()
                            .filter(rule -> !Boolean.TRUE.equals(rule.getDeletedFlag()))
                            .sorted(java.util.Comparator.comparing(UploadRule::getId))
                            .toList();
                    case "save" -> {
                        UploadRule entity = (UploadRule) args[0];
                        store.put(entity.getId(), entity);
                        yield entity;
                    }
                    case "toString" -> "UploadRuleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static final class FixedIdGenerator extends SnowflakeIdGenerator {

        private final long id;

        private FixedIdGenerator(long id) {
            this.id = id;
        }

        @Override
        public synchronized long nextId() {
            return id;
        }
    }

    private static final class SequenceIdGenerator extends SnowflakeIdGenerator {

        private final long[] ids;
        private int index;

        private SequenceIdGenerator(long... ids) {
            this.ids = ids;
        }

        @Override
        public synchronized long nextId() {
            return ids[index++];
        }
    }
}
