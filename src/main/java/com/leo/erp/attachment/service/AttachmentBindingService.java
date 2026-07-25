package com.leo.erp.attachment.service;

import com.leo.erp.attachment.api.AttachmentQuery;
import com.leo.erp.attachment.api.AttachmentView;
import com.leo.erp.attachment.domain.entity.AttachmentBinding;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentBindingRepository;
import com.leo.erp.attachment.repository.AttachmentBindingTargetLockRepository;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.support.SecurityPrincipal;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AttachmentBindingService implements AttachmentQuery {

    private static final String ACTIVE_ATTACHMENT_BINDING_UNIQUE_INDEX =
            "uk_sys_attachment_binding_active_attachment";

    private final AttachmentBindingRepository repository;
    private final AttachmentBindingTargetLockRepository targetLockRepository;
    private final RecordExistenceRegistry recordExistenceRegistry;
    private final AttachmentService attachmentService;
    private final SnowflakeIdGenerator idGenerator;
    private final ModuleCatalog moduleCatalog;
    private final AttachmentFileRepository attachmentFileRepository;

    public AttachmentBindingService(AttachmentBindingRepository repository,
                                    AttachmentBindingTargetLockRepository targetLockRepository,
                                    RecordExistenceRegistry recordExistenceRegistry,
                                    AttachmentService attachmentService,
                                    SnowflakeIdGenerator idGenerator,
                                    ModuleCatalog moduleCatalog,
                                    AttachmentFileRepository attachmentFileRepository) {
        this.repository = repository;
        this.targetLockRepository = targetLockRepository;
        this.recordExistenceRegistry = recordExistenceRegistry;
        this.attachmentService = attachmentService;
        this.idGenerator = idGenerator;
        this.moduleCatalog = moduleCatalog;
        this.attachmentFileRepository = attachmentFileRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public List<AttachmentView> list(String moduleKey, Long recordId) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        long normalizedRecordId = normalizeRecordId(recordId);
        List<Long> attachmentIds = repository.findByModuleKeyAndRecordIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc(
                        normalizedModuleKey,
                        normalizedRecordId
                ).stream()
                .map(AttachmentBinding::getAttachmentId)
                .toList();
        return attachmentService.getAttachments(attachmentIds, normalizedModuleKey);
    }

    @Transactional
    public List<AttachmentView> replace(String moduleKey, Long recordId, List<Long> attachmentIds) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        long normalizedRecordId = normalizeRecordId(recordId);
        List<Long> normalizedAttachmentIds = normalizeAttachmentIds(attachmentIds);
        attachmentService.validateAttachmentIds(normalizedAttachmentIds);
        lockAndAssertTargetRecordActive(normalizedModuleKey, normalizedRecordId);
        assertAttachmentsBindable(normalizedModuleKey, normalizedRecordId, normalizedAttachmentIds);

        List<AttachmentBinding> existingBindings = repository
                .findByModuleKeyAndRecordIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc(normalizedModuleKey, normalizedRecordId);
        if (!existingBindings.isEmpty()) {
            repository.deleteAllInBatch(existingBindings);
            repository.flush();
        }
        if (!normalizedAttachmentIds.isEmpty()) {
            List<AttachmentBinding> bindings = new ArrayList<>(normalizedAttachmentIds.size());
            for (int index = 0; index < normalizedAttachmentIds.size(); index++) {
                AttachmentBinding binding = new AttachmentBinding();
                binding.setId(idGenerator.nextId());
                binding.setModuleKey(normalizedModuleKey);
                binding.setRecordId(normalizedRecordId);
                binding.setAttachmentId(normalizedAttachmentIds.get(index));
                binding.setSortOrder(index + 1);
                bindings.add(binding);
            }
            try {
                repository.saveAll(bindings);
                repository.flush();
            } catch (DataIntegrityViolationException ex) {
                if (!isActiveAttachmentBindingConflict(ex)) {
                    throw ex;
                }
                throw new BusinessException(
                        ErrorCode.CONCURRENT_MODIFICATION,
                        "附件绑定已被其他请求修改，请刷新后重试"
                );
            }
        }

        return attachmentService.getAttachments(normalizedAttachmentIds, normalizedModuleKey);
    }

    @Transactional(readOnly = true)
    public List<Long> listAttachmentIds(String moduleKey, Long recordId) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        long normalizedRecordId = normalizeRecordId(recordId);
        return repository.findByModuleKeyAndRecordIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc(normalizedModuleKey, normalizedRecordId)
                .stream()
                .map(AttachmentBinding::getAttachmentId)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public Map<Long, List<AttachmentView>> listByRecordIds(String moduleKey, List<Long> recordIds) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        List<Long> normalizedRecordIds = normalizeRecordIds(recordIds);
        if (normalizedRecordIds.isEmpty()) {
            return Map.of();
        }

        List<AttachmentBinding> bindings = repository
                .findByModuleKeyAndRecordIdInAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc(normalizedModuleKey, normalizedRecordIds);
        if (bindings.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<Long>> attachmentIdsByRecordId = new LinkedHashMap<>();
        List<Long> attachmentIds = new ArrayList<>();
        for (AttachmentBinding binding : bindings) {
            attachmentIdsByRecordId.computeIfAbsent(binding.getRecordId(), key -> new ArrayList<>()).add(binding.getAttachmentId());
            attachmentIds.add(binding.getAttachmentId());
        }

        Map<Long, AttachmentView> attachmentMap = attachmentService.getAttachmentMap(attachmentIds, normalizedModuleKey);
        Map<Long, List<AttachmentView>> result = new LinkedHashMap<>();
        for (Long recordId : normalizedRecordIds) {
            List<AttachmentView> attachments = attachmentIdsByRecordId.getOrDefault(recordId, List.of()).stream()
                    .map(attachmentMap::get)
                    .filter(item -> item != null)
                    .toList();
            if (!attachments.isEmpty()) {
                result.put(recordId, attachments);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> countByRecordIds(String moduleKey, List<Long> recordIds) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        List<Long> normalizedRecordIds = normalizeRecordIds(recordIds);
        if (normalizedRecordIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> result = new LinkedHashMap<>();
        for (Long recordId : normalizedRecordIds) {
            result.put(recordId, 0);
        }

        List<AttachmentBinding> bindings = repository
                .findByModuleKeyAndRecordIdInAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc(normalizedModuleKey, normalizedRecordIds);
        for (AttachmentBinding binding : bindings) {
            result.computeIfPresent(binding.getRecordId(), (key, count) -> count + 1);
        }
        return result;
    }

    private String normalizeModuleKey(String moduleKey) {
        if (moduleKey == null || moduleKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少模块标识");
        }
        String normalized = moduleCatalog.normalizeModuleKey(moduleKey);
        if (!moduleCatalog.containsModule(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模块标识不合法");
        }
        return normalized;
    }

    private long normalizeRecordId(Long recordId) {
        if (recordId == null || recordId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少业务记录标识");
        }
        return recordId;
    }

    private List<Long> normalizeRecordIds(List<Long> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(recordIds.stream()
                .filter(id -> id != null && id > 0)
                .toList()));
    }

    private List<Long> normalizeAttachmentIds(List<Long> attachmentIds) {
        if (attachmentIds == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "附件列表不能为空");
        }
        if (attachmentIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalized = new ArrayList<>(attachmentIds.size());
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (Long attachmentId : attachmentIds) {
            if (attachmentId == null || attachmentId <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "附件ID不合法");
            }
            if (!uniqueIds.add(attachmentId)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "附件列表存在重复项");
            }
            normalized.add(attachmentId);
        }
        return normalized;
    }

    private void lockAndAssertTargetRecordActive(String moduleKey, long recordId) {
        targetLockRepository.lock(moduleKey, recordId);
        if (!recordExistenceRegistry.require(moduleKey).lockActive(recordId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "业务记录不存在");
        }
    }

    private boolean isActiveAttachmentBindingConflict(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && ACTIVE_ATTACHMENT_BINDING_UNIQUE_INDEX.equals(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void assertAttachmentsBindable(String moduleKey, Long recordId, List<Long> attachmentIds) {
        if (attachmentIds.isEmpty()) {
            return;
        }
        Long currentUserId = currentUserId();
        List<AttachmentFile> ownedByCurrentUser = attachmentFileRepository
                .findAllOwnedByForUpdate(attachmentIds, currentUserId);
        Set<Long> currentUserAttachmentIds = ownedByCurrentUser.stream()
                .map(AttachmentFile::getId)
                .collect(Collectors.toUnmodifiableSet());
        for (Long attachmentId : attachmentIds) {
            List<AttachmentBinding> bindings = repository
                    .findByAttachmentIdAndDeletedFlagFalseOrderByModuleKeyAscRecordIdAscSortOrderAscIdAsc(attachmentId);
            boolean boundToOtherRecord = bindings.stream()
                    .anyMatch(binding -> !moduleKey.equals(binding.getModuleKey())
                            || !recordId.equals(binding.getRecordId()));
            if (boundToOtherRecord) {
                // 已绑定附件不可跨业务记录复用，这是绑定领域不变量，与附件所有权无关。
                throw new BusinessException(ErrorCode.FORBIDDEN, "附件已绑定其他业务记录，不可复用");
            }
            if (!bindings.isEmpty() || currentUserAttachmentIds.contains(attachmentId)) {
                continue;
            }
            throw new BusinessException(ErrorCode.FORBIDDEN, "附件不属于当前用户");
        }
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof SecurityPrincipal principal) {
            return principal.id();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
    }
}
