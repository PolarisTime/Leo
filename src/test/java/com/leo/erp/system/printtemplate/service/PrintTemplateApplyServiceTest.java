package com.leo.erp.system.printtemplate.service;

import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PrintTemplateApplyService 状态写入极端情况测试。
 * <p>
 * 覆盖 ACTIVE/DISABLED 写入、isDisabled 三分支与 null 防御缺口（生产代码不修改，测试锁定行为）。
 */
class PrintTemplateApplyServiceTest {

    private PrintTemplateApplyService service;
    private PrintTemplate template;

    @BeforeEach
    void setUp() {
        service = new PrintTemplateApplyService();
        template = new PrintTemplate();
    }

    @Test
    void activate_shouldSetActiveStatus() {
        template.setStatus(PrintTemplateApplyService.STATUS_DISABLED);

        service.activate(template);

        assertThat(template.getStatus()).isEqualTo(PrintTemplateApplyService.STATUS_ACTIVE);
    }

    @Test
    void disable_shouldSetDisabledStatus() {
        template.setStatus(PrintTemplateApplyService.STATUS_ACTIVE);

        service.disable(template);

        assertThat(template.getStatus()).isEqualTo(PrintTemplateApplyService.STATUS_DISABLED);
    }

    @Test
    void isDisabled_shouldReturnTrueWhenDisabled() {
        template.setStatus(PrintTemplateApplyService.STATUS_DISABLED);

        assertThat(service.isDisabled(template)).isTrue();
    }

    @Test
    void isDisabled_shouldReturnFalseWhenActive() {
        template.setStatus(PrintTemplateApplyService.STATUS_ACTIVE);

        assertThat(service.isDisabled(template)).isFalse();
    }

    @Test
    void isDisabled_shouldReturnFalseWhenStatusNull() {
        template.setStatus(null);

        assertThat(service.isDisabled(template)).isFalse();
    }

    // 防御缺口：activate(null) 直接 NPE，生产代码未做入参校验。测试锁定行为，不修改生产代码。
    @Test
    void activate_shouldThrowNpeWhenTemplateNull() {
        assertThatThrownBy(() -> service.activate(null)).isInstanceOf(NullPointerException.class);
    }
}
