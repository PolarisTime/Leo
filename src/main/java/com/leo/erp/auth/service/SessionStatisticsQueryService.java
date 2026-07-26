package com.leo.erp.auth.service;

import com.leo.erp.auth.api.SessionStatisticsQuery;
import com.leo.erp.auth.repository.RefreshTokenSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class SessionStatisticsQueryService implements SessionStatisticsQuery {

    private final RefreshTokenSessionRepository refreshTokenSessionRepository;

    public SessionStatisticsQueryService(RefreshTokenSessionRepository refreshTokenSessionRepository) {
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
    }

    @Override
    public long countActiveSessions(Long userId, LocalDateTime activeAt) {
        return refreshTokenSessionRepository
                .countByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfter(userId, activeAt);
    }
}
