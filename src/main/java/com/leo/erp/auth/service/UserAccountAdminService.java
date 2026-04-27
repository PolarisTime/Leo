package com.leo.erp.auth.service;

import com.leo.erp.auth.config.AuthProperties;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.mapper.UserAccountAdminMapper;
import com.leo.erp.auth.web.dto.TotpEnableRequest;
import com.leo.erp.auth.web.dto.LoginNameAvailabilityResponse;
import com.leo.erp.auth.web.dto.TotpSetupResponse;
import com.leo.erp.auth.web.dto.UserAccountCreateResponse;
import com.leo.erp.auth.web.dto.UserAccountAdminRequest;
import com.leo.erp.auth.web.dto.UserAccountAdminResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.jwt.AuthenticatedUserCacheService;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.system.department.domain.entity.Department;
import com.leo.erp.system.department.repository.DepartmentRepository;
import com.leo.erp.system.norule.service.SystemSwitchService;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.security.SecureRandom;
import java.time.Duration;

@Service
public class UserAccountAdminService {

    private static final Set<String> ALLOWED_DATA_SCOPES = Set.of("全部数据", "全部", "本部门", "本人");
    private static final String UPPERCASE_PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE_PASSWORD_CHARS = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGIT_PASSWORD_CHARS = "0123456789";
    private static final String ALL_PASSWORD_CHARS = UPPERCASE_PASSWORD_CHARS + LOWERCASE_PASSWORD_CHARS + DIGIT_PASSWORD_CHARS;
    private static final int GENERATED_PASSWORD_LENGTH = 8;
    private static final String LOGIN_NAME_OWNER_CACHE_PREFIX = "auth:user:login-name:owner:";
    private static final Duration LOGIN_NAME_OWNER_CACHE_TTL = Duration.ofMinutes(10);
    private static final Long LOGIN_NAME_NOT_FOUND = 0L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserAccountRepository repository;
    private final SnowflakeIdGenerator idGenerator;
    private final PasswordEncoder passwordEncoder;
    private final UserAccountAdminMapper userAccountAdminMapper;
    private final TotpService totpService;
    private final UserRoleBindingService userRoleBindingService;
    private final PermissionService permissionService;
    private final AuthProperties authProperties;
    private final SystemSwitchService systemSwitchService;
    private final AuthenticatedUserCacheService authenticatedUserCacheService;
    private final DashboardSummaryService dashboardSummaryService;
    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final DepartmentRepository departmentRepository;

