package com.leo.erp.master.material.repository;

import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.common.support.MaterialCatalog;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long>, JpaSpecificationExecutor<Material>, MaterialCatalog {

    boolean existsByMaterialCodeAndDeletedFlagFalse(String materialCode);

    Optional<Material> findByMaterialCode(String materialCode);

    List<Material> findByMaterialCodeInAndDeletedFlagFalse(Collection<String> materialCodes);

    List<Material> findByDeletedFlagFalseOrderByMaterialCodeAsc();

    @Override
    default List<TradeMaterialSnapshot> listActiveMaterials() {
        return findByDeletedFlagFalseOrderByMaterialCodeAsc().stream()
                .map(material -> new TradeMaterialSnapshot(
                        material.getMaterialCode(),
                        Boolean.TRUE.equals(material.getBatchNoEnabled())))
                .toList();
    }

    Optional<Material> findByIdAndDeletedFlagFalse(Long id);

    long countByDeletedFlagFalse();

    @Query("SELECT DISTINCT m.material FROM Material m WHERE m.deletedFlag = false AND m.material IS NOT NULL ORDER BY m.material")
    List<String> findDistinctMaterials();
}
