package com.leo.erp.master.material.repository;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.common.support.MaterialCatalog;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.master.material.domain.entity.Material;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long>, JpaSpecificationExecutor<Material>,
        MaterialCatalog, RecordExistencePort {

    @Override
    default String moduleKey() {
        return "material";
    }

    @Override
    default boolean existsActive(Long recordId) {
        return existsByIdAndDeletedFlagFalse(recordId);
    }

    @Override
    default boolean lockActive(Long recordId) {
        return findActiveForAttachmentBinding(recordId).isPresent();
    }

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select material from Material material where material.id = :id and material.deletedFlag = false")
    Optional<Material> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsByMaterialCodeAndDeletedFlagFalse(String materialCode);

    Optional<Material> findByMaterialCode(String materialCode);

    List<Material> findByMaterialCodeInAndDeletedFlagFalse(Collection<String> materialCodes);

    List<Material> findByDeletedFlagFalseOrderByMaterialCodeAsc();

    @Query("""
            SELECT m
            FROM Material m
            WHERE m.deletedFlag = false
              AND TRIM(m.brand) = :brand
              AND TRIM(m.material) = :material
              AND TRIM(m.spec) = :spec
              AND TRIM(COALESCE(m.length, '')) = :length
              AND (:excludedId IS NULL OR m.id <> :excludedId)
            ORDER BY m.materialCode ASC
            """)
    List<Material> findActiveIdentityConflicts(@Param("brand") String brand,
                                               @Param("material") String material,
                                               @Param("spec") String spec,
                                               @Param("length") String length,
                                               @Param("excludedId") Long excludedId);

    @Query("""
            SELECT m
            FROM Material m
            WHERE m.deletedFlag = false
              AND TRIM(m.brand) IN :brands
              AND TRIM(m.material) IN :materials
              AND TRIM(m.spec) IN :specs
            ORDER BY m.materialCode ASC
            """)
    List<Material> findActiveIdentityCandidates(@Param("brands") Collection<String> brands,
                                                @Param("materials") Collection<String> materials,
                                                @Param("specs") Collection<String> specs);

    @Override
    default List<TradeMaterialSnapshot> listActiveMaterials() {
        return findByDeletedFlagFalseOrderByMaterialCodeAsc().stream()
                .map(material -> new TradeMaterialSnapshot(
                        material.getId(),
                        material.getMaterialCode()))
                .toList();
    }

    Optional<Material> findByIdAndDeletedFlagFalse(Long id);

    long countByDeletedFlagFalse();

    @Query("SELECT DISTINCT m.material FROM Material m WHERE m.deletedFlag = false AND m.material IS NOT NULL ORDER BY m.material")
    List<String> findDistinctMaterials();
}
