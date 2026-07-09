package com.leo.erp.common.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrudStatusGuardTest {

    private final CrudStatusGuard guard = new CrudStatusGuard();

    @Test
    void shouldResolveTrimmedStatus() {
        TestStatusEntity entity = new TestStatusEntity();
        entity.setStatus(" 草稿 ");

        Optional<String> status = guard.resolveStatus(entity);

        assertThat(status).contains("草稿");
    }

    @Test
    void shouldReturnEmptyWhenStatusValueIsBlank() {
        TestStatusEntity entity = new TestStatusEntity();
        entity.setStatus(" ");

        assertThat(guard.resolveStatus(entity)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenNoStatusGetter() {
        assertThat(guard.resolveStatus(new Object())).isEmpty();
    }

    @Test
    void shouldWrapStatusGetterFailure() {
        assertThatThrownBy(() -> guard.resolveStatus(new ThrowingStatusEntity()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("读取单据状态失败");
    }

    @Test
    void shouldRejectBlankStatus() {
        assertThatThrownBy(() -> guard.normalizeRequiredStatus(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");
        assertThatThrownBy(() -> guard.normalizeRequiredStatus(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");
    }

    @Test
    void shouldNormalizeRequiredStatusByTrimming() {
        assertThat(guard.normalizeRequiredStatus(" 草稿 ")).isEqualTo("草稿");
    }

    @Test
    void shouldRejectEditingProtectedStatus() {
        TestStatusEntity entity = new TestStatusEntity();
        entity.setStatus(StatusConstants.CONFIRMED);

        assertThatThrownBy(() -> guard.assertEditAllowed(entity, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能编辑");
    }

    @Test
    void shouldAllowEditingProtectedStatusWhenExplicitlyAllowed() {
        TestStatusEntity entity = new TestStatusEntity();
        entity.setStatus(StatusConstants.CONFIRMED);

        guard.assertEditAllowed(entity, true);
    }

    @Test
    void shouldRejectDeletingProtectedStatus() {
        TestStatusEntity entity = new TestStatusEntity();
        entity.setStatus(StatusConstants.AUDITED);

        assertThatThrownBy(() -> guard.assertDeleteAllowed(entity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能删除");
    }

    @Test
    void shouldRejectDirectFinalStatusWrite() {
        TestStatusEntity entity = new TestStatusEntity();
        entity.setStatus(StatusConstants.COMPLETED);

        assertThatThrownBy(() -> guard.assertRequestDidNotWriteFinalStatus(entity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("完成态状态必须通过专用状态接口变更");
    }

    @Test
    void shouldSkipRequestTransitionWhenAllowedTransitionsAreEmpty() {
        TestStatusEntity entity = new TestStatusEntity();
        entity.setStatus(StatusConstants.AUDITED);

        guard.assertRequestStatusTransitionAllowed(entity, Optional.of(StatusConstants.DRAFT), Set.of());
    }

    @Test
    void shouldSkipRequestTransitionWhenCurrentStatusMissing() {
        TestStatusEntity entity = new TestStatusEntity();
        entity.setStatus(StatusConstants.AUDITED);

        guard.assertRequestStatusTransitionAllowed(entity, Optional.empty(), Set.of("草稿->已审核"));
    }

    @Test
    void shouldSkipRequestTransitionWhenNextStatusMissing() {
        TestStatusEntity entity = new TestStatusEntity();
        entity.setStatus(" ");

        guard.assertRequestStatusTransitionAllowed(entity, Optional.of(StatusConstants.DRAFT), Set.of("草稿->已审核"));
    }

    @Test
    void shouldSkipRequestTransitionWhenStatusDoesNotChange() {
        TestStatusEntity entity = new TestStatusEntity();
        entity.setStatus(StatusConstants.DRAFT);

        guard.assertRequestStatusTransitionAllowed(entity, Optional.of(StatusConstants.DRAFT), Set.of("草稿->已审核"));
    }

    @Test
    void shouldValidateRequestStatusTransitionWhenStatusChanges() {
        TestStatusEntity entity = new TestStatusEntity();
        entity.setStatus(StatusConstants.AUDITED);

        guard.assertRequestStatusTransitionAllowed(entity, Optional.of(StatusConstants.DRAFT), Set.of("草稿->已审核"));
    }

    @Test
    void shouldAllowConfiguredTransition() {
        guard.validateStatusTransition(Set.of("草稿->已审核"), "草稿", "已审核");
    }

    @Test
    void shouldRejectUnconfiguredTransition() {
        assertThatThrownBy(() -> guard.validateStatusTransition(Set.of("草稿->已审核"), "草稿", "已完成"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从");
    }

    @Test
    void shouldWriteStatus() {
        TestStatusEntity entity = new TestStatusEntity();

        guard.writeStatus(entity, "已审核");

        assertThat(entity.getStatus()).isEqualTo("已审核");
    }

    @Test
    void shouldRejectStatusWriteWhenSetterMissing() {
        assertThatThrownBy(() -> guard.writeStatus(new Object(), "已审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前模块不支持状态变更");
    }

    @Test
    void shouldWrapStatusSetterFailure() {
        assertThatThrownBy(() -> guard.writeStatus(new ThrowingSetterEntity(), "已审核"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("写入单据状态失败");
    }

    private static class TestStatusEntity {
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    private static class ThrowingStatusEntity {
        public String getStatus() {
            throw new IllegalStateException("boom");
        }
    }

    private static class ThrowingSetterEntity {
        public void setStatus(String status) {
            throw new IllegalStateException("boom");
        }
    }
}
