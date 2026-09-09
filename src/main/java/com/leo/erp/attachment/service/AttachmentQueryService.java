package com.leo.erp.attachment.service;

import com.leo.erp.attachment.api.AttachmentView;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AttachmentQueryService {

    private final AttachmentFileRepository repository;
    private final AttachmentResponseAssembler responseAssembler;

    public AttachmentQueryService(AttachmentFileRepository repository,
                                  AttachmentResponseAssembler responseAssembler) {
        this.repository = repository;
        this.responseAssembler = responseAssembler;
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> getAttachments(List<Long> ids) {
        return getAttachments(ids, null);
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> getAttachments(List<Long> ids, String moduleKey) {
        List<Long> normalizedIds = normalizeIds(ids);
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        Map<Long, AttachmentView> attachmentMap = getAttachmentMap(normalizedIds, moduleKey);
        List<AttachmentView> results = new ArrayList<>(normalizedIds.size());
        for (Long id : normalizedIds) {
            AttachmentView response = attachmentMap.get(id);
            if (response != null) {
                results.add(response);
            }
        }
        return results;
    }

    @Transactional(readOnly = true)
    public Map<Long, AttachmentView> getAttachmentMap(List<Long> ids) {
        return getAttachmentMap(ids, null);
    }

    @Transactional(readOnly = true)
    public Map<Long, AttachmentView> getAttachmentMap(List<Long> ids, String moduleKey) {
        List<Long> normalizedIds = normalizeIds(ids);
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }
        return repository.findAllByIdInAndDeletedFlagFalse(normalizedIds).stream()
                .collect(Collectors.toMap(
                        AttachmentFile::getId,
                        entity -> responseAssembler.toResponse(entity, moduleKey),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    @Transactional(readOnly = true)
    public void validateAttachmentIds(List<Long> ids) {
        List<Long> normalizedIds = normalizeIds(ids);
        if (normalizedIds.isEmpty()) {
            return;
        }
        List<AttachmentFile> entities = repository.findAllByIdInAndDeletedFlagFalse(normalizedIds);
        if (entities.size() != normalizedIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "附件不存在或已删除");
        }
    }

    AttachmentFile getAttachment(Long id, String accessKey) {
        return repository.findByIdAndDeletedFlagFalse(id)
                .filter(item -> accessKeyMatches(item, accessKey))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "附件不存在"));
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private boolean accessKeyMatches(AttachmentFile entity, String accessKey) {
        if (accessKey == null || accessKey.isBlank() || entity.getAccessKey() == null || entity.getAccessKey().isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                entity.getAccessKey().getBytes(StandardCharsets.UTF_8),
                accessKey.getBytes(StandardCharsets.UTF_8)
        );
    }
}
