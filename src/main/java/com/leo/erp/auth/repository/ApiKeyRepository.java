package com.leo.erp.auth.repository;

import com.leo.erp.auth.domain.entity.ApiKey;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long>, JpaSpecificationExecutor<ApiKey> {

    Optional<ApiKey> findByKeyHashAndDeletedFlagFalse(String keyHash);

    Optional<ApiKey> findByIdAndDeletedFlagFalse(Long id);

    @Modifying
    @Query("""
            update ApiKey apiKey
               set apiKey.lastUsedAt = :lastUsedAt
             where apiKey.id = :id
            """)
    int updateLastUsedAt(@Param("id") Long id, @Param("lastUsedAt") LocalDateTime lastUsedAt);
}
