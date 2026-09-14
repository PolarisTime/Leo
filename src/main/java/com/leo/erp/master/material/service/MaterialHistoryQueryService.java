package com.leo.erp.master.material.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.material.domain.MaterialSnapshot;
import com.leo.erp.master.material.domain.entity.MaterialHistory;
import com.leo.erp.master.material.repository.MaterialHistoryRepository;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.material.web.dto.MaterialHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaterialHistoryQueryService {

    private final MaterialHistoryRepository historyRepository;
    private final MaterialRepository materialRepository;
    private final MaterialHistoryRecorder recorder;

    public MaterialHistoryQueryService(MaterialHistoryRepository historyRepository,
                                       MaterialRepository materialRepository,
                                       MaterialHistoryRecorder recorder) {
        this.historyRepository = historyRepository;
        this.materialRepository = materialRepository;
        this.recorder = recorder;
    }

    @Transactional(readOnly = true)
    public Page<MaterialHistoryResponse> page(Long materialId, PageQuery query) {
        if (!materialRepository.existsById(materialId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        Pageable pageable = query.toPageable("id");
        return historyRepository.findByMaterialId(materialId, pageable).map(this::toResponse);
    }

    private MaterialHistoryResponse toResponse(MaterialHistory history) {
        return new MaterialHistoryResponse(
                history.getId(),
                history.getMaterialId(),
                history.getChangeSource(),
                history.getChangeType(),
                toSnapshot(recorder.parse(history.getBeforeSnapshot())),
                toSnapshot(recorder.parse(history.getAfterSnapshot())),
                history.getImportBatchNo(),
                history.getRemark(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    private MaterialHistoryResponse.Snapshot toSnapshot(MaterialSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new MaterialHistoryResponse.Snapshot(
                snapshot.id(),
                snapshot.materialCode(),
                snapshot.brand(),
                snapshot.material(),
                snapshot.category(),
                snapshot.spec(),
                snapshot.length(),
                snapshot.unit(),
                snapshot.quantityUnit(),
                snapshot.pieceWeightTon(),
                snapshot.piecesPerBundle(),
                snapshot.unitPrice(),
                snapshot.remark(),
                snapshot.materialType()
        );
    }
}
