package com.leo.erp.system.norule.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.mapper.NoRuleMapper;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.leo.erp.system.norule.web.dto.NoRuleRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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
}
