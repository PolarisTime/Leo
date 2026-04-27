package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.RefreshTokenSessionRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.RefreshTokenAdminResponse;
import com.leo.erp.auth.web.dto.RefreshTokenSessionSummaryResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.AfterCommitExecutor;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.security.jwt.AccessTokenBlacklistService;
import com.leo.erp.security.jwt.SessionActivityService;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RefreshTokenAdminService {

    private final RefreshTokenSessionRepository repository;
    private final UserAccountRepository userAccountRepository;
    private final AccessTokenBlacklistService blacklistService;
    private final SessionActivityService sessionActivityService;
    private final AfterCommitExecutor afterCommitExecutor;

    public RefreshTokenAdminService(RefreshTokenSessionRepository repository,
                                    UserAccountRepository userAccountRepository,
                                    AccessTokenBlacklistService blacklistService,
                                    SessionActivityService sessionActivityService,
                                    AfterCommitExecutor afterCommitExecutor) {
        this.repository = repository;
        this.userAccountRepository = userAccountRepository;
        this.blacklistService = blacklistService;
        this.sessionActivityService = sessionActivityService;
        this.afterCommitExecutor = afterCommitExecutor;
    }

    @Transactional(readOnly = true)
    public Page<RefreshTokenAdminResponse> page(PageQuery query, String keyword) {
        Specification<RefreshTokenSession> spec = Specs.<RefreshTokenSession>notDeleted()
                .and(Specs.keywordLike(keyword, "tokenId", "loginIp", "deviceInfo"));
        return repository.findAll(spec, query.toPageable("id"))
                .map(entity -> toResponse(entity, Map.of(), Map.of()));
    }

    @Transactional(readOnly = true)
    public Page<RefreshTokenAdminResponse> pageWithUserInfo(PageQuery query, String keyword) {
        Specification<RefreshTokenSession> spec = Specs.<RefreshTokenSession>notDeleted()
                .and(Specs.keywordLike(keyword, "tokenId", "loginIp", "deviceInfo"));

        Page<RefreshTokenSession> page = repository.findAll(spec, query.toPageable("id"));

        Map<Long, UserAccount> userMap = page.getContent().stream()
                .map(RefreshTokenSession::getUserId)
                .distinct()
                .map(id -> userAccountRepository.findByIdAndDeletedFlagFalse(id).orElse(null))
                .filter(u -> u != null)
                .collect(Collectors.toMap(UserAccount::getId, u -> u));
        Map<String, LocalDateTime> lastActiveMap = sessionActivityService.resolveLastActiveAt(
                page.getContent().stream().map(RefreshTokenSession::getTokenId).toList()
        );

        return page.map(entity -> toResponse(entity, userMap, lastActiveMap));
    }

    @Transactional(readOnly = true)
    public RefreshTokenSessionSummaryResponse summary() {
        var activeSessions = repository.findByDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfter(LocalDateTime.now());
        Map<String, LocalDateTime> lastActiveMap = sessionActivityService.resolveLastActiveAt(
                activeSessions.stream().map(RefreshTokenSession::getTokenId).toList()
        );
        long onlineSessions = activeSessions.stream()
                .filter(session -> lastActiveMap.containsKey(session.getTokenId()))
                .count();
        long onlineUsers = activeSessions.stream()
                .filter(session -> lastActiveMap.containsKey(session.getTokenId()))
                .map(RefreshTokenSession::getUserId)
                .distinct()
                .count();
        return new RefreshTokenSessionSummaryResponse(onlineUsers, onlineSessions, activeSessions.size());
    }

    @Transactional
    public void revoke(Long id) {
        RefreshTokenSession entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "令牌不存在"));
        if (entity.isRevoked()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "令牌已被禁用");
        }
        entity.setRevokedAt(LocalDateTime.now());
        repository.save(entity);
        scheduleSessionRevocationSideEffects(entity.getTokenId());
    }

    @Transactional
    public int revokeAll() {
        Specification<RefreshTokenSession> spec = Specs.<RefreshTokenSession>notDeleted()
                .and((root, query, cb) -> cb.isNull(root.get("revokedAt")));
        var activeTokens = repository.findAll(spec);
        LocalDateTime now = LocalDateTime.now();
        for (RefreshTokenSession token : activeTokens) {
            token.setRevokedAt(now);
            scheduleSessionRevocationSideEffects(token.getTokenId());
        }
        repository.saveAll(activeTokens);
        return activeTokens.size();
    }

    private void scheduleSessionRevocationSideEffects(String sessionId) {
        afterCommitExecutor.run(() -> {
            blacklistService.blacklistSession(sessionId);
            sessionActivityService.clearSession(sessionId);
        });
    }

    private RefreshTokenAdminResponse toResponse(RefreshTokenSession entity,
                                                 Map<Long, UserAccount> userMap,
                                                 Map<String, LocalDateTime> lastActiveMap) {
        String status;
        if (entity.isRevoked()) {
            status = "已禁用";
        } else if (entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            status = "已过期";
        } else {
            status = "有效";
        }

        UserAccount user = userMap.get(entity.getUserId());
        String loginName = user != null ? user.getLoginName() : String.valueOf(entity.getUserId());
        String userName = user != null ? user.getUserName() : "--";
        LocalDateTime lastActiveAt = lastActiveMap.get(entity.getTokenId());
        boolean online = "有效".equals(status) && lastActiveAt != null;

        return new RefreshTokenAdminResponse(
                entity.getId(),
                entity.getUserId(),
                loginName,
                userName,
                entity.getTokenId(),
                entity.getLoginIp(),
                entity.getDeviceInfo(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                status,
                lastActiveAt,
                online
        );
    }
}
