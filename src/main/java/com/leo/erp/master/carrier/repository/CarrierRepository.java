package com.leo.erp.master.carrier.repository;

import com.leo.erp.master.carrier.domain.entity.Carrier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CarrierRepository extends JpaRepository<Carrier, Long>, JpaSpecificationExecutor<Carrier> {

    boolean existsByCarrierCodeAndDeletedFlagFalse(String carrierCode);

    List<Carrier> findByDeletedFlagFalseOrderByCarrierCodeAsc();

    Optional<Carrier> findByIdAndDeletedFlagFalse(Long id);
}
