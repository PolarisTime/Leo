package com.leo.erp.system.setup.repository;

import com.leo.erp.system.setup.domain.entity.BootstrapState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface BootstrapStateRepository extends JpaRepository<BootstrapState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from BootstrapState state where state.id = 1")
    Optional<BootstrapState> findSingletonForUpdate();
}
