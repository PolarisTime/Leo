package com.leo.erp.attachment.service;

import com.leo.erp.attachment.api.AttachmentRecordAccess;
import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.attachment.domain.entity.AttachmentBinding;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentBindingRepository;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class AttachmentRecordAccessService implements AttachmentRecordAccess {

    private final RecordExistenceRegistry recordExistenceRegistry;
    private final AttachmentBindingRepository attachmentBindingRepository;
    private final AttachmentFileRepository attachmentFileRepository;

    public AttachmentRecordAccessService(RecordExistenceRegistry recordExistenceRegistry,
                                         AttachmentBindingRepository attachmentBindingRepository,
                                         AttachmentFileRepository attachmentFileRepository) {
        this.recordExistenceRegistry = recordExistenceRegistry;
        this.attachmentBindingRepository = attachmentBindingRepository;
        this.attachmentFileRepository = attachmentFileRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public void assertRecordExists(String moduleKey, Long recordId) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        long normalizedRecordId = normalizeRecordId(recordId);
        RecordExistencePort port = recordExistenceRegistry.require(normalizedModuleKey);
        if (!port.existsActive(normalizedRecordId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "业务记录不存在");
        }
    }

    @Transactional(readOnly = true)
    public void assertAttachmentAccessible(SecurityPrincipal principal, String moduleKey, Long attachmentId) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        long normalizedAttachmentId = normalizeRecordId(attachmentId);
        RecordExistencePort port = recordExistenceRegistry.require(normalizedModuleKey);
        List<AttachmentBinding> bindings = attachmentBindingRepository
                .findByAttachmentIdAndDeletedFlagFalseOrderByModuleKeyAscRecordIdAscSortOrderAscIdAsc(
                        normalizedAttachmentId);
        if (bindings.isEmpty()) {
            assertUnboundAttachmentOwner(principal, normalizedAttachmentId);
            return;
        }
        boolean boundToExistingRecord = bindings.stream()
                .filter(binding -> normalizedModuleKey.equals(
                        recordExistenceRegistry.normalizeModuleKey(binding.getModuleKey())))
                .map(AttachmentBinding::getRecordId)
                .anyMatch(port::existsActive);
        if (!boundToExistingRecord) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件未绑定到有效业务记录");
        }
    }

    public String normalizeModuleKey(String moduleKey) {
        if (moduleKey == null || moduleKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少模块标识");
        }
        String normalized = recordExistenceRegistry.normalizeModuleKey(moduleKey);
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少模块标识");
        }
        recordExistenceRegistry.require(normalized);
        return normalized;
    }

    private void assertUnboundAttachmentOwner(SecurityPrincipal principal, long attachmentId) {
        AttachmentFile attachment = attachmentFileRepository.findByIdAndDeletedFlagFalse(attachmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "附件不存在或已删除"));
        Long ownerUserId = attachment.getOwnerUserId() == null
                ? attachment.getCreatedBy()
                : attachment.getOwnerUserId();
        if (principal == null || !Objects.equals(principal.id(), ownerUserId)) {
            // 未绑定附件的业务所有权属于领域不变量，与创建审计无关。
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅附件所有者可以访问未绑定附件");
        }
    }

    private long normalizeRecordId(Long recordId) {
        if (recordId == null || recordId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少业务记录标识");
        }
        return recordId;
    }
}
