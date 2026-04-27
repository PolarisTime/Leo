package com.leo.erp.master.material.repository;

import com.leo.erp.master.material.domain.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long>, JpaSpecificationExecutor<Material> {

    boolean existsByMaterialCodeAndDeletedFlagFalse(String materialCode);

    Optional<Material> findByMaterialCode(String materialCode);

    List<Material> findByMaterialCodeInAndDeletedFlagFalse(Collection<String> materialCodes);

    List<Material> findByDeletedFlagFalseOrderByMaterialCodeAsc();

    Optional<Material> findByIdAndDeletedFlagFalse(Long id);
}
