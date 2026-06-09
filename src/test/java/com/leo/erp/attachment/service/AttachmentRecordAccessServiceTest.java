package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.AttachmentBinding;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentBindingRepository;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.common.support.BusinessEntityRegistrar;
import com.leo.erp.common.support.BusinessRecordEntityCatalog;
import com.leo.erp.security.jwt.ApiKeyAuthenticationDetails;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttachmentRecordAccessServiceTest {

    private EntityManager entityManager;
    private PermissionService permissionService;
    private AttachmentBindingRepository attachmentBindingRepository;
    private AttachmentFileRepository attachmentFileRepository;
    private AttachmentRecordAccessService service;

    @BeforeEach
    void setUp() {
        var registrar = new BusinessEntityRegistrar();
        registrar.register("purchase-order", TestBusinessEntity.class);
        BusinessRecordEntityCatalog.setRegistrar(registrar);

        entityManager = mock(EntityManager.class);
        when(entityManager.find(any(), anyLong())).thenReturn(createTestEntity());

        permissionService = mock(PermissionService.class);
        when(permissionService.can(anyLong(), anyString(), anyString())).thenReturn(true);
        when(permissionService.getUserDataScope(anyLong(), anyString(), anyString())).thenReturn("all");
        when(permissionService.getDataScopeOwnerUserIds(anyLong(), anyString())).thenReturn(Set.of());

        attachmentBindingRepository = mock(AttachmentBindingRepository.class);
        when(attachmentBindingRepository.findByModuleKeyAndAttachmentIdAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc(
                anyString(), anyLong())).thenReturn(List.of(createBinding()));
        when(attachmentBindingRepository.findByAttachmentIdAndDeletedFlagFalseOrderByModuleKeyAscRecordIdAscSortOrderAscIdAsc(
                anyLong())).thenReturn(List.of(createBinding()));

        attachmentFileRepository = mock(AttachmentFileRepository.class);

        service = new AttachmentRecordAccessService(entityManager, permissionService, attachmentBindingRepository, attachmentFileRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        DataScopeContext.clear();
        BusinessRecordEntityCatalog.setRegistrar(null);
    }

    @Test
    void shouldThrowException_whenModuleKeyNull() {
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        assertThatThrownBy(() -> service.assertRecordAccessible(principal, null, "read", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少模块标识");
    }

    @Test
    void shouldThrowException_whenModuleKeyBlank() {
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        assertThatThrownBy(() -> service.assertRecordAccessible(principal, "  ", "read", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少模块标识");
    }

    @Test
    void shouldPass_whenModuleKeyNotRegistered() {
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        service.assertRecordAccessible(principal, "unknown-module", "read", 1L);
    }

    @Test
    void shouldThrowException_whenRecordIdNull() {
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        assertThatThrownBy(() -> service.assertRecordAccessible(principal, "purchase-order", "read", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少业务记录标识");
    }

    @Test
    void shouldThrowException_whenRecordIdInvalid() {
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        assertThatThrownBy(() -> service.assertRecordAccessible(principal, "purchase-order", "read", 0L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少业务记录标识");
    }

    @Test
    void shouldThrowException_whenRecordIdNegative() {
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        assertThatThrownBy(() -> service.assertRecordAccessible(principal, "purchase-order", "read", -5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少业务记录标识");
    }

    @Test
    void shouldPass_whenEntityExists_andUserHasAllDataAccess() {
        when(permissionService.getUserDataScope(eq(1L), anyString(), eq("read"))).thenReturn("all");
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        service.assertRecordAccessible(principal, "purchase-order", "read", 1L);
    }

    @Test
    void shouldPass_whenEntityExists_andUserIsOwner() {
        TestBusinessEntity entity = createTestEntity();
        entity.setCreatedBy(1L);
        when(entityManager.find(any(), anyLong())).thenReturn(entity);
        when(permissionService.getUserDataScope(eq(1L), anyString(), eq("read"))).thenReturn("self");
        when(permissionService.getDataScopeOwnerUserIds(eq(1L), eq("self"))).thenReturn(Set.of(1L));

        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        service.assertRecordAccessible(principal, "purchase-order", "read", 1L);
    }

    @Test
    void shouldThrowException_whenEntityExists_butUserHasNoAccess() {
        TestBusinessEntity entity = createTestEntity();
        entity.setCreatedBy(999L);
        when(entityManager.find(any(), anyLong())).thenReturn(entity);
        when(permissionService.getUserDataScope(eq(1L), anyString(), eq("read"))).thenReturn("self");
        when(permissionService.getDataScopeOwnerUserIds(eq(1L), eq("self"))).thenReturn(Set.of(1L));

        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        assertThatThrownBy(() -> service.assertRecordAccessible(principal, "purchase-order", "read", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    @Test
    void shouldThrowException_whenEntityNotFound() {
        when(entityManager.find(any(), anyLong())).thenReturn(null);
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        assertThatThrownBy(() -> service.assertRecordAccessible(principal, "purchase-order", "read", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("业务记录不存在");
    }

    @Test
    void shouldThrowException_whenEntityDeleted() {
        TestBusinessEntity entity = createTestEntity();
        entity.setDeletedFlag(true);
        when(entityManager.find(any(), anyLong())).thenReturn(entity);
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        assertThatThrownBy(() -> service.assertRecordAccessible(principal, "purchase-order", "read", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("业务记录不存在");
    }

    @Test
    void shouldPass_assertRecordAccessible_whenNonBusinessModule() {
        var registrar = new BusinessEntityRegistrar();
        BusinessRecordEntityCatalog.setRegistrar(registrar);

        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        service.assertRecordAccessible(principal, "purchase-order", "read", 1L);
    }

    @Test
    void shouldThrowException_whenAttachmentBindingsEmpty() {
        var repo = mock(AttachmentBindingRepository.class);
        when(repo.findByModuleKeyAndAttachmentIdAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc(
                anyString(), anyLong())).thenReturn(List.of());
        var svc = new AttachmentRecordAccessService(entityManager, permissionService, repo, attachmentFileRepository);
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        assertThatThrownBy(() -> svc.assertAttachmentAccessible(principal, "purchase-order", "read", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件未绑定到当前业务记录");
    }

    @Test
    void shouldPass_assertAttachmentAccessible_whenUserHasAllDataAccess() {
        when(permissionService.getUserDataScope(eq(1L), anyString(), eq("read"))).thenReturn("all");
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        service.assertAttachmentAccessible(principal, "purchase-order", "read", 1L);
    }

    @Test
    void shouldRejectAttachmentAccessWhenUserLacksRealModulePermission() {
        when(permissionService.can(eq(1L), eq("purchase-order"), eq("read"))).thenReturn(false);
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        assertThatThrownBy(() -> service.assertAttachmentAccessible(principal, "read", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    @Test
    void shouldRejectAttachmentAccessWhenApiKeyLacksRealModulePermission() {
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        auth.setDetails(new ApiKeyAuthenticationDetails(null, List.of("sales-order"), List.of("read")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatThrownBy(() -> service.assertAttachmentAccessible(principal, "read", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    @Test
    void shouldRejectForgedModuleKey_whenAttachmentBoundToInaccessibleRealRecord() {
        TestBusinessEntity entity = createTestEntity();
        entity.setCreatedBy(999L);
        when(entityManager.find(any(), anyLong())).thenReturn(entity);
        when(permissionService.getUserDataScope(eq(1L), anyString(), eq("read"))).thenReturn("self");
        when(permissionService.getDataScopeOwnerUserIds(eq(1L), eq("self"))).thenReturn(Set.of(1L));
        when(attachmentBindingRepository.findByAttachmentIdAndDeletedFlagFalseOrderByModuleKeyAscRecordIdAscSortOrderAscIdAsc(
                eq(1L))).thenReturn(List.of(createBinding(1L)));
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        assertThatThrownBy(() -> service.assertAttachmentAccessible(principal, "read", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    @Test
    void shouldAllowUnboundAttachmentOnlyForUploader() {
        AttachmentFile attachment = new AttachmentFile();
        attachment.setId(1L);
        attachment.setCreatedBy(1L);
        when(attachmentBindingRepository.findByAttachmentIdAndDeletedFlagFalseOrderByModuleKeyAscRecordIdAscSortOrderAscIdAsc(
                eq(1L))).thenReturn(List.of());
        when(attachmentFileRepository.findByIdAndDeletedFlagFalse(eq(1L))).thenReturn(java.util.Optional.of(attachment));
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        service.assertAttachmentAccessible(principal, "read", 1L);
    }

    @Test
    void shouldRejectUnboundAttachmentUploadedByOtherUser() {
        AttachmentFile attachment = new AttachmentFile();
        attachment.setId(1L);
        attachment.setCreatedBy(2L);
        when(attachmentBindingRepository.findByAttachmentIdAndDeletedFlagFalseOrderByModuleKeyAscRecordIdAscSortOrderAscIdAsc(
                eq(1L))).thenReturn(List.of());
        when(attachmentFileRepository.findByIdAndDeletedFlagFalse(eq(1L))).thenReturn(java.util.Optional.of(attachment));
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        assertThatThrownBy(() -> service.assertAttachmentAccessible(principal, "read", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无附件访问权限");
    }

    @Test
    void shouldPass_assertAttachmentAccessible_whenUserIsOwner() {
        TestBusinessEntity entity = createTestEntity();
        entity.setCreatedBy(1L);
        when(entityManager.find(any(), anyLong())).thenReturn(entity);
        when(permissionService.getUserDataScope(eq(1L), anyString(), eq("read"))).thenReturn("self");
        when(permissionService.getDataScopeOwnerUserIds(eq(1L), eq("self"))).thenReturn(Set.of(1L));

        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        service.assertAttachmentAccessible(principal, "purchase-order", "read", 1L);
    }

    @Test
    void shouldThrowException_assertAttachmentAccessible_whenNoAccess() {
        TestBusinessEntity entity = createTestEntity();
        entity.setCreatedBy(999L);
        when(entityManager.find(any(), anyLong())).thenReturn(entity);
        when(permissionService.getUserDataScope(eq(1L), anyString(), eq("read"))).thenReturn("self");
        when(permissionService.getDataScopeOwnerUserIds(eq(1L), eq("self"))).thenReturn(Set.of(1L));

        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        assertThatThrownBy(() -> service.assertAttachmentAccessible(principal, "purchase-order", "read", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    @Test
    void shouldPass_assertAttachmentAccessible_whenNonBusinessModule() {
        var registrar = new BusinessEntityRegistrar();
        BusinessRecordEntityCatalog.setRegistrar(registrar);

        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        service.assertAttachmentAccessible(principal, "purchase-order", "read", 1L);
    }

    @Test
    void shouldPass_assertAttachmentAccessible_whenMultipleBindings_oneAccessible() {
        TestBusinessEntity entity1 = createTestEntity();
        entity1.setCreatedBy(999L);
        TestBusinessEntity entity2 = createTestEntity();
        entity2.setCreatedBy(1L);

        when(entityManager.find(any(), anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(1);
            return id == 1L ? entity1 : entity2;
        });
        when(attachmentBindingRepository.findByModuleKeyAndAttachmentIdAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc(
                anyString(), anyLong())).thenReturn(List.of(createBinding(1L), createBinding(2L)));
        when(permissionService.getUserDataScope(eq(1L), anyString(), eq("read"))).thenReturn("self");
        when(permissionService.getDataScopeOwnerUserIds(eq(1L), eq("self"))).thenReturn(Set.of(1L));

        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        service.assertAttachmentAccessible(principal, "purchase-order", "read", 1L);
    }

    @Test
    void shouldThrowException_assertAttachmentAccessible_whenAllBindingsDeleted() {
        TestBusinessEntity deletedEntity = createTestEntity();
        deletedEntity.setDeletedFlag(true);
        when(entityManager.find(any(), anyLong())).thenReturn(deletedEntity);
        when(permissionService.getUserDataScope(eq(1L), anyString(), eq("read"))).thenReturn("self");
        when(permissionService.getDataScopeOwnerUserIds(eq(1L), eq("self"))).thenReturn(Set.of(1L));

        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        assertThatThrownBy(() -> service.assertAttachmentAccessible(principal, "purchase-order", "read", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    @Test
    void shouldPass_assertRecordAccessible_withNormalizedModuleKey() {
        when(permissionService.getUserDataScope(eq(1L), anyString(), eq("read"))).thenReturn("all");
        var principal = new SecurityPrincipal(1L, "admin", "admin", true, List.of());

        service.assertRecordAccessible(principal, "purchase-order", "read", 1L);
    }

    private static TestBusinessEntity createTestEntity() {
        return new TestBusinessEntity();
    }

    private static AttachmentBinding createBinding() {
        return createBinding(1L);
    }

    private static AttachmentBinding createBinding(Long recordId) {
        var binding = new AttachmentBinding();
        binding.setId(recordId);
        binding.setModuleKey("purchase-order");
        binding.setRecordId(recordId);
        binding.setAttachmentId(1L);
        return binding;
    }

    private static class TestBusinessEntity extends AbstractAuditableEntity {
    }
}
