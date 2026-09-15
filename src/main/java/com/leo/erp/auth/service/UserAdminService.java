package com.leo.erp.auth.service;

import com.leo.erp.auth.api.UserAccountChangedEvent;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.UserAccountCreateRequest;
import com.leo.erp.auth.web.dto.UserAccountResponse;
import com.leo.erp.auth.web.dto.UserAccountUpdateRequest;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.jwt.AuthenticatedUserCacheService;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.rbac.repository.SysRolePermissionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;

/**
 * 多用户账号管理服务（管理员视角）：建号、列表、编辑、停用/启用、重置密码与软删除。
 *
 * <p>与 {@link InitialAccountProvisioningService}（首次初始化建号）和 {@link UserAccountService}
 * （个人账号自助维护）职责分离，本服务不参与首启判定，因此首个账号存在后仍可继续建号。</p>
 *
 * <p>安全约束：
 * <ul>
 *   <li>登录账号在未删除账号内唯一，密码始终经 {@link PasswordEncoder} 加密；</li>
 *   <li>禁止删除当前登录账号；</li>
 *   <li>停用/删除账号前保证至少保留一个“启用且具备 {@code *} 权限”的管理员，避免系统锁死。</li>
 * </ul>
 */
@Service
public class UserAdminService {

    private final UserAccountRepository userAccountRepository;
    private final SysRolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final SessionManagementService sessionManagementService;
    private final AuthenticatedUserCacheService authenticatedUserCacheService;
    private final UserAccountStatusApplyService statusApplyService;
    private final ApplicationEventPublisher eventPublisher;