    @Autowired
    public UserAccountAdminService(
            UserAccountRepository repository,
            SnowflakeIdGenerator idGenerator,
            PasswordEncoder passwordEncoder,
            UserAccountAdminMapper userAccountAdminMapper,
            TotpService totpService,
            UserRoleBindingService userRoleBindingService,
            PermissionService permissionService,
            AuthProperties authProperties,
            SystemSwitchService systemSwitchService,
            AuthenticatedUserCacheService authenticatedUserCacheService,
            DashboardSummaryService dashboardSummaryService,
            RedisJsonCacheSupport redisJsonCacheSupport,
            DepartmentRepository departmentRepository
    ) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.passwordEncoder = passwordEncoder;
        this.userAccountAdminMapper = userAccountAdminMapper;
        this.totpService = totpService;
        this.userRoleBindingService = userRoleBindingService;
        this.permissionService = permissionService;
        this.authProperties = authProperties;
        this.systemSwitchService = systemSwitchService;
        this.authenticatedUserCacheService = authenticatedUserCacheService;
        this.dashboardSummaryService = dashboardSummaryService;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public Page<UserAccountAdminResponse> page(PageQuery query, String keyword, String status) {
        Specification<UserAccount> spec = Specs.<UserAccount>notDeleted()
                .and(Specs.keywordLike(keyword, "loginName", "userName", "roleName", "mobile", "departmentName"));
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, criteriaQuery, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), toStatus(status)));
        }
        return repository.findAll(spec, query.toPageable("id"))
                .map(this::toResponseWithRoles);
    }

    @Transactional(readOnly = true)
    public UserAccountAdminResponse detail(Long id) {
        return toResponseWithRoles(getEntity(id));
    }

    @Transactional(readOnly = true)
    public LoginNameAvailabilityResponse checkLoginNameAvailability(String loginName, Long excludeUserId) {
        String normalizedLoginName = normalizeLoginName(loginName);
        return resolveLoginNameAvailability(normalizedLoginName, excludeUserId);
    }

    @Transactional
    public UserAccountCreateResponse create(UserAccountAdminRequest request) {
        String normalizedLoginName = normalizeLoginName(request.loginName());
        ensureLoginNameAvailable(normalizedLoginName, null);
        String initialPassword = resolveInitialPassword(request.password());
        UserAccount entity = new UserAccount();
        entity.setId(idGenerator.nextId());
        entity.setPasswordHash(passwordEncoder.encode(initialPassword));
        entity.setRequireTotpSetup(shouldRequireTotpSetupForNewUser());
        return new UserAccountCreateResponse(saveWithRoles(entity, request), initialPassword);
    }

    @Transactional
    public UserAccountAdminResponse update(Long id, UserAccountAdminRequest request) {
        UserAccount entity = getEntity(id);
        String normalizedLoginName = normalizeLoginName(request.loginName());
        ensureLoginNameAvailable(normalizedLoginName, entity.getId());
        return saveWithRoles(entity, request);
    }

    @Transactional
    public void delete(Long id) {
        UserAccount entity = getEntity(id);
        entity.setDeletedFlag(Boolean.TRUE);
        repository.save(entity);
        evictLoginNameCache(entity.getLoginName());
        permissionService.evictCache(entity.getId());
        permissionService.evictDepartmentUserCache(entity.getDepartmentId());
        evictAuthenticatedUser(entity.getId());
        evictDashboard(entity.getId());
    }

    // --- 2FA 管理 ---

    @Transactional
    public TotpSetupResponse setup2fa(Long id) {
        UserAccount entity = getEntity(id);

        // 生成新密钥并加密存储（但不启用）
        String secret = totpService.generateSecret();
        entity.setTotpSecret(totpService.encryptSecret(secret));
        entity.setTotpEnabled(Boolean.FALSE);
        repository.save(entity);
        evictAuthenticatedUser(entity.getId());
        evictDashboard(entity.getId());

        // 生成 QR 码
        byte[] qrBytes = totpService.generateQrCodeImage(secret, entity.getLoginName());
        String qrBase64 = Base64.getEncoder().encodeToString(qrBytes);

        return new TotpSetupResponse(qrBase64, secret);
    }

    @Transactional
    public UserAccountAdminResponse enable2fa(Long id, TotpEnableRequest request) {
        UserAccount entity = getEntity(id);

        if (entity.getTotpSecret() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请先生成2FA密钥");
        }

        // 验证 TOTP 码
        String secret = totpService.decryptSecret(entity.getTotpSecret());
        if (!totpService.verifyCode(secret, request.totpCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "验证码错误或已过期");
        }

        entity.setTotpEnabled(Boolean.TRUE);
        entity.setRequireTotpSetup(Boolean.FALSE);
        UserAccount saved = repository.save(entity);
        evictAuthenticatedUser(saved.getId());
        evictDashboard(saved.getId());
        return toResponseWithRoles(saved);
    }

    @Transactional
    public UserAccountAdminResponse disable2fa(Long id) {
        UserAccount entity = getEntity(id);
        entity.setTotpSecret(null);
        entity.setTotpEnabled(Boolean.FALSE);
        UserAccount saved = repository.save(entity);
        evictAuthenticatedUser(saved.getId());
        evictDashboard(saved.getId());
        return toResponseWithRoles(saved);
    }

    // ---

    private UserAccount getEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private UserAccountAdminResponse toResponseWithRoles(UserAccount entity) {
        List<RoleSetting> roles = userRoleBindingService.resolveRolesForUser(entity.getId());
        UserAccountAdminResponse response = userAccountAdminMapper.toResponse(entity);
        String permissionSummary = permissionService.getPermissionSummaryForRoles(roles);
        return new UserAccountAdminResponse(
                response.id(), response.loginName(), response.userName(),
                response.mobile(), response.departmentId(), response.departmentName(),
                splitRoles(userRoleBindingService.joinRoleNames(roles)),
                response.dataScope(), permissionSummary,
                response.lastLoginDate(), response.status(), response.remark(),
                Boolean.TRUE.equals(entity.getTotpEnabled())
        );
    }

    private UserAccountAdminResponse saveWithRoles(UserAccount entity, UserAccountAdminRequest request) {
        String previousLoginName = entity.getLoginName();
        Long previousDepartmentId = entity.getDepartmentId();
        List<RoleSetting> roles = userRoleBindingService.resolveRoles(request.roleNames());
        apply(entity, request, roles);
        try {
            UserAccount saved = repository.save(entity);
            userRoleBindingService.replaceUserRoles(saved.getId(), roles);
            evictLoginNameCache(previousLoginName, saved.getLoginName());
            permissionService.evictCache(saved.getId());
            evictDepartmentUserCaches(previousDepartmentId, saved.getDepartmentId());
            evictAuthenticatedUser(saved.getId());
            evictDashboard(saved.getId());
            return toResponseWithRoles(saved);
        } catch (DataIntegrityViolationException ex) {
            evictLoginNameCache(previousLoginName, entity.getLoginName());
            if (isLoginNameConflict(ex)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "登录账号已存在");
            }
            throw ex;
        }
    }

    private void apply(UserAccount entity, UserAccountAdminRequest request, List<RoleSetting> roles) {
        entity.setLoginName(normalizeLoginName(request.loginName()));
        entity.setUserName(normalizeRequiredValue(request.userName(), "用户姓名"));
        entity.setMobile(normalizeOptionalValue(request.mobile()));
        applyDepartment(entity, request.departmentId());
        entity.setRoleName(userRoleBindingService.joinRoleNames(roles));
        entity.setDataScope(resolveEffectiveDataScope(roles));
        entity.setPermissionSummary("");
        entity.setStatus(toStatus(request.status()));
        entity.setRemark(normalizeOptionalValue(request.remark()));
    }

    private void applyDepartment(UserAccount entity, Long departmentId) {
        if (departmentId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择所属部门");
        }
        if (departmentRepository == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "部门不存在");
        }
        Department department = departmentRepository.findByIdAndDeletedFlagFalse(departmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "部门不存在"));
        if (!"正常".equals(department.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "部门已禁用");
        }
        entity.setDepartmentId(department.getId());
        entity.setDepartmentName(department.getDepartmentName());
    }

    private boolean shouldRequireTotpSetupForNewUser() {
        return systemSwitchService != null && systemSwitchService.shouldForceUserTotpOnFirstLogin();
    }

    private UserStatus toStatus(String value) {
        String normalized = normalizeOptionalValue(value);
        if (normalized == null || "正常".equals(normalized)) {
            return UserStatus.NORMAL;
        }
        if ("禁用".equals(normalized)) {
            return UserStatus.DISABLED;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "用户状态不合法");
    }

    private List<String> splitRoles(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String resolveInitialPassword(String password) {
        String normalizedPassword = normalizeOptionalValue(password);
        if (normalizedPassword != null) {
            return normalizedPassword;
        }
        return generateRandomInitialPassword();
    }

    private String normalizeLoginName(String loginName) {
        String normalized = normalizeRequiredValue(loginName, "登录账号");
        if (normalized.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "登录账号长度不能超过64");
        }
        return normalized;
    }

    private void ensureLoginNameAvailable(String loginName, Long currentUserId) {
        if (!resolveLoginNameAvailability(loginName, currentUserId).available()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "登录账号已存在");
        }
        repository.findByLoginName(loginName)
                .filter(existing -> !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, "登录账号已存在");
                });
    }

    private LoginNameAvailabilityResponse resolveLoginNameAvailability(String loginName, Long excludeUserId) {
        Long ownerUserId = loadLoginNameOwnerId(loginName);
        boolean available = ownerUserId == null
                || LOGIN_NAME_NOT_FOUND.equals(ownerUserId)
                || ownerUserId.equals(excludeUserId);
        return new LoginNameAvailabilityResponse(available, available ? null : "登录账号已存在");
    }

    private Long loadLoginNameOwnerId(String loginName) {
        if (redisJsonCacheSupport == null) {
            return repository.findByLoginName(loginName)
                    .map(UserAccount::getId)
                    .orElse(LOGIN_NAME_NOT_FOUND);
        }
        return redisJsonCacheSupport.getOrLoad(
                loginNameOwnerCacheKey(loginName),
                LOGIN_NAME_OWNER_CACHE_TTL,
                Long.class,
                () -> repository.findByLoginName(loginName)
                        .map(UserAccount::getId)
                        .orElse(LOGIN_NAME_NOT_FOUND)
        );
    }

    private void evictLoginNameCache(String... loginNames) {
        if (redisJsonCacheSupport == null || loginNames == null || loginNames.length == 0) {
            return;
        }
        List<String> keys = Arrays.stream(loginNames)
                .map(this::normalizeOptionalValue)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .map(this::loginNameOwnerCacheKey)
                .toList();
        if (!keys.isEmpty()) {
            redisJsonCacheSupport.delete(keys);
        }
    }

    private String resolveEffectiveDataScope(List<RoleSetting> roles) {
        String effective = "本人";
        int effectiveRank = dataScopeRank(effective);
        for (RoleSetting role : roles) {
            String roleDataScope = normalizeDataScope(role.getDataScope());
            int roleRank = dataScopeRank(roleDataScope);
            if (roleRank > effectiveRank) {
                effective = roleDataScope;
                effectiveRank = roleRank;
            }
        }
        return effective;
    }

    private String loginNameOwnerCacheKey(String loginName) {
        return LOGIN_NAME_OWNER_CACHE_PREFIX + loginName;
    }

    private boolean isLoginNameConflict(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && (message.contains("sys_user_login_name_key") || message.contains("login_name"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String normalizeDataScope(String value) {
        String normalized = normalizeOptionalValue(value);
        if (normalized == null) {
            return "本人";
        }
        if (!ALLOWED_DATA_SCOPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "数据范围不合法");
        }
        return "全部".equals(normalized) ? "全部数据" : normalized;
    }

    private int dataScopeRank(String value) {
        return switch (normalizeDataScope(value)) {
            case "全部数据" -> 3;
            case "本部门" -> 2;
            default -> 1;
        };
    }

    private String normalizeRequiredValue(String value, String fieldName) {
        String normalized = normalizeOptionalValue(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + "不能为空");
        }
        return normalized;
    }

    private String normalizeOptionalValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String generateRandomInitialPassword() {
        List<Character> passwordChars = new java.util.ArrayList<>(GENERATED_PASSWORD_LENGTH);
        passwordChars.add(randomChar(UPPERCASE_PASSWORD_CHARS));
        passwordChars.add(randomChar(LOWERCASE_PASSWORD_CHARS));
        passwordChars.add(randomChar(DIGIT_PASSWORD_CHARS));
        while (passwordChars.size() < GENERATED_PASSWORD_LENGTH) {
            passwordChars.add(randomChar(ALL_PASSWORD_CHARS));
        }
        Collections.shuffle(passwordChars, SECURE_RANDOM);
        StringBuilder builder = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        passwordChars.forEach(builder::append);
        return builder.toString();
    }

    private char randomChar(String source) {
        return source.charAt(SECURE_RANDOM.nextInt(source.length()));
    }

    private void evictDashboard(Long userId) {
        if (dashboardSummaryService != null) {
            dashboardSummaryService.evictCache(userId);
        }
    }

    private void evictAuthenticatedUser(Long userId) {
        if (authenticatedUserCacheService != null) {
            authenticatedUserCacheService.evict(userId);
        }
    }

    private void evictDepartmentUserCaches(Long previousDepartmentId, Long nextDepartmentId) {
        permissionService.evictDepartmentUserCache(previousDepartmentId);
        if (!java.util.Objects.equals(previousDepartmentId, nextDepartmentId)) {
            permissionService.evictDepartmentUserCache(nextDepartmentId);
        }
    }
}
