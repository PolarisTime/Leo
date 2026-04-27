package com.leo.erp.system.securitykey.repository;

import com.leo.erp.system.securitykey.domain.entity.SecuritySecret;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SecuritySecretRepository extends JpaRepository<SecuritySecret, Long> {

    Optional<SecuritySecret> findFirstBySecretTypeAndStatusAndDeletedFlagFalseOrderByKeyVersionDesc(String secretType, String status);

    Optional<SecuritySecret> findFirstBySecretTypeAndDeletedFlagFalseOrderByKeyVersionDesc(String secretType);

    List<SecuritySecret> findBySecretTypeAndDeletedFlagFalseOrderByKeyVersionDesc(String secretType);

    List<SecuritySecret> findBySecretTypeAndStatusInAndDeletedFlagFalseOrderByKeyVersionDesc(
            String secretType,
            Collection<String> statuses
    );
}