    public UserAdminService(
            UserAccountRepository userAccountRepository,
            SysRolePermissionRepository rolePermissionRepository,
            PasswordEncoder passwordEncoder,
            SnowflakeIdGenerator snowflakeIdGenerator,
            SessionManagementService sessionManagementService,
            AuthenticatedUserCacheService authenticatedUserCacheService,
            UserAccountStatusApplyService statusApplyService,
            ApplicationEventPublisher eventPublisher) {
        this.userAccountRepository = userAccountRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.sessionManagementService = sessionManagementService;
        this.authenticatedUserCacheService = authenticatedUserCacheService;
        this.statusApplyService = statusApplyService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Page<UserAccountResponse> page(PageQuery query, String keyword, String status) {
        Specification<UserAccount> spec = Specs.<UserAccount>notDeleted()
                .and(Specs.keywordLike(keyword, "loginName", "userName", "mobile"))
                .and(Specs.equalValueIfPresent("status", parseOptionalStatus(status)));
        return userAccountRepository.findAll(spec, query.toPageable("id")).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserAccountResponse detail(Long id) {
        return toResponse(requireActiveUser(id));
    }

    @Transactional
    public UserAccountResponse create(UserAccountCreateRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写账号信息");
        }
        String loginName = requireText(request.loginName(), "登录账号不能为空");
        String userName = requireText(request.userName(), "姓名不能为空");
        String password = requireText(request.password(), "密码不能为空");
        if (password.length() < 8) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "密码至少8位");
        }
        if (userAccountRepository.existsByLoginNameAndDeletedFlagFalse(loginName)) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "登录账号已存在");
        }

        UserAccount account = new UserAccount();
        account.setId(snowflakeIdGenerator.nextId());
        account.setLoginName(loginName);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setUserName(userName);
        account.setMobile(normalizeOptional(request.mobile()));
        // 新账号默认无角色、无权限，由管理员按需分配；登录不依赖权限，不会因此被锁死。
        account.setStatus(request.status() == null ? UserStatus.NORMAL : request.status());
        try {
            userAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "登录账号已存在");
        }
        return toResponse(account);
    }

    @Transactional
    public UserAccountResponse update(Long id, UserAccountUpdateRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写账号信息");
        }
        UserAccount account = requireActiveUserForUpdate(id);
        account.setUserName(requireText(request.userName(), "姓名不能为空"));
        account.setMobile(normalizeOptional(request.mobile()));
        if (request.status() != null && request.status() != account.getStatus()) {
            applyStatusChange(account, request.status());
        }
        userAccountRepository.saveAndFlush(account);
        evictCaches(account.getId());
        return toResponse(account);
    }

    @Transactional
    public UserAccountResponse updateStatus(Long id, UserStatus status) {
        if (status == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "账号状态不能为空");
        }
        UserAccount account = requireActiveUserForUpdate(id);
        if (account.getStatus() != status) {
            applyStatusChange(account, status);
            userAccountRepository.saveAndFlush(account);
        }
        evictCaches(account.getId());
        return toResponse(account);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "新密码不能为空");
        }
        if (newPassword.length() < 8) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "密码至少8位");
        }
        UserAccount account = requireActiveUserForUpdate(id);
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        long credentialVersion = account.getCredentialVersion() == null ? 0L : account.getCredentialVersion();
        account.setCredentialVersion(credentialVersion + 1L);
        userAccountRepository.saveAndFlush(account);
        sessionManagementService.revokeActiveSessionsForPasswordChange(id);
        evictCaches(id);
    }

    @Transactional
    public void delete(Long id, Long actingUserId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "用户ID不能为空");
        }
        if (Objects.equals(id, actingUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能删除当前登录账号");
        }
        UserAccount account = requireActiveUserForUpdate(id);
        assertAdminContinuity(account);
        account.setDeletedFlag(true);
        account.setStatus(UserStatus.DISABLED);
        userAccountRepository.saveAndFlush(account);
        sessionManagementService.revokeActiveSessionsForAccountStatusChange(id);
        evictCaches(id);
    }

    private void applyStatusChange(UserAccount account, UserStatus status) {
        if (status == UserStatus.DISABLED) {
            assertAdminContinuity(account);
        }
        statusApplyService.apply(account, status);
        if (status == UserStatus.DISABLED) {
            sessionManagementService.revokeActiveSessionsForAccountStatusChange(account.getId());
        }
    }

    /**
     * 停用/删除前保护：目标账号若为“启用且具 {@code *} 权限”的管理员，
     * 则必须还存在其他同类管理员，避免系统失去全部管理入口。
     */
    private void assertAdminContinuity(UserAccount target) {
        if (!isActiveAdmin(target)) {
            return;
        }
        long activeAdmins = userAccountRepository.countActiveAdminsWithPermission(
                PermissionCodes.WILDCARD, StatusConstants.NORMAL);
        if (activeAdmins <= 1L) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "至少保留一个启用且具备全部权限的管理员账号");
        }
    }

    private boolean isActiveAdmin(UserAccount account) {
        if (account == null || account.getStatus() != UserStatus.NORMAL || account.getId() == null) {
            return false;
        }
        return rolePermissionRepository
                .findPermissionCodesByUserId(account.getId(), StatusConstants.NORMAL)
                .contains(PermissionCodes.WILDCARD);
    }

    private UserAccount requireActiveUser(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "用户ID不能为空");
        }
        return userAccountRepository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账号不存在"));
    }

    private UserAccount requireActiveUserForUpdate(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "用户ID不能为空");
        }
        return userAccountRepository.findByIdAndDeletedFlagFalseForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账号不存在"));
    }

    private UserStatus parseOptionalStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        try {
            return UserStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "账号状态不合法");
        }
    }

    private String requireText(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
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

    private UserAccountResponse toResponse(UserAccount account) {
        return new UserAccountResponse(
                account.getId(),
                account.getLoginName(),
                account.getUserName(),
                account.getMobile(),
                account.getStatus(),
                account.getLastLoginDate(),
                account.getRemark()
        );
    }

    private void evictCaches(Long userId) {
        authenticatedUserCacheService.evict(userId);
        eventPublisher.publishEvent(new UserAccountChangedEvent(userId));
    }
}
