package com.leo.erp.master.material.repository;

import com.leo.erp.master.material.domain.entity.MaterialHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialHistoryRepository extends JpaRepository<MaterialHistory, Long> {

    Page<MaterialHistory> findByMaterialId(Long materialId, Pageable pageable);

    List<MaterialHistory> findByImportBatchNoOrderByIdAsc(String importBatchNo);
}
