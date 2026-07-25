package com.leo.erp.master.material.repository;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.master.material.domain.entity.MaterialCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaterialCategoryRepository extends JpaRepository<MaterialCategory, Long>,
        JpaSpecificationExecutor<MaterialCategory>, RecordExistencePort {

    @Override
    default String moduleKey() {
        return "material-category";
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
    @Query("select category from MaterialCategory category where category.id = :id and category.deletedFlag = false")
    Optional<MaterialCategory> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    Optional<MaterialCategory> findByIdAndDeletedFlagFalse(Long id);

    Optional<MaterialCategory> findByCategoryCodeAndDeletedFlagFalse(String categoryCode);

    List<MaterialCategory> findByCategoryNameInAndDeletedFlagFalse(Collection<String> categoryNames);

    List<MaterialCategory> findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc(String status);
}
