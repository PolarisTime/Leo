package com.leo.erp.master.material.repository;

import com.leo.erp.master.material.domain.entity.MaterialCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface MaterialCategoryRepository extends JpaRepository<MaterialCategory, Long>, JpaSpecificationExecutor<MaterialCategory> {

    Optional<MaterialCategory> findByIdAndDeletedFlagFalse(Long id);

    List<MaterialCategory> findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc(String status);
}
