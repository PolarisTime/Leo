package com.leo.erp.master.carrier.repository;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CarrierRepository extends JpaRepository<Carrier, Long>, JpaSpecificationExecutor<Carrier>,
        RecordExistencePort {

    @Override
    default String moduleKey() {
        return "carrier";
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
    @Query("select carrier from Carrier carrier where carrier.id = :id and carrier.deletedFlag = false")
    Optional<Carrier> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsByCarrierCodeAndDeletedFlagFalse(String carrierCode);

    Optional<Carrier> findByCarrierCodeAndDeletedFlagFalse(String carrierCode);

    List<Carrier> findByDeletedFlagFalseOrderByCarrierCodeAsc();

    List<Carrier> findByDeletedFlagFalseAndStatusOrderByCarrierCodeAsc(String status);

    Optional<Carrier> findByIdAndDeletedFlagFalse(Long id);
}
