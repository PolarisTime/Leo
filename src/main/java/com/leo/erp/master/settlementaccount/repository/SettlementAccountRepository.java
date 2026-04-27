package com.leo.erp.master.settlementaccount.repository;

import com.leo.erp.master.settlementaccount.domain.entity.SettlementAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SettlementAccountRepository extends JpaRepository<SettlementAccount, Long>, JpaSpecificationExecutor<SettlementAccount> {

    Optional<SettlementAccount> findByIdAndDeletedFlagFalse(Long id);

    boolean existsByBankAccountAndDeletedFlagFalse(String bankAccount);

    List<SettlementAccount> findByDeletedFlagFalseOrderByAccountNameAsc();
}
