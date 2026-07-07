package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.UploadRule;
import com.leo.erp.attachment.mapper.UploadRuleWebMapper;
import com.leo.erp.attachment.repository.UploadRuleRepository;
import com.leo.erp.attachment.web.dto.UploadRuleRequest;
import com.leo.erp.attachment.web.dto.UploadRuleResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.setting.PageUploadRuleSummary;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
                new ModuleCatalog(),
                Mockito.mock(UploadRuleWebMapper.class)
        );

        PageUploadRuleDetail response = service.getPageUploadRule("sales-order");

        assertThat(response.moduleKey()).isEqualTo("sales-order");
        assertThat(response.moduleName()).isEqualTo("销售订单");
        assertThat(response.ruleCode()).isEqualTo("PAGE_UPLOAD_SALES_ORDER");
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
                new ModuleCatalog(),
                Mockito.mock(UploadRuleWebMapper.class)
        );

        PageUploadRuleDetail updated = service.updatePageUploadRule(
                "customer",
                new UpdatePageUploadRuleCommand("CUSTOM_{originName}", "禁用", "客户页面上传")
        );
        String fileName = service.buildPageUploadFileName("customer", "invoice.pdf", "application/pdf");
        PageUploadRuleDetail salesRule = service.getPageUploadRule("sales-order");

        assertThat(updated.moduleKey()).isEqualTo("customer");
        assertThat(updated.renamePattern()).isEqualTo("CUSTOM_{originName}");
        assertThat(updated.status()).isEqualTo("禁用");
        assertThat(updated.remark()).isEqualTo("客户页面上传");
        assertThat(fileName).isEqualTo("CUSTOM_invoice.pdf");
        assertThat(salesRule.renamePattern()).isEqualTo(UploadRuleService.DEFAULT_RENAME_PATTERN);
    }

    @Test
    void shouldResolveMaterialCategoriesAliasToCanonicalUploadRule() {
        Map<Long, UploadRule> store = new LinkedHashMap<>();
        UploadRule materialCategoryRule = new UploadRule();
        materialCategoryRule.setId(10L);
        materialCategoryRule.setModuleKey("material-category");
        materialCategoryRule.setRuleCode("PAGE_UPLOAD_MATERIAL_CATEGORY");
        materialCategoryRule.setRuleName("商品类别上传命名规则");
        materialCategoryRule.setRenamePattern("CATEGORY_{originName}");
        materialCategoryRule.setStatus("正常");
        store.put(materialCategoryRule.getId(), materialCategoryRule);

        UploadRuleService service = new UploadRuleService(
                uploadRuleRepository(store),
                new FixedIdGenerator(200L),
                new AttachmentFilenameResolver(),
                new ModuleCatalog(),
                Mockito.mock(UploadRuleWebMapper.class)
        );

        PageUploadRuleDetail detail = service.getPageUploadRule("material-categories");
        String fileName = service.buildPageUploadFileName("material-categories", "category.pdf", "application/pdf");

        assertThat(detail.moduleKey()).isEqualTo("material-category");
        assertThat(detail.moduleName()).isEqualTo("商品类别");
        assertThat(detail.renamePattern()).isEqualTo("CATEGORY_{originName}");
        assertThat(fileName).isEqualTo("CATEGORY_category.pdf");
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
                new ModuleCatalog(),
                Mockito.mock(UploadRuleWebMapper.class)
        );

        List<PageUploadRuleSummary> responses = service.listPageUploadRules();

        assertThat(responses).hasSize(new ModuleCatalog().orderedModuleKeys().size());
        assertThat(responses)
                .extracting(PageUploadRuleSummary::moduleKey)
                .contains("material", "sales-order", "general-setting", "role-setting");
    }

    @Test
    void shouldRejectUnknownModuleKey() {
        UploadRuleService service = new UploadRuleService(
                uploadRuleRepository(new LinkedHashMap<>()),
                new FixedIdGenerator(1L),
                new AttachmentFilenameResolver(),
                new ModuleCatalog(),
                Mockito.mock(UploadRuleWebMapper.class)
        );

        assertThatThrownBy(() -> service.getPageUploadRule("unknown-module"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("页面模块不合法");
    }

    @Test
    void shouldMapResponseDetailThroughWebMapper() {
        UploadRuleWebMapper mapper = Mockito.mock(UploadRuleWebMapper.class);
        mapDetailsToResponses(mapper);

        UploadRuleService service = uploadRuleService(new LinkedHashMap<>(), new FixedIdGenerator(1L), mapper);

        UploadRuleResponse response = service.responseDetail(null);

        assertThat(response.moduleKey()).isEqualTo("general-setting");
        assertThat(response.moduleName()).isEqualTo("通用设置");
        assertThat(response.ruleCode()).isEqualTo("PAGE_UPLOAD_GENERAL_SETTING");
        assertThat(response.renamePattern()).isEqualTo(UploadRuleService.DEFAULT_RENAME_PATTERN);
        Mockito.verify(mapper).toResponse(Mockito.any(PageUploadRuleDetail.class));
    }

    @Test
    void shouldMapRequestAndResponseWhenUpdatingThroughWebMapper() {
        Map<Long, UploadRule> store = new LinkedHashMap<>();
        UploadRuleWebMapper mapper = Mockito.mock(UploadRuleWebMapper.class);
        UploadRuleRequest request = new UploadRuleRequest("WEB_{originName}", StatusConstants.NORMAL, "客户上传");
        Mockito.when(mapper.toCommand(request))
                .thenReturn(new UpdatePageUploadRuleCommand("WEB_{originName}", StatusConstants.NORMAL, "客户上传"));
        mapDetailsToResponses(mapper);

        UploadRuleService service = uploadRuleService(store, new FixedIdGenerator(500L), mapper);

        UploadRuleResponse response = service.responseUpdate(" customer ", request);

        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.moduleKey()).isEqualTo("customer");
        assertThat(response.renamePattern()).isEqualTo("WEB_{originName}");
        assertThat(response.status()).isEqualTo(StatusConstants.NORMAL);
        assertThat(store.get(500L).getRemark()).isEqualTo("客户上传");
        Mockito.verify(mapper).toCommand(request);
    }

    @Test
    void shouldReportPageUploadEnabledByStoredStatus() {
        Map<Long, UploadRule> store = new LinkedHashMap<>();
        UploadRule disabledRule = uploadRule(
                10L,
                "customer",
                "PAGE_UPLOAD_CUSTOMER",
                "客户上传命名规则",
                "{originName}",
                StatusConstants.DISABLED,
                null
        );
        UploadRule enabledRule = uploadRule(
                11L,
                "sales-order",
                "PAGE_UPLOAD_SALES_ORDER",
                "销售订单上传命名规则",
                "{originName}",
                StatusConstants.NORMAL,
                null
        );
        store.put(disabledRule.getId(), disabledRule);
        store.put(enabledRule.getId(), enabledRule);

        UploadRuleService service = uploadRuleService(store);

        assertThat(service.isPageUploadEnabled("customer")).isFalse();
        assertThat(service.isPageUploadEnabled("sales-order")).isTrue();
    }

    @Test
    void shouldUseGeneralSettingWhenModuleKeyIsNullOrBlank() {
        UploadRuleService service = uploadRuleService(new LinkedHashMap<>());

        PageUploadRuleDetail nullModuleRule = service.getPageUploadRule(null);
        PageUploadRuleDetail blankModuleRule = service.getPageUploadRule("   ");

        assertThat(nullModuleRule.moduleKey()).isEqualTo("general-setting");
        assertThat(blankModuleRule.moduleKey()).isEqualTo("general-setting");
        assertThat(blankModuleRule.ruleCode()).isEqualTo("PAGE_UPLOAD_GENERAL_SETTING");
        assertThat(blankModuleRule.remark()).isEqualTo("适用于通用设置页面选择文件和剪贴板粘贴上传");
    }

    @Test
    void shouldFallbackToBuiltInDefaultsWhenLegacyTemplateFieldsAreBlankOrNull() {
        Map<Long, UploadRule> blankStore = new LinkedHashMap<>();
        UploadRule blankLegacy = uploadRule(
                1L,
                "legacy-page-upload",
                UploadRuleService.LEGACY_PAGE_UPLOAD_RULE_CODE,
                "页面上传文件命名规则",
                "   ",
                StatusConstants.NORMAL,
                "\t"
        );
        blankStore.put(blankLegacy.getId(), blankLegacy);
        Map<Long, UploadRule> nullStore = new LinkedHashMap<>();
        UploadRule nullLegacy = uploadRule(
                2L,
                "legacy-page-upload",
                UploadRuleService.LEGACY_PAGE_UPLOAD_RULE_CODE,
                "页面上传文件命名规则",
                null,
                StatusConstants.NORMAL,
                null
        );
        nullStore.put(nullLegacy.getId(), nullLegacy);

        PageUploadRuleDetail blankDetail = uploadRuleService(blankStore).getPageUploadRule("sales-order");
        PageUploadRuleDetail nullDetail = uploadRuleService(nullStore).getPageUploadRule("sales-order");

        assertThat(blankDetail.renamePattern()).isEqualTo(UploadRuleService.DEFAULT_RENAME_PATTERN);
        assertThat(blankDetail.remark()).isEqualTo("适用于销售订单页面选择文件和剪贴板粘贴上传");
        assertThat(nullDetail.renamePattern()).isEqualTo(UploadRuleService.DEFAULT_RENAME_PATTERN);
        assertThat(nullDetail.remark()).isEqualTo("适用于销售订单页面选择文件和剪贴板粘贴上传");
    }

    @Test
    void shouldFallbackToBuiltInDefaultsWhenLegacyTemplateHasMixedNullAndBlankFields() {
        Map<Long, UploadRule> store = new LinkedHashMap<>();
        UploadRule legacy = uploadRule(
                1L,
                "legacy-page-upload",
                UploadRuleService.LEGACY_PAGE_UPLOAD_RULE_CODE,
                "页面上传文件命名规则",
                null,
                StatusConstants.NORMAL,
                "   "
        );
        store.put(legacy.getId(), legacy);

        PageUploadRuleDetail detail = uploadRuleService(store).getPageUploadRule("sales-order");

        assertThat(detail.renamePattern()).isEqualTo(UploadRuleService.DEFAULT_RENAME_PATTERN);
        assertThat(detail.remark()).isEqualTo("适用于销售订单页面选择文件和剪贴板粘贴上传");
    }

    @Test
    void shouldRejectInvalidStatusBeforeSaving() {
        Map<Long, UploadRule> store = new LinkedHashMap<>();
        UploadRuleService service = uploadRuleService(
                store,
                new FixedIdGenerator(700L),
                Mockito.mock(UploadRuleWebMapper.class)
        );

        assertThatThrownBy(() -> service.updatePageUploadRule(
                "customer",
                new UpdatePageUploadRuleCommand("{originName}", "停用", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传规则状态不合法");
        assertThat(store).isEmpty();
    }

    private UploadRuleService uploadRuleService(Map<Long, UploadRule> store) {
        return uploadRuleService(store, new FixedIdGenerator(1L), Mockito.mock(UploadRuleWebMapper.class));
    }

    private UploadRuleService uploadRuleService(Map<Long, UploadRule> store,
                                                SnowflakeIdGenerator idGenerator,
                                                UploadRuleWebMapper mapper) {
        return new UploadRuleService(
                uploadRuleRepository(store),
                idGenerator,
                new AttachmentFilenameResolver(),
                new ModuleCatalog(),
                mapper
        );
    }

    private void mapDetailsToResponses(UploadRuleWebMapper mapper) {
        Mockito.when(mapper.toResponse(Mockito.any(PageUploadRuleDetail.class)))
                .thenAnswer(invocation -> {
                    PageUploadRuleDetail detail = invocation.getArgument(0);
                    return toResponse(detail);
                });
    }

    private UploadRuleResponse toResponse(PageUploadRuleDetail detail) {
        return new UploadRuleResponse(
                detail.id(),
                detail.moduleKey(),
                detail.moduleName(),
                detail.ruleCode(),
                detail.ruleName(),
                detail.renamePattern(),
                detail.status(),
                detail.remark(),
                detail.previewFileName()
        );
    }

    private UploadRule uploadRule(long id,
                                  String moduleKey,
                                  String ruleCode,
                                  String ruleName,
                                  String renamePattern,
                                  String status,
                                  String remark) {
        UploadRule rule = new UploadRule();
        rule.setId(id);
        rule.setModuleKey(moduleKey);
        rule.setRuleCode(ruleCode);
        rule.setRuleName(ruleName);
        rule.setRenamePattern(renamePattern);
        rule.setStatus(status);
        rule.setRemark(remark);
        return rule;
    }

    private UploadRuleRepository uploadRuleRepository(Map<Long, UploadRule> store) {
        return (UploadRuleRepository) Proxy.newProxyInstance(
                UploadRuleRepository.class.getClassLoader(),
                new Class[]{UploadRuleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRuleCodeAndDeletedFlagFalse" -> store.values().stream()
                            .filter(rule -> !rule.isDeletedFlag())
                            .filter(rule -> ((String) args[0]).equals(rule.getRuleCode()))
                            .findFirst();
                    case "findByModuleKeyAndDeletedFlagFalse" -> store.values().stream()
                            .filter(rule -> !rule.isDeletedFlag())
                            .filter(rule -> ((String) args[0]).equals(rule.getModuleKey()))
                            .findFirst();
                    case "findAllByDeletedFlagFalseOrderByIdAsc" -> store.values().stream()
                            .filter(rule -> !rule.isDeletedFlag())
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
