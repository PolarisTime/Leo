package com.leo.erp.common.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractCrudServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void detailShouldThrowWhenEntityNotFound() {
        TestCrudService service = new TestCrudService();

        assertThatThrownBy(() -> service.detail(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteShouldThrowWhenEntityNotFound() {
        TestCrudService service = new TestCrudService();

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void nextIdShouldGenerateSnowflakeId() {
        TestCrudService service = new TestCrudService();

        long id1 = service.nextId();
        long id2 = service.nextId();

        assertThat(id1).isPositive();
        assertThat(id2).isPositive();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void updateShouldThrowWhenEntityNotFound() {
        TestCrudService service = new TestCrudService();

        assertThatThrownBy(() -> service.update(99L, "req"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateStatusShouldThrowWhenEntityNotFound() {
        TestCrudService service = new TestCrudService();

        assertThatThrownBy(() -> service.updateStatus(99L, "ACTIVE"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateStatusShouldThrowWhenStatusEmpty() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, "DRAFT");

        assertThatThrownBy(() -> service.updateStatus(1L, "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");
    }

    @Test
    void updateStatusShouldReturnSameWhenStatusUnchanged() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, "DRAFT");

        var result = service.updateStatus(1L, "DRAFT");

        assertThat(result).isEqualTo("response");
    }

    @Test
    void updateStatusShouldThrowWhenNoTransitionsConfigured() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, "DRAFT");

        assertThatThrownBy(() -> service.updateStatus(1L, "ACTIVE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持状态变更");
    }

    @Test
    void updateStatusShouldThrowWhenTransitionNotAllowed() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, "DRAFT");
        service.setAllowedTransitions(Set.of("DRAFT->SUBMITTED"));

        assertThatThrownBy(() -> service.updateStatus(1L, "ACTIVE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从");
    }

    @Test
    void updateStatusShouldSucceedWhenTransitionAllowed() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, "DRAFT");
        service.setAllowedTransitions(Set.of("DRAFT->ACTIVE"));

        var result = service.updateStatus(1L, "ACTIVE");

        assertThat(result).isEqualTo("response");
    }

    @Test
    void deleteShouldThrowWhenStatusProtected() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, StatusConstants.AUDITED);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能删除");
    }

    @Test
    void deleteShouldThrowWhenStatusConfirmed() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, StatusConstants.CONFIRMED);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能删除");
    }

    @Test
    void updateShouldThrowWhenStatusProtected() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, StatusConstants.COMPLETED);

        assertThatThrownBy(() -> service.update(1L, "req"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能编辑");
    }

    @Test
    void updateShouldThrowWhenStatusConfirmed() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, StatusConstants.CONFIRMED);

        assertThatThrownBy(() -> service.update(1L, "req"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能编辑");
    }

    @Test
    void createShouldAssignIdAndSave() {
        TestCrudService service = new TestCrudService();

        var result = service.create("request");

        assertThat(result).isEqualTo("response");
    }

    @Test
    void detailShouldReturnResponseWhenEntityFound() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, "DRAFT");

        var result = service.detail(1L);

        assertThat(result).isEqualTo("response");
    }

    @Test
    void deleteShouldMarkDeletedWhenStatusNotProtected() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, "DRAFT");

        service.delete(1L);

        assertThat(service.getEntity(1L).isDeletedFlag()).isTrue();
    }

    @Test
    void updateShouldSucceedWhenStatusNotProtected() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, "DRAFT");

        var result = service.update(1L, "updated");

        assertThat(result).isEqualTo("response");
    }

    @Test
    void createShouldUsePreallocatedIdFromHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        setupAdminPrincipal();

        TestCrudService service = new TestCrudService();

        var result = service.create("request");
        assertThat(result).isEqualTo("response");
    }

    @Test
    void createShouldConsumePreallocatedIdAfterSuccessfulCreate() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Business-Module-Key", "sales-order");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        setupAdminPrincipal();
        BusinessPreallocationService preallocatedBusinessNoService = mock(BusinessPreallocationService.class);
        TestCrudService service = new TestCrudService();
        service.setPreallocatedBusinessNoServiceForTest(preallocatedBusinessNoService);

        var result = service.create("request");

        assertThat(result).isEqualTo("response");
        verify(preallocatedBusinessNoService).assertReservedByPrincipal(
                org.mockito.ArgumentMatchers.eq("sales-order"),
                org.mockito.ArgumentMatchers.eq(123456789L),
                org.mockito.ArgumentMatchers.any(SecurityPrincipal.class)
        );
        verify(preallocatedBusinessNoService).consume("sales-order", 123456789L);
    }

    @Test
    void createShouldNotConsumePreallocatedIdWhenSaveFails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Business-Module-Key", "sales-order");
        request.addHeader("X-Preallocated-Id", "123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        setupAdminPrincipal();
        BusinessPreallocationService preallocatedBusinessNoService = mock(BusinessPreallocationService.class);
        TestCrudService service = new TestCrudService();
        service.setPreallocatedBusinessNoServiceForTest(preallocatedBusinessNoService);
        service.failOnSave();

        assertThatThrownBy(() -> service.create("request"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("save failed");
        verify(preallocatedBusinessNoService).assertReservedByPrincipal(
                org.mockito.ArgumentMatchers.eq("sales-order"),
                org.mockito.ArgumentMatchers.eq(123456789L),
                org.mockito.ArgumentMatchers.any(SecurityPrincipal.class)
        );
        verify(preallocatedBusinessNoService, never()).consume("sales-order", 123456789L);
    }

    @Test
    void nextBusinessNoShouldThrowWhenNoRuleSequenceServiceNull() {
        TestCrudService service = new TestCrudService();

        assertThatThrownBy(() -> service.publicNextBusinessNo("module"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("编号规则服务不可用");
    }

    @Test
    void resolveCreateBusinessNoShouldThrowWhenNoServiceAndNoRequestedNo() {
        TestCrudService service = new TestCrudService();

        assertThatThrownBy(() -> service.publicResolveCreateBusinessNo("module", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("编号规则服务不可用");
    }

    @Test
    void resolveCreateBusinessNoShouldUseRequestedNoWhenNoService() {
        TestCrudService service = new TestCrudService();

        String result = service.publicResolveCreateBusinessNo("module", "BIZ-001");

        assertThat(result).isEqualTo("BIZ-001");
    }

    @Test
    void resolveCreateBusinessNoShouldUseSnowflakeIdWhenConfigured() {
        TestCrudService service = new TestCrudService();
        service.enableSnowflakeIdAsBusinessNo();
        service.setCrudRuntimeSettingsForTest(mock(CrudRuntimeSettings.class,
                (invocation) -> {
                    if (invocation.getMethod().getName().equals("shouldUseSnowflakeIdAsBusinessNo")) return true;
                    return null;
                }));

        String result = service.publicResolveCreateBusinessNo("module", null, 12345L);

        assertThat(result).isEqualTo("12345");
    }

    @Test
    void resolveCreateBusinessNoShouldThrowWhenSnowflakeIdInvalid() {
        TestCrudService service = new TestCrudService();
        service.enableSnowflakeIdAsBusinessNo();
        service.setCrudRuntimeSettingsForTest(mock(CrudRuntimeSettings.class,
                (invocation) -> {
                    if (invocation.getMethod().getName().equals("shouldUseSnowflakeIdAsBusinessNo")) return true;
                    return null;
                }));

        assertThatThrownBy(() -> service.publicResolveCreateBusinessNo("module", null, 0L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("雪花ID尚未分配");
    }

    @Test
    void createShouldSkipPreallocatedIdWhenNoHeader() {
        TestCrudService service = new TestCrudService();

        var result = service.create("request");
        assertThat(result).isEqualTo("response");
    }

    @Test
    void createShouldSkipModuleKeyResolutionWhenNoPreallocatedId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        TestCrudService service = new TestCrudService();
        var result = service.create("request");
        assertThat(result).isEqualTo("response");
    }

    @Test
    void shouldNotApplyDataScopeByDefault() {
        TestCrudService service = new TestCrudService();
        assertThat(service.shouldApplyDataScope()).isFalse();
    }

    @Test
    void markDeletedShouldSetDeletedStatusWhenAdminViewEnabled() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, "ACTIVE");
        service.enableAdminViewDeleted();

        service.delete(1L);

        assertThat(service.getEntity(1L).isDeletedFlag()).isTrue();
        assertThat(service.getEntity(1L).getStatus()).isEqualTo(StatusConstants.DELETED);
    }

    @Test
    void updateShouldSucceedForDraftStatus() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, "DRAFT");

        var result = service.update(1L, "updated");
        assertThat(result).isEqualTo("response");
    }

    @Test
    void detailShouldUseActiveEntityWhenAdminViewNotEnabled() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, "DRAFT");

        var result = service.detail(1L);
        assertThat(result).isEqualTo("response");
    }

    @Test
    void detailShouldThrowWhenEntityDeletedAndAdminViewNotEnabled() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, "DRAFT");
        service.getEntity(1L).setDeletedFlag(true);

        assertThatThrownBy(() -> service.detail(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void detailShouldUseVisibleEntityWhenAdminViewEnabled() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, "DRAFT");
        service.enableAdminViewDeleted();
        service.setCrudRuntimeSettingsForTest(mock(CrudRuntimeSettings.class,
                (invocation) -> {
                    if (invocation.getMethod().getName().equals("shouldAdminSeeDeletedRecords")) return true;
                    return null;
                }));
        setupAdminPrincipal();

        var result = service.detail(1L);
        assertThat(result).isEqualTo("response");
    }

    @Test
    void currentUserIsAdminShouldReturnFalseWhenNoAuth() {
        SecurityContextHolder.clearContext();
        TestCrudService service = new TestCrudService();
        service.enableAdminViewDeleted();
        service.setCrudRuntimeSettingsForTest(mock(CrudRuntimeSettings.class,
                (invocation) -> {
                    if (invocation.getMethod().getName().equals("shouldAdminSeeDeletedRecords")) return true;
                    return null;
                }));
        service.addEntity(1L, "DRAFT");

        var result = service.detail(1L);
        assertThat(result).isEqualTo("response");
    }

    @Test
    void shouldNotApplyListVisibilityWhenSwitchServiceNull() {
        TestCrudService service = new TestCrudService();
        service.addEntity(1L, "DRAFT");

        var result = service.detail(1L);
        assertThat(result).isEqualTo("response");
    }

    @Test
    void resolveCreateBusinessNoShouldCallNextBusinessNoWhenServiceAvailable() {
        TestCrudService service = new TestCrudService();
        var noRuleService = mock(BusinessNumberAllocator.class);
        when(noRuleService.nextValueByModuleKey("test")).thenReturn("NO-001");
        service.setNoRuleSequenceServiceForTest(noRuleService);

        String result = service.publicResolveCreateBusinessNo("test", null);
        assertThat(result).isEqualTo("NO-001");
    }

    @Test
    void createShouldUseReservedBusinessNoFromRequest() {
        setupAdminPrincipal();
        TestCrudService service = new TestCrudService();
        service.useBusinessNoAsRequest();
        var noRuleService = mock(BusinessNumberAllocator.class);
        service.setNoRuleSequenceServiceForTest(noRuleService);
        BusinessPreallocationService preallocationService = mock(BusinessPreallocationService.class);
        when(preallocationService.isBusinessNoReservedByPrincipal(
                org.mockito.ArgumentMatchers.eq("test"),
                org.mockito.ArgumentMatchers.eq("NO-001"),
                org.mockito.ArgumentMatchers.any(SecurityPrincipal.class)
        )).thenReturn(true);
        service.setPreallocatedBusinessNoServiceForTest(preallocationService);

        service.create("NO-001");

        assertThat(service.lastAppliedRequest()).isEqualTo("NO-001");
        verify(noRuleService, never()).nextValueByModuleKey("test");
        verify(preallocationService).consumeBusinessNo("test", "NO-001");
    }

    @Test
    void resolveCreateBusinessNoShouldThrowWhenServiceReturnsBlank() {
        TestCrudService service = new TestCrudService();
        var noRuleService = mock(BusinessNumberAllocator.class);
        when(noRuleService.nextValueByModuleKey("test")).thenReturn("  ");
        service.setNoRuleSequenceServiceForTest(noRuleService);

        assertThatThrownBy(() -> service.publicResolveCreateBusinessNo("test", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模块未配置编号规则");
    }

    @Test
    void resolveCreateBusinessNoShouldThrowWhenServiceReturnsNull() {
        TestCrudService service = new TestCrudService();
        var noRuleService = mock(BusinessNumberAllocator.class);
        when(noRuleService.nextValueByModuleKey("test")).thenReturn(null);
        service.setNoRuleSequenceServiceForTest(noRuleService);

        assertThatThrownBy(() -> service.publicResolveCreateBusinessNo("test", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模块未配置编号规则");
    }

    private void setupAdminPrincipal() {
        var principal = new com.leo.erp.security.support.SecurityPrincipal(
                1L, "admin", "", true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static class TestEntity extends AbstractAuditableEntity {
        private Long id;
        private String status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    private static class TestCrudService extends AbstractCrudService<TestEntity, String, String> {

        private final java.util.Map<Long, TestEntity> store = new java.util.HashMap<>();
        private Set<String> transitions = Set.of();
        private boolean useSnowflakeIdAsBusinessNo = false;
        private boolean adminViewDeleted = false;
        private boolean failOnSave = false;
        private boolean useBusinessNoAsRequest = false;
        private String lastAppliedRequest;

        TestCrudService() {
            super(new SnowflakeIdGenerator(0L));
        }

        void addEntity(Long id, String status) {
            TestEntity entity = new TestEntity();
            entity.setId(id);
            entity.setStatus(status);
            store.put(id, entity);
        }

        TestEntity getEntity(Long id) {
            return store.get(id);
        }

        void setAllowedTransitions(Set<String> transitions) {
            this.transitions = transitions;
        }

        void enableSnowflakeIdAsBusinessNo() {
            this.useSnowflakeIdAsBusinessNo = true;
        }

        void setCrudRuntimeSettingsForTest(CrudRuntimeSettings service) {
            setCrudRuntimeSettings(service);
        }

        void setNoRuleSequenceServiceForTest(BusinessNumberAllocator service) {
            setBusinessNumberAllocator(service);
        }

        void setPreallocatedBusinessNoServiceForTest(BusinessPreallocationService service) {
            setBusinessPreallocationService(service);
        }

        void enableAdminViewDeleted() {
            this.adminViewDeleted = true;
        }

        void failOnSave() {
            this.failOnSave = true;
        }

        void useBusinessNoAsRequest() {
            this.useBusinessNoAsRequest = true;
        }

        String lastAppliedRequest() {
            return lastAppliedRequest;
        }

        @Override
        protected String normalizeCreateRequest(String request, long entityId) {
            return useBusinessNoAsRequest
                    ? resolveCreateBusinessNo("test", request, entityId)
                    : super.normalizeCreateRequest(request, entityId);
        }

        @Override
        protected TestEntity newEntity() {
            return new TestEntity();
        }

        @Override
        protected void assignId(TestEntity entity, Long id) {
            entity.setId(id);
        }

        @Override
        protected Optional<TestEntity> findActiveEntity(Long id) {
            TestEntity entity = store.get(id);
            if (entity != null && !entity.isDeletedFlag()) {
                return Optional.of(entity);
            }
            return Optional.empty();
        }

        @Override
        protected String notFoundMessage() {
            return "Not found";
        }

        @Override
        protected void apply(TestEntity entity, String request) {
            lastAppliedRequest = request;
        }

        @Override
        protected TestEntity saveEntity(TestEntity entity) {
            if (failOnSave) {
                throw new IllegalStateException("save failed");
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        protected String toResponse(TestEntity entity) {
            return "response";
        }

        @Override
        protected Set<String> allowedStatusTransitions() {
            return transitions;
        }

        @Override
        protected boolean shouldApplyDataScope() {
            return false;
        }

        @Override
        protected boolean allowAdminViewDeletedRecords() {
            return adminViewDeleted;
        }

        String publicNextBusinessNo(String moduleKey) {
            return nextBusinessNo(moduleKey);
        }

        String publicResolveCreateBusinessNo(String moduleKey, String requestedNo) {
            return resolveCreateBusinessNo(moduleKey, requestedNo);
        }

        String publicResolveCreateBusinessNo(String moduleKey, String requestedNo, Long entityId) {
            return resolveCreateBusinessNo(moduleKey, requestedNo, entityId);
        }
    }
}
