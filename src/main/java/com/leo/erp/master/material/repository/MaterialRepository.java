package com.leo.erp.master.material.repository;

import com.leo.erp.common.support.ModuleKeys;
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
        return ModuleKeys.MATERIAL;
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

    /**
     * 「选商品」目录：未删除 + 指定状态（正常/禁用）+ 商品类型。
     *
     * <p>启停过滤下推到 SQL，避免在 Java 侧先查全量再筛；禁用商品仍留在库里，
     * 只是不再进入选商品列表。</p>
     */
    List<Material> findByDeletedFlagFalseAndStatusAndMaterialTypeOrderByMaterialCodeAsc(String status,
                                                                                        String materialType);

    long countByDeletedFlagFalse();

    /**
     * 材质（品名）字典：只取实体商品。
     *
     * <p>附加费用类商品（吊装费、运费、加工费、过磅费…）复用同一张物料表，但它们的
     * `material` 列存的是费用名，不是牌号。此前未过滤 `materialType`，导致材质下拉里
     * 混进 6 个费用项——用户按「材质」筛实体商品时会看到「运费」「吊装费」这种选项。</p>
     *
     * <p>与下方 {@link #findDistinctActiveProductBrands()} 口径对齐：品牌查询早就带了
     * `materialType = '实体商品'`，只有材质查询漏了，属同族查询的条件不一致。</p>
     */
    @Query("SELECT DISTINCT m.material FROM Material m WHERE m.deletedFlag = false AND m.materialType = '实体商品' AND m.material IS NOT NULL ORDER BY m.material")
    List<String> findDistinctMaterials();

    @Query("SELECT DISTINCT m.brand FROM Material m WHERE m.deletedFlag = false AND m.materialType = '实体商品' AND m.brand IS NOT NULL ORDER BY m.brand")
    List<String> findDistinctActiveProductBrands();
}
