package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.AttachmentBinding;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentBindingRepository;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.common.support.BusinessRecordEntityCatalog;
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
    private final AttachmentFileRepository attachmentFileRepository;

    public AttachmentRecordAccessService(EntityManager entityManager,
                                         PermissionService permissionService,
                                         AttachmentBindingRepository attachmentBindingRepository,
                                         AttachmentFileRepository attachmentFileRepository) {
        this.entityManager = entityManager;
        this.permissionService = permissionService;
        this.attachmentBindingRepository = attachmentBindingRepository;
        this.attachmentFileRepository = attachmentFileRepository;
    }

    @Transactional(readOnly = true)
    public void assertRecordAccessible(SecurityPrincipal principal, String moduleKey, String actionCode, Long recordId) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        long normalizedRecordId = normalizeRecordId(recordId);
        if (!isBusinessModule(normalizedModuleKey)) {
            return;
        }
        AbstractAuditableEntity entity = loadBusinessEntity(normalizedModuleKey, normalizedRecordId);
        if (entity == null || entity.isDeletedFlag()) {
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
                .filter(entity -> entity != null && !entity.isDeletedFlag())
                .anyMatch(entity -> canAccess(principal, normalizedModuleKey, actionCode));
        if (!accessible) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无访问权限");
        }
    }

    @Transactional(readOnly = true)
    public void assertAttachmentAccessible(SecurityPrincipal principal, String actionCode, Long attachmentId) {
        long normalizedAttachmentId = normalizeRecordId(attachmentId);
        List<AttachmentBinding> bindings = attachmentBindingRepository
                .findByAttachmentIdAndDeletedFlagFalseOrderByModuleKeyAscRecordIdAscSortOrderAscIdAsc(normalizedAttachmentId);
        if (bindings.isEmpty()) {
            AttachmentFile attachment = attachmentFileRepository.findByIdAndDeletedFlagFalse(normalizedAttachmentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "附件不存在或已删除"));
            if (principal == null || !principal.id().equals(attachment.getCreatedBy())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无附件访问权限");
            }
            return;
        }
        boolean accessible = bindings.stream().anyMatch(binding -> canAccessBinding(principal, actionCode, binding));
        if (!accessible) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无访问权限");
        }
    }

    private void assertCanAccess(SecurityPrincipal principal, String moduleKey, String actionCode, AbstractAuditableEntity entity) {
        if (!canAccess(principal, moduleKey, actionCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无访问权限");
        }
    }

    private boolean canAccess(SecurityPrincipal principal, String moduleKey, String actionCode) {
        if (principal == null) {
            return false;
        }
        String resource = resolveResource(moduleKey);
        String action = ResourcePermissionCatalog.normalizeAction(actionCode);
        return permissionService.can(principal.id(), resource, action);
    }

    private boolean canAccessBinding(SecurityPrincipal principal, String actionCode, AttachmentBinding binding) {
        String moduleKey = normalizeModuleKey(binding.getModuleKey());
        if (!isBusinessModule(moduleKey)) {
            return false;
        }
        AbstractAuditableEntity entity = loadBusinessEntity(moduleKey, binding.getRecordId());
        return entity != null && !entity.isDeletedFlag() && canAccess(principal, moduleKey, actionCode);
    }

    private AbstractAuditableEntity loadBusinessEntity(String moduleKey, Long recordId) {
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

}
