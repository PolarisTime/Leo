package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.persistence.AuditableEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataScopeContextTest {

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    void shouldAllowAllScopeToAccessAnyEntity() {
        DataScopeContext.set(1L, "material", "all");
        TestEntity entity = new TestEntity();
        entity.setCreatedBy(2L);

        assertThatCode(() -> DataScopeContext.assertCanAccess(entity)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectSelfScopeWhenEntityBelongsToAnotherUser() {
        DataScopeContext.set(1L, "material", "self");
        TestEntity entity = new TestEntity();
        entity.setCreatedBy(2L);

        assertThatThrownBy(() -> DataScopeContext.assertCanAccess(entity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    @Test
    void shouldAllowDepartmentScopeWhenEntityBelongsToSameDepartmentUser() {
        DataScopeContext.set(1L, "material", "department", Set.of(1L, 2L));
        TestEntity entity = new TestEntity();
        entity.setCreatedBy(2L);

        assertThatCode(() -> DataScopeContext.assertCanAccess(entity)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDepartmentScopeWhenEntityBelongsToAnotherDepartment() {
        DataScopeContext.set(1L, "material", "department", Set.of(1L, 3L));
        TestEntity entity = new TestEntity();
        entity.setCreatedBy(2L);

        assertThatThrownBy(() -> DataScopeContext.assertCanAccess(entity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    private static class TestEntity extends AuditableEntity {
    }
}
