package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.AttachmentBinding;
import com.leo.erp.attachment.repository.AttachmentBindingRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.AuditableEntity;
import com.leo.erp.common.support.BusinessRecordEntityCatalog;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AttachmentRecordAccessService {

    private final EntityManager entityManager;
    private final PermissionService permissionService;
    private final AttachmentBindingRepository attachmentBindingRepository;

    public AttachmentRecordAccessService(EntityManager entityManager,
                                         PermissionService permissionService,
                                         AttachmentBindingRepository attachmentBindingRepository) {
        this.entityManager = entityManager;
        this.permissionService = permissionService;
        this.attachmentBindingRepository = attachmentBindingRepository;
    }

    @Transactional(readOnly = true)
    public void assertRecordAccessible(SecurityPrincipal principal, String moduleKey, String actionCode, Long recordId) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        long normalizedRecordId = normalizeRecordId(recordId);
        if (!isBusinessModule(normalizedModuleKey)) {
            return;
        }
        AuditableEntity entity = loadBusinessEntity(normalizedModuleKey, normalizedRecordId);
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "业务记录不存在");
        }
        assertCanAccess(principal, normalizedModuleKey, actionCode, entity);
    }

    @Transactional(readOnly = true)
    public void assertAttachmentAccessible(SecurityPrincipal principal, String moduleKey, String actionCode, Long attachmentId) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        long normalizedAttachmentId = normalizeRecordId(attachmentId);
        if (!isBusinessModule(normalizedModuleKey)) {
            return;
        }
        List<AttachmentBinding> bindings = attachmentBindingRepository
                .findByModuleKeyAndAttachmentIdAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc(
                        normalizedModuleKey,
                        normalizedAttachmentId
                );
        if (bindings.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "附件未绑定到当前业务记录");
        }
        boolean accessible = bindings.stream()
                .map(AttachmentBinding::getRecordId)
                .map(recordId -> loadBusinessEntity(normalizedModuleKey, recordId))
                .filter(entity -> entity != null && !Boolean.TRUE.equals(entity.getDeletedFlag()))
                .anyMatch(entity -> canAccess(principal, normalizedModuleKey, actionCode, entity));
        if (!accessible) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无数据权限");
        }
    }

    private void assertCanAccess(SecurityPrincipal principal, String moduleKey, String actionCode, AuditableEntity entity) {
        if (!canAccess(principal, moduleKey, actionCode, entity)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无数据权限");
        }
    }

    private boolean canAccess(SecurityPrincipal principal, String moduleKey, String actionCode, AuditableEntity entity) {
        DataScopeContext.Context previous = DataScopeContext.current();
        String resource = resolveResource(moduleKey);
        String action = ResourcePermissionCatalog.normalizeAction(actionCode);
        String dataScope = permissionService.getUserDataScope(principal.id(), resource, action);
        try {
            DataScopeContext.set(
                    principal.id(),
                    resource,
                    dataScope,
                    permissionService.getDataScopeOwnerUserIds(principal.id(), dataScope)
            );
            return DataScopeContext.canAccess(entity);
        } finally {
            restore(previous);
        }
    }

    private AuditableEntity loadBusinessEntity(String moduleKey, Long recordId) {
        return BusinessRecordEntityCatalog.findEntityType(moduleKey)
                .map(entityType -> entityManager.find(entityType, recordId))
                .orElse(null);
    }

    private boolean isBusinessModule(String moduleKey) {
        return BusinessRecordEntityCatalog.hasEntity(moduleKey);
    }

    private String resolveResource(String moduleKey) {
        return ResourcePermissionCatalog.resolveResourceByMenuCode(moduleKey)
                .orElseGet(() -> ResourcePermissionCatalog.normalizeResource(moduleKey));
    }

    private String normalizeModuleKey(String moduleKey) {
        if (moduleKey == null || moduleKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少模块标识");
        }
        String normalized = BusinessRecordEntityCatalog.normalizeModuleKey(moduleKey);
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少模块标识");
        }
        return normalized;
    }

    private long normalizeRecordId(Long recordId) {
        if (recordId == null || recordId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少业务记录标识");
        }
        return recordId;
    }

    private void restore(DataScopeContext.Context previous) {
        if (previous == null) {
            DataScopeContext.clear();
            return;
        }
        DataScopeContext.set(previous.userId(), previous.resource(), previous.scope(), previous.ownerUserIds());
    }
}
