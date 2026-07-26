package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.CurrentAccountResponse;
import com.leo.erp.auth.web.dto.CurrentAccountUpdateRequest;
import com.leo.erp.auth.web.dto.PasswordChangeRequest;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.jwt.AuthenticatedUserCacheService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 单人模式下的当前账号资料与凭据服务，不承载角色或权限判断。 */
@Service
public class UserAccountService {

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SessionManagementService sessionManagementService;
    private final AuthenticatedUserCacheService authenticatedUserCacheService;
    private final ApplicationEventPublisher eventPublisher;

    public UserAccountService(
            UserAccountRepository repository,
            PasswordEncoder passwordEncoder,
            SessionManagementService sessionManagementService,
            AuthenticatedUserCacheService authenticatedUserCacheService,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.sessionManagementService = sessionManagementService;
        this.authenticatedUserCacheService = authenticatedUserCacheService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public CurrentAccountResponse current(Long userId) {
        return toResponse(getEntity(userId));
    }

    @Transactional
    public CurrentAccountResponse updateCurrent(Long userId, CurrentAccountUpdateRequest request) {
        UserAccount account = getEntityForUpdate(userId);
        account.setUserName(normalizeRequired(request.userName(), "用户姓名"));
        account.setMobile(normalizeOptional(request.mobile()));
        account.setRemark(normalizeOptional(request.remark()));
        UserAccount saved = repository.save(account);
        evictCaches(saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        UserAccount account = getEntityForUpdate(userId);
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "新密码不能与当前密码相同");
        }

        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        long credentialVersion = account.getCredentialVersion() == null
                ? 0L
                : account.getCredentialVersion();
        account.setCredentialVersion(credentialVersion + 1L);
        repository.saveAndFlush(account);
        sessionManagementService.revokeActiveSessionsForPasswordChange(userId);
        evictCaches(userId);
    }

    private UserAccount getEntity(Long userId) {
        return repository.findByIdAndDeletedFlagFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "当前账号不存在"));
    }

    private UserAccount getEntityForUpdate(Long userId) {
        return repository.findByIdAndDeletedFlagFalseForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "当前账号不存在"));
    }

    private CurrentAccountResponse toResponse(UserAccount account) {
        return new CurrentAccountResponse(
                account.getId(),
                account.getLoginName(),
                account.getUserName(),
                account.getMobile(),
                account.getLastLoginDate(),
                account.getRemark()
        );
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + "不能为空");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void evictCaches(Long userId) {
        authenticatedUserCacheService.evict(userId);
        eventPublisher.publishEvent(new UserAccountChangedEvent(userId));
    }
}
