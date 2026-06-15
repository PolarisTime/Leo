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
    void shouldReturnEmptyWhenNoStatusGetter() {
        assertThat(guard.resolveStatus(new Object())).isEmpty();
    }

    @Test
    void shouldRejectBlankStatus() {
        assertThatThrownBy(() -> guard.normalizeRequiredStatus(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");
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
    void shouldMarkDeletedStatusWhenEnabled() {
        TestStatusEntity entity = new TestStatusEntity();
        entity.setStatus("草稿");

        guard.markDeletedStatus(entity, true);

        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DELETED);
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
}
