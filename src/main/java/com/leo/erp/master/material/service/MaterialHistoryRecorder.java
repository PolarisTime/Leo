package com.leo.erp.master.material.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.material.domain.MaterialSnapshot;
import com.leo.erp.master.material.domain.entity.MaterialHistory;
import com.leo.erp.master.material.repository.MaterialHistoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * 商品主数据历史写入与快照编解码。
 * 历史记录只追加，不修改；created_by/created_at 由 JPA 审计自动填充。
 */
@Service
public class MaterialHistoryRecorder {

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_IMPORT = "IMPORT";
    public static final String SOURCE_ROLLBACK = "ROLLBACK";

    public static final String TYPE_CREATED = "CREATED";
    public static final String TYPE_UPDATED = "UPDATED";
    public static final String TYPE_DELETED = "DELETED";
    public static final String TYPE_ROLLBACK = "ROLLBACK";

    private final MaterialHistoryRepository repository;
    private final SnowflakeIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public MaterialHistoryRecorder(MaterialHistoryRepository repository,
                                   SnowflakeIdGenerator idGenerator,
                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    public void record(Long materialId,
                       String changeSource,
                       String changeType,
                       MaterialSnapshot before,
                       MaterialSnapshot after,
                       String importBatchNo,
                       String remark) {
        MaterialHistory history = new MaterialHistory();
        history.setId(idGenerator.nextId());
        history.setMaterialId(materialId);
        history.setChangeSource(changeSource);
        history.setChangeType(changeType);
        history.setBeforeSnapshot(write(before));
        history.setAfterSnapshot(write(after));
        history.setImportBatchNo(importBatchNo);
        history.setRemark(remark);
        repository.save(history);
    }

    public MaterialSnapshot parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MaterialSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("商品历史快照解析失败", exception);
        }
    }

    private String write(MaterialSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("商品历史快照序列化失败", exception);
        }
    }
}
