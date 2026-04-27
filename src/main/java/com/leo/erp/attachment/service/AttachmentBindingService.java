package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.AttachmentBinding;
import com.leo.erp.attachment.repository.AttachmentBindingRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class AttachmentBindingService {

    private final AttachmentBindingRepository repository;
    private final AttachmentService attachmentService;
    private final SnowflakeIdGenerator idGenerator;
    private final ModuleCatalog moduleCatalog;

    public AttachmentBindingService(AttachmentBindingRepository repository,
                                    AttachmentService attachmentService,
                                    SnowflakeIdGenerator idGenerator,
                                    ModuleCatalog moduleCatalog) {
        this.repository = repository;
        this.attachmentService = attachmentService;
        this.idGenerator = idGenerator;
        this.moduleCatalog = moduleCatalog;
    }

    @Transactional(readOnly = true)
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
            repository.saveAll(bindings);
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
    public Map<Long, List<AttachmentView>> listByRecordIds(String moduleKey, List<Long> recordIds) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        if (recordIds == null || recordIds.isEmpty()) {
            return Map.of();
        }
        List<Long> normalizedRecordIds = new ArrayList<>(new LinkedHashSet<>(recordIds.stream()
                .filter(id -> id != null && id > 0)
                .toList()));
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

    private String normalizeModuleKey(String moduleKey) {
        if (moduleKey == null || moduleKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少模块标识");
        }
        String normalized = moduleKey.trim();
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
}
