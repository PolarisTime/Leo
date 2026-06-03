package com.leo.erp.system.norule.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.mapper.NoRuleMapper;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.leo.erp.system.norule.web.dto.NoRuleRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NoRuleServiceTest {

    @Test
    void shouldAcceptValidDetailedOperationLogActions() throws Exception {
        NoRuleService service = buildService();

        assertThatCode(() -> invokeValidateUpdate(service, request("QUERY,DETAIL,CREATE")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectEmptyDetailedOperationLogActions() throws Exception {
        NoRuleService service = buildService();

        assertThatThrownBy(() -> invokeValidateUpdate(service, request(" ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要勾选一个记录动作");
    }

    @Test
    void shouldRejectUnsupportedDetailedOperationLogActions() throws Exception {
        NoRuleService service = buildService();

        assertThatThrownBy(() -> invokeValidateUpdate(service, request("QUERY,ON")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的记录动作");
    }

    @Test
    void shouldRejectDuplicateSettingCodeOnCreate() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        when(repository.existsBySettingCodeAndDeletedFlagFalse("RULE_TEST")).thenReturn(true);
        NoRuleService service = new NoRuleService(
                repository, new SnowflakeIdGenerator(1), mock(NoRuleMapper.class),
                mock(NoRuleSequenceService.class), mock(SystemSwitchService.class),
                mock(PreallocatedBusinessNoService.class), null
        );

        assertThatThrownBy(() -> service.create(new NoRuleRequest(
                "RULE_TEST", "测试规则", "测试单据", "TEST", "yyyy", 4, "YEARLY", "TEST0001", "正常", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("设置编码已存在");
    }

    @Test
    void shouldCreateNoRule() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        when(repository.existsBySettingCodeAndDeletedFlagFalse("RULE_TEST")).thenReturn(false);
        when(repository.save(any(NoRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NoRuleMapper noRuleMapper = mock(NoRuleMapper.class);
        when(noRuleMapper.toResponse(any(NoRule.class))).thenAnswer(invocation -> {
            NoRule entity = invocation.getArgument(0);
            return new com.leo.erp.system.norule.web.dto.NoRuleResponse(
                    1L, entity.getSettingCode(), entity.getSettingName(), entity.getBillName(),
                    entity.getPrefix(), entity.getDateRule(), entity.getSerialLength(),
                    entity.getResetRule(), entity.getSampleNo(), entity.getStatus(), entity.getRemark()
            );
        });
        NoRuleService service = new NoRuleService(
                repository, new SnowflakeIdGenerator(1), noRuleMapper,
                mock(NoRuleSequenceService.class), mock(SystemSwitchService.class),
                mock(PreallocatedBusinessNoService.class), null
        );

        var result = service.create(new NoRuleRequest(
                "RULE_TEST", "测试规则", "测试单据", "TEST", "yyyy", 4, "YEARLY", "TEST0001", "正常", null
        ));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldUpdateNoRule() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        NoRule entity = new NoRule();
        entity.setSettingCode("RULE_TEST");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(NoRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NoRuleMapper noRuleMapper = mock(NoRuleMapper.class);
        when(noRuleMapper.toResponse(any(NoRule.class))).thenAnswer(invocation -> {
            NoRule e = invocation.getArgument(0);
            return new com.leo.erp.system.norule.web.dto.NoRuleResponse(
                    1L, e.getSettingCode(), e.getSettingName(), e.getBillName(),
                    e.getPrefix(), e.getDateRule(), e.getSerialLength(),
                    e.getResetRule(), e.getSampleNo(), e.getStatus(), e.getRemark()
            );
        });
        NoRuleService service = new NoRuleService(
                repository, new SnowflakeIdGenerator(1), noRuleMapper,
                mock(NoRuleSequenceService.class), mock(SystemSwitchService.class),
                mock(PreallocatedBusinessNoService.class), null
        );

        var result = service.update(1L, new NoRuleRequest(
                "RULE_TEST", "测试规则", "测试单据", "TEST", "yyyy", 4, "YEARLY", "TEST0001", "正常", null
        ));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldRejectUpdateWhenNotFound() {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());
        NoRuleService service = new NoRuleService(
                repository, new SnowflakeIdGenerator(1), mock(NoRuleMapper.class),
                mock(NoRuleSequenceService.class), mock(SystemSwitchService.class),
                mock(PreallocatedBusinessNoService.class), null
        );

        assertThatThrownBy(() -> service.update(1L, new NoRuleRequest(
                "RULE_TEST", "测试规则", "测试单据", "TEST", "yyyy", 4, "YEARLY", "TEST0001", "正常", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("单号规则不存在");
    }

    @Test
    void shouldValidateSettingCodeUniquenessOnUpdateWhenChanged() throws Exception {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        when(repository.existsBySettingCodeAndDeletedFlagFalse("RULE_NEW")).thenReturn(true);
        NoRuleService service = new NoRuleService(
                repository, new SnowflakeIdGenerator(1), mock(NoRuleMapper.class),
                mock(NoRuleSequenceService.class), mock(SystemSwitchService.class),
                mock(PreallocatedBusinessNoService.class), null
        );

        NoRule entity = new NoRule();
        entity.setSettingCode("RULE_OLD");

        assertThatThrownBy(() -> invokeValidateUpdate(service, entity, new NoRuleRequest(
                "RULE_NEW", "测试规则", "测试单据", "TEST", "yyyy", 4, "YEARLY", "TEST0001", "正常", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("设置编码已存在");
    }

    @Test
    void shouldNotValidateSettingCodeUniquenessOnUpdateWhenUnchanged() throws Exception {
        NoRuleRepository repository = mock(NoRuleRepository.class);
        NoRuleService service = new NoRuleService(
                repository, new SnowflakeIdGenerator(1), mock(NoRuleMapper.class),
                mock(NoRuleSequenceService.class), mock(SystemSwitchService.class),
                mock(PreallocatedBusinessNoService.class), null
        );

        NoRule entity = new NoRule();
        entity.setSettingCode("RULE_TEST");

        assertThatCode(() -> invokeValidateUpdate(service, entity, new NoRuleRequest(
                "RULE_TEST", "测试规则", "测试单据", "TEST", "yyyy", 4, "YEARLY", "TEST0001", "正常", null
        ))).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectRuleTemplateWithoutSeq() throws Exception {
        NoRuleSequenceService sequenceService = mock(NoRuleSequenceService.class);
        when(sequenceService.usesMagicVariables("{prefix}{date}{seq}")).thenReturn(true);
        when(sequenceService.containsSequenceToken("{prefix}{date}{seq}")).thenReturn(false);
        NoRuleService service = new NoRuleService(
                mock(NoRuleRepository.class), new SnowflakeIdGenerator(1), mock(NoRuleMapper.class),
                sequenceService, mock(SystemSwitchService.class),
                mock(PreallocatedBusinessNoService.class), null
        );

        assertThatThrownBy(() -> invokeValidateCreate(service, new NoRuleRequest(
                "RULE_TEST", "测试规则", "测试单据", "{prefix}{date}{seq}", "yyyy", 4, "YEARLY", "TEST0001", "正常", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("单号规则模板必须包含 {seq} 变量");
    }

    @Test
    void shouldAcceptRuleTemplateWithSeq() throws Exception {
        NoRuleSequenceService sequenceService = mock(NoRuleSequenceService.class);
        when(sequenceService.usesMagicVariables("{prefix}{date}{seq}")).thenReturn(true);
        when(sequenceService.containsSequenceToken("{prefix}{date}{seq}")).thenReturn(true);
        NoRuleRepository repository = mock(NoRuleRepository.class);
        when(repository.existsBySettingCodeAndDeletedFlagFalse("RULE_TEST")).thenReturn(false);
        NoRuleService service = new NoRuleService(
                repository, new SnowflakeIdGenerator(1), mock(NoRuleMapper.class),
                sequenceService, mock(SystemSwitchService.class),
                mock(PreallocatedBusinessNoService.class), null
        );

        assertThatCode(() -> invokeValidateCreate(service, new NoRuleRequest(
                "RULE_TEST", "测试规则", "测试单据", "{prefix}{date}{seq}", "yyyy", 4, "YEARLY", "TEST0001", "正常", null
        ))).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptNonRulePrefixSettingCode() throws Exception {
        NoRuleService service = buildService();

        assertThatCode(() -> invokeValidateCreate(service, new NoRuleRequest(
                "SYS_TEST", "测试设置", "测试单据", "TEST", "yyyy", 4, "YEARLY", "TEST0001", "正常", null
        ))).doesNotThrowAnyException();
    }

    @Test
    void shouldNextNumberWithSnowflakeId() {
        SystemSwitchService systemSwitchService = mock(SystemSwitchService.class);
        when(systemSwitchService.shouldUseSnowflakeIdAsBusinessNo()).thenReturn(true);
        PreallocatedBusinessNoService preallocatedBusinessNoService = mock(PreallocatedBusinessNoService.class);
        NoRuleService service = new NoRuleService(
                mock(NoRuleRepository.class), new SnowflakeIdGenerator(1), mock(NoRuleMapper.class),
                mock(NoRuleSequenceService.class), systemSwitchService,
                preallocatedBusinessNoService, null
        );

        var result = service.nextNumber("TEST", null);
        assertThat(result).isNotNull();
        assertThat(result.moduleKey()).isEqualTo("TEST");
    }

    @Test
    void shouldNextNumberWithSequence() {
        SystemSwitchService systemSwitchService = mock(SystemSwitchService.class);
        when(systemSwitchService.shouldUseSnowflakeIdAsBusinessNo()).thenReturn(false);
        NoRuleSequenceService noRuleSequenceService = mock(NoRuleSequenceService.class);
        when(noRuleSequenceService.nextValueByModuleKey("TEST")).thenReturn("TEST0001");
        NoRuleService service = new NoRuleService(
                mock(NoRuleRepository.class), new SnowflakeIdGenerator(1), mock(NoRuleMapper.class),
                noRuleSequenceService, systemSwitchService,
                mock(PreallocatedBusinessNoService.class), null
        );

        var result = service.nextNumber("TEST", null);
        assertThat(result).isNotNull();
        assertThat(result.moduleKey()).isEqualTo("TEST");
        assertThat(result.generatedNo()).isEqualTo("TEST0001");
    }

    @Test
    void shouldReturnStatementGeneratorRules() {
        SystemSwitchService systemSwitchService = mock(SystemSwitchService.class);
        when(systemSwitchService.shouldDefaultCustomerStatementReceiptAmountToZero()).thenReturn(true);
        when(systemSwitchService.shouldDefaultSupplierStatementToFullPayment()).thenReturn(false);
        NoRuleService service = new NoRuleService(
                mock(NoRuleRepository.class), new SnowflakeIdGenerator(1), mock(NoRuleMapper.class),
                mock(NoRuleSequenceService.class), systemSwitchService,
                mock(PreallocatedBusinessNoService.class), null
        );

        var result = service.statementGeneratorRules();
        assertThat(result).isNotNull();
    }

    private static NoRuleService buildService() {
        return new NoRuleService(
                mock(NoRuleRepository.class),
                null,
                mock(NoRuleMapper.class),
                mock(NoRuleSequenceService.class),
                mock(SystemSwitchService.class),
                mock(PreallocatedBusinessNoService.class),
                null
        );
    }

    private static NoRuleRequest request(String sampleNo) {
        return new NoRuleRequest(
                SystemSwitchService.OPERATION_LOG_DETAILED_PAGE_ACTIONS_SWITCH,
                "页面操作详细日志",
                "操作日志",
                "SYS",
                "yyyy",
                1,
                "YEARLY",
                sampleNo,
                "正常",
                "remark"
        );
    }

    private static void invokeValidateUpdate(NoRuleService service, NoRuleRequest request) throws Exception {
        Method method = NoRuleService.class.getDeclaredMethod("validateUpdate", NoRule.class, NoRuleRequest.class);
        method.setAccessible(true);
        try {
            NoRule entity = new NoRule();
            entity.setSettingCode(request.settingCode());
            method.invoke(service, entity, request);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw ex;
        }
    }

    private static void invokeValidateUpdate(NoRuleService service, NoRule entity, NoRuleRequest request) throws Exception {
        Method method = NoRuleService.class.getDeclaredMethod("validateUpdate", NoRule.class, NoRuleRequest.class);
        method.setAccessible(true);
        try {
            method.invoke(service, entity, request);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw ex;
        }
    }

    private static void invokeValidateCreate(NoRuleService service, NoRuleRequest request) throws Exception {
        Method method = NoRuleService.class.getDeclaredMethod("validateCreate", NoRuleRequest.class);
        method.setAccessible(true);
        try {
            method.invoke(service, request);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw ex;
        }
    }
}
