package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.AttachmentBinding;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentBindingRepository;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.common.support.BusinessRecordEntityCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AttachmentRecordAccessService {

    private final EntityManager entityManager;
    private final AttachmentBindingRepository attachmentBindingRepository;
    private final AttachmentFileRepository attachmentFileRepository;

    public AttachmentRecordAccessService(EntityManager entityManager,
                                         AttachmentBindingRepository attachmentBindingRepository,
                                         AttachmentFileRepository attachmentFileRepository) {
        this.entityManager = entityManager;
        this.attachmentBindingRepository = attachmentBindingRepository;
        this.attachmentFileRepository = attachmentFileRepository;
    }

    @Transactional(readOnly = true)
    public void assertRecordExists(String moduleKey, Long recordId) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        long normalizedRecordId = normalizeRecordId(recordId);
        if (!isBusinessModule(normalizedModuleKey)) {
            return;
        }
        AbstractAuditableEntity entity = loadBusinessEntity(normalizedModuleKey, normalizedRecordId);
        if (entity == null || entity.isDeletedFlag()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "业务记录不存在");
        }
    }

    @Transactional(readOnly = true)
    public void assertAttachmentBoundToExistingRecord(String moduleKey, Long attachmentId) {
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
        boolean boundToExistingRecord = bindings.stream()
                .map(AttachmentBinding::getRecordId)
                .map(recordId -> loadBusinessEntity(normalizedModuleKey, recordId))
                .anyMatch(entity -> entity != null && !entity.isDeletedFlag());
        if (!boundToExistingRecord) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件未绑定到有效业务记录");
        }
    }

    @Transactional(readOnly = true)
    public void assertAttachmentAccessible(SecurityPrincipal principal, Long attachmentId) {
        long normalizedAttachmentId = normalizeRecordId(attachmentId);
        List<AttachmentBinding> bindings = attachmentBindingRepository
                .findByAttachmentIdAndDeletedFlagFalseOrderByModuleKeyAscRecordIdAscSortOrderAscIdAsc(
                        normalizedAttachmentId
                );
        if (bindings.isEmpty()) {
            assertUnboundAttachmentOwner(principal, normalizedAttachmentId);
            return;
        }
        boolean boundToExistingRecord = bindings.stream().anyMatch(this::isBoundToExistingRecord);
        if (!boundToExistingRecord) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件未绑定到有效业务记录");
        }
    }

    public String normalizeModuleKey(String moduleKey) {
        if (moduleKey == null || moduleKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少模块标识");
        }
        String normalized = BusinessRecordEntityCatalog.normalizeModuleKey(moduleKey);
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少模块标识");
        }
        return normalized;
    }

    private void assertUnboundAttachmentOwner(SecurityPrincipal principal, long attachmentId) {
        AttachmentFile attachment = attachmentFileRepository.findByIdAndDeletedFlagFalse(attachmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "附件不存在或已删除"));
        if (principal == null || !principal.id().equals(attachment.getCreatedBy())) {
            // 未绑定附件的创建者约束属于数据所有权不变量，由业务层处理。
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅附件创建者可以访问未绑定附件");
        }
    }

    private boolean isBoundToExistingRecord(AttachmentBinding binding) {
        String moduleKey = normalizeModuleKey(binding.getModuleKey());
        if (!isBusinessModule(moduleKey)) {
            return false;
        }
        AbstractAuditableEntity entity = loadBusinessEntity(moduleKey, binding.getRecordId());
        return entity != null && !entity.isDeletedFlag();
    }

    private AbstractAuditableEntity loadBusinessEntity(String moduleKey, Long recordId) {
        return BusinessRecordEntityCatalog.findEntityType(moduleKey)
                .map(entityType -> entityManager.find(entityType, recordId))
                .orElse(null);
    }

    private boolean isBusinessModule(String moduleKey) {
        return BusinessRecordEntityCatalog.hasEntity(moduleKey);
    }

    private long normalizeRecordId(Long recordId) {
        if (recordId == null || recordId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少业务记录标识");
        }
        return recordId;
    }
}
