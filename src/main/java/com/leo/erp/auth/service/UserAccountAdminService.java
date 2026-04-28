package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
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
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.permission.PermissionService;
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
import java.util.List;

@Service
public class UserAccountAdminService {

    private final UserAccountRepository repository;
    private final SnowflakeIdGenerator idGenerator;
    private final PasswordEncoder passwordEncoder;
    private final UserAccountAdminMapper userAccountAdminMapper;
    private final TotpService totpService;
    private final UserRoleBindingService userRoleBindingService;
    private final PermissionService permissionService;
    private final UserAccountValidationService validationService;
    private final UserAccountCacheService cacheService;

    @Autowired
    public UserAccountAdminService(
            UserAccountRepository repository,
            SnowflakeIdGenerator idGenerator,
            PasswordEncoder passwordEncoder,
            UserAccountAdminMapper userAccountAdminMapper,
            TotpService totpService,
            UserRoleBindingService userRoleBindingService,
            PermissionService permissionService,
            UserAccountValidationService validationService,
            UserAccountCacheService cacheService) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.passwordEncoder = passwordEncoder;
        this.userAccountAdminMapper = userAccountAdminMapper;
        this.totpService = totpService;
        this.userRoleBindingService = userRoleBindingService;
        this.permissionService = permissionService;
        this.validationService = validationService;
        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    public Page<UserAccountAdminResponse> page(PageQuery query, String keyword, String status) {
        Specification<UserAccount> spec = Specs.<UserAccount>notDeleted()
                .and(Specs.keywordLike(keyword, "loginName", "userName", "roleName", "mobile", "departmentName"));
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, criteriaQuery, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), validationService.toStatus(status)));
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
        String normalizedLoginName = validationService.normalizeLoginName(loginName);
        return validationService.resolveLoginNameAvailability(normalizedLoginName, excludeUserId);
    }

    @Transactional
    public UserAccountCreateResponse create(UserAccountAdminRequest request) {
        String normalizedLoginName = validationService.normalizeLoginName(request.loginName());
        validationService.ensureLoginNameAvailable(normalizedLoginName, null);
        String initialPassword = validationService.resolveInitialPassword(request.password());
        UserAccount entity = new UserAccount();
        entity.setId(idGenerator.nextId());
        entity.setPasswordHash(passwordEncoder.encode(initialPassword));
        entity.setRequireTotpSetup(validationService.shouldRequireTotpSetupForNewUser());
        return new UserAccountCreateResponse(saveWithRoles(entity, request), initialPassword);
    }

    @Transactional
    public UserAccountAdminResponse update(Long id, UserAccountAdminRequest request) {
        UserAccount entity = getEntity(id);
        String normalizedLoginName = validationService.normalizeLoginName(request.loginName());
        validationService.ensureLoginNameAvailable(normalizedLoginName, entity.getId());
        return saveWithRoles(entity, request);
    }

    @Transactional
    public void delete(Long id) {
        UserAccount entity = getEntity(id);
        entity.setDeletedFlag(Boolean.TRUE);
        repository.save(entity);
        cacheService.evictLoginNameCache(entity.getLoginName());
        cacheService.evictPermissionCache(entity.getId());
        cacheService.evictDepartmentUserCaches(entity.getDepartmentId(), entity.getDepartmentId());
        cacheService.evictAuthenticatedUser(entity.getId());
        cacheService.evictDashboard(entity.getId());
    }

    // --- 2FA 管理 ---

    @Transactional
    public TotpSetupResponse setup2fa(Long id) {
        UserAccount entity = getEntity(id);
        String secret = totpService.generateSecret();
        entity.setTotpSecret(totpService.encryptSecret(secret));
        entity.setTotpEnabled(Boolean.FALSE);
        repository.save(entity);
        cacheService.evictAuthenticatedUser(entity.getId());
        cacheService.evictDashboard(entity.getId());

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

        String secret = totpService.decryptSecret(entity.getTotpSecret());
        if (!totpService.verifyCode(secret, request.totpCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "验证码错误或已过期");
        }

        entity.setTotpEnabled(Boolean.TRUE);
        entity.setRequireTotpSetup(Boolean.FALSE);
        UserAccount saved = repository.save(entity);
        cacheService.evictAuthenticatedUser(saved.getId());
        cacheService.evictDashboard(saved.getId());
        return toResponseWithRoles(saved);
    }

    @Transactional
    public UserAccountAdminResponse disable2fa(Long id) {
        UserAccount entity = getEntity(id);
        entity.setTotpSecret(null);
        entity.setTotpEnabled(Boolean.FALSE);
        UserAccount saved = repository.save(entity);
        cacheService.evictAuthenticatedUser(saved.getId());
        cacheService.evictDashboard(saved.getId());
        return toResponseWithRoles(saved);
    }

    // --- 内部方法 ---

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
            cacheService.evictLoginNameCache(previousLoginName, saved.getLoginName());
            cacheService.evictPermissionCache(saved.getId());
            cacheService.evictDepartmentUserCaches(previousDepartmentId, saved.getDepartmentId());
            cacheService.evictAuthenticatedUser(saved.getId());
            cacheService.evictDashboard(saved.getId());
            return toResponseWithRoles(saved);
        } catch (DataIntegrityViolationException ex) {
            cacheService.evictLoginNameCache(previousLoginName, entity.getLoginName());
            if (isLoginNameConflict(ex)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "登录账号已存在");
            }
            throw ex;
        }
    }

    private void apply(UserAccount entity, UserAccountAdminRequest request, List<RoleSetting> roles) {
        entity.setLoginName(validationService.normalizeLoginName(request.loginName()));
        entity.setUserName(validationService.normalizeRequiredValue(request.userName(), "用户姓名"));
        entity.setMobile(validationService.normalizeOptionalValue(request.mobile()));
        validationService.applyDepartment(entity, request.departmentId());
        entity.setRoleName(userRoleBindingService.joinRoleNames(roles));
        entity.setDataScope(validationService.resolveEffectiveDataScope(roles));
        entity.setPermissionSummary("");
        entity.setStatus(validationService.toStatus(request.status()));
        entity.setRemark(validationService.normalizeOptionalValue(request.remark()));
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
}
