package com.leo.erp.master.material.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.material.domain.MaterialSnapshot;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.domain.entity.MaterialHistory;
import com.leo.erp.master.material.repository.MaterialHistoryRepository;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.material.web.dto.MaterialBatchRollbackResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialBatchRollbackServiceTest {

    @Mock
    private MaterialHistoryRepository historyRepository;
    @Mock
    private MaterialRepository materialRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MaterialBatchRollbackService service;

    @BeforeEach
    void setUp() {
        MaterialHistoryRecorder recorder = new MaterialHistoryRecorder(
                historyRepository, new SnowflakeIdGenerator(1), objectMapper);
        service = new MaterialBatchRollbackService(historyRepository, materialRepository, recorder);
    }

    @Test
    void rollbackSoftDeletesCreatedRows() {
        Material current = dbMaterial(250);
        MaterialHistory history = history(100L, "CREATED", null);
        when(historyRepository.findByImportBatchNoOrderByIdAsc("B1")).thenReturn(List.of(history));
        when(materialRepository.findById(100L)).thenReturn(Optional.of(current));

        MaterialBatchRollbackResponse response = service.rollback("B1");

        assertThat(response.createdRolledBack()).isEqualTo(1);
        assertThat(response.updatedRestored()).isZero();
        assertThat(current.isDeletedFlag()).isTrue();
        verify(materialRepository).save(current);
        verify(historyRepository).save(any(MaterialHistory.class));
    }

    @Test
    void rollbackRestoresUpdatedRowsFromBeforeSnapshot() throws Exception {
        Material current = dbMaterial(250);
        String beforeJson = objectMapper.writeValueAsString(snapshot(1));
        MaterialHistory history = history(100L, "UPDATED", beforeJson);
        when(historyRepository.findByImportBatchNoOrderByIdAsc("B1")).thenReturn(List.of(history));
        when(materialRepository.findById(100L)).thenReturn(Optional.of(current));

        MaterialBatchRollbackResponse response = service.rollback("B1");

        assertThat(response.updatedRestored()).isEqualTo(1);
        assertThat(response.createdRolledBack()).isZero();
        assertThat(current.getPiecesPerBundle()).isEqualTo(1);
        verify(materialRepository).save(current);
    }

    @Test
    void rollbackUnknownBatchThrowsNotFound() {
        when(historyRepository.findByImportBatchNoOrderByIdAsc("NOPE")).thenReturn(List.of());

        assertThatThrownBy(() -> service.rollback("NOPE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void rollbackBlankBatchRejected() {
        assertThatThrownBy(() -> service.rollback("   "))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private MaterialHistory history(Long materialId, String changeType, String beforeSnapshot) {
        MaterialHistory history = new MaterialHistory();
        history.setId(1L);
        history.setMaterialId(materialId);
        history.setChangeSource(MaterialHistoryRecorder.SOURCE_IMPORT);
        history.setChangeType(changeType);
        history.setBeforeSnapshot(beforeSnapshot);
        history.setImportBatchNo("B1");
        return history;
    }

    private Material dbMaterial(int piecesPerBundle) {
        Material material = new Material();
        material.setId(100L);
        material.setMaterialCode("100");
        material.setBrand("中天");
        material.setMaterial("HRB400E");
        material.setCategory("直条");
        material.setSpec("12");
        material.setLength("9米");
        material.setUnit("吨");
        material.setQuantityUnit("件");
        material.setPieceWeightTon(new BigDecimal("1.998"));
        material.setPiecesPerBundle(piecesPerBundle);
        material.setUnitPrice(BigDecimal.ZERO);
        material.setMaterialType("实体商品");
        return material;
    }

    private MaterialSnapshot snapshot(int piecesPerBundle) {
        return new MaterialSnapshot(100L, "100", "中天", "HRB400E", "直条", "12", "9米", "吨", "件",
                new BigDecimal("1.998"), piecesPerBundle, BigDecimal.ZERO, null, "实体商品");
    }
}
