package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.material.domain.MaterialSnapshot;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.domain.entity.MaterialHistory;
import com.leo.erp.master.material.repository.MaterialHistoryRepository;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.material.web.dto.MaterialBatchRollbackResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 按导入批次回滚：CREATED 行软删，UPDATED 行还原 before 快照。
 * 限制：仅回滚仍可按 ID 定位到的商品；回滚动作本身写入 ROLLBACK 历史作为审计留痕，不参与再次回滚。
 */
@Service
public class MaterialBatchRollbackService {

    private final MaterialHistoryRepository historyRepository;
    private final MaterialRepository materialRepository;
    private final MaterialHistoryRecorder recorder;

    public MaterialBatchRollbackService(MaterialHistoryRepository historyRepository,
                                        MaterialRepository materialRepository,
                                        MaterialHistoryRecorder recorder) {
        this.historyRepository = historyRepository;
        this.materialRepository = materialRepository;
        this.recorder = recorder;
    }

    @Transactional
    public MaterialBatchRollbackResponse rollback(String importBatchNo) {
        if (importBatchNo == null || importBatchNo.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导入批次号不能为空");
        }
        String batch = importBatchNo.trim();
        List<MaterialHistory> rows = historyRepository.findByImportBatchNoOrderByIdAsc(batch);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "导入批次不存在");
        }

        int createdRolledBack = 0;
        int updatedRestored = 0;
        int missing = 0;
        // 逆序回滚：同批次内同一商品可能多次变更，先撤销较晚变更再撤销较早变更。
        for (int index = rows.size() - 1; index >= 0; index--) {
            MaterialHistory history = rows.get(index);
            Optional<Material> target = materialRepository.findById(history.getMaterialId());
            if (target.isEmpty()) {
                missing++;
                continue;
            }
            Material material = target.get();
            MaterialSnapshot before = MaterialSnapshot.of(material);
            if (MaterialHistoryRecorder.TYPE_CREATED.equals(history.getChangeType())) {
                material.setDeletedFlag(true);
                materialRepository.save(material);
                recorder.record(material.getId(), MaterialHistoryRecorder.SOURCE_ROLLBACK,
                        MaterialHistoryRecorder.TYPE_ROLLBACK, before, null, null, "回滚导入批次 " + batch);
                createdRolledBack++;
            } else if (MaterialHistoryRecorder.TYPE_UPDATED.equals(history.getChangeType())) {
                MaterialSnapshot restore = recorder.parse(history.getBeforeSnapshot());
                if (restore == null) {
                    missing++;
                    continue;
                }
                restore.applyTo(material);
                materialRepository.save(material);
                recorder.record(material.getId(), MaterialHistoryRecorder.SOURCE_ROLLBACK,
                        MaterialHistoryRecorder.TYPE_ROLLBACK, before, MaterialSnapshot.of(material),
                        null, "回滚导入批次 " + batch);
                updatedRestored++;
            }
        }
        return new MaterialBatchRollbackResponse(batch, rows.size(), createdRolledBack, updatedRestored, missing);
    }
}
