package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.mapper.UserAccountAdminMapper;
import com.leo.erp.auth.web.dto.LoginNameAvailabilityResponse;
import com.leo.erp.auth.web.dto.UserAccountCreateResponse;
import com.leo.erp.auth.web.dto.UserAccountAdminRequest;
import com.leo.erp.auth.web.dto.UserAccountAdminResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.security.permission.CasbinPolicyStore;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.system.role.domain.entity.RoleConflict;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RoleConflictRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class UserAccountAdminService {

    private static final String ADMIN_ROLE_CODE = "ADMIN";

    private final UserAccountRepository repository;
    private final SnowflakeIdGenerator idGenerator;
    private final PasswordEncoder passwordEncoder;
    private final UserAccountAdminMapper userAccountAdminMapper;
    private final UserRoleBindingService userRoleBindingService;
    private final PermissionService permissionService;
    private final UserAccountValidationService validationService;
    private final UserAccountCacheService cacheService;
    private final RoleConflictRepository roleConflictRepository;
    private final CasbinPolicyStore casbinPolicyStore;

    @Autowired
    public UserAccountAdminService(
            UserAccountRepository repository,
            SnowflakeIdGenerator idGenerator,
            PasswordEncoder passwordEncoder,
            UserAccountAdminMapper userAccountAdminMapper,
            UserRoleBindingService userRoleBindingService,
            PermissionService permissionService,
            UserAccountValidationService validationService,
            UserAccountCacheService cacheService,
            RoleConflictRepository roleConflictRepository,
            CasbinPolicyStore casbinPolicyStore) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.passwordEncoder = passwordEncoder;
        this.userAccountAdminMapper = userAccountAdminMapper;
        this.userRoleBindingService = userRoleBindingService;
        this.permissionService = permissionService;
        this.validationService = validationService;
        this.cacheService = cacheService;
        this.roleConflictRepository = roleConflictRepository;
        this.casbinPolicyStore = casbinPolicyStore;
    }

    @Transactional(readOnly = true)
    public Page<UserAccountAdminResponse> page(PageQuery query, String keyword, String status) {
        Specification<UserAccount> spec = Specs.<UserAccount>notDeleted()
                .and(Specs.keywordLike(keyword, "loginName", "userName", "mobile", "departmentName"));
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
        assertNotLastActiveAdmin(entity, List.of(), UserStatus.DISABLED, true);
        entity.setDeletedFlag(true);
        repository.save(entity);
        casbinPolicyStore.replaceUserRoles(String.valueOf(entity.getId()), List.of());
        cacheService.evictLoginNameCache(entity.getLoginName());
        cacheService.evictPermissionCache(entity.getId());
        cacheService.evictAuthenticatedUser(entity.getId());
        cacheService.evictDashboard(entity.getId());
    }

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
                roles.stream().map(RoleSetting::getRoleName).toList(),
                roles.stream().map(RoleSetting::getId).toList(),
                permissionSummary,
                response.lastLoginDate(), response.status(), response.remark()
        );
    }

    private UserAccountAdminResponse saveWithRoles(UserAccount entity, UserAccountAdminRequest request) {
        String previousLoginName = entity.getLoginName();
        boolean roleUpdateRequested = hasRolePayload(request);
        List<RoleSetting> roles = resolveRolesFromRequest(entity, request);
        if (roleUpdateRequested) {
            userRoleBindingService.assertCurrentPrincipalCanBindRoles(entity.getId(), roles);
        }
        assertNoRoleConflict(roles);
        UserStatus nextStatus = validationService.toStatus(request.status());
        assertNotLastActiveAdmin(entity, roles, nextStatus, false);
        apply(entity, request);
        try {
            UserAccount saved = repository.save(entity);
            if (roleUpdateRequested) {
                userRoleBindingService.replaceUserRolesWithinCurrentPrincipalBounds(saved.getId(), roles);
            }
            cacheService.evictLoginNameCache(previousLoginName, saved.getLoginName());
            cacheService.evictPermissionCache(saved.getId());
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

    private boolean hasRolePayload(UserAccountAdminRequest request) {
        return request.roleIds() != null || request.roleNames() != null;
    }

    private void assertNoRoleConflict(List<RoleSetting> roles) {
        if (roles.size() < 2) return;
        List<Long> roleIds = roles.stream().map(RoleSetting::getId).toList();
        List<RoleConflict> conflicts = roleConflictRepository.findConflictsByRoleIds(roleIds);
        if (conflicts.isEmpty()) return;
        for (RoleConflict conflict : conflicts) {
            for (RoleSetting role : roles) {
                if (role.getId().equals(conflict.getConflictRoleId())) {
                    RoleSetting conflictRole = roles.stream()
                            .filter(r -> r.getId().equals(conflict.getRoleId())).findFirst().orElse(null);
                    if (conflictRole != null) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                                "角色 \"" + conflictRole.getRoleName() + "\" 与 \"" + role.getRoleName() + "\" 互斥，不能同时选择");
                    }
                }
            }
        }
    }

    private List<RoleSetting> resolveRolesFromRequest(UserAccount entity, UserAccountAdminRequest request) {
        if (request.roleIds() != null && !request.roleIds().isEmpty()) {
            return userRoleBindingService.resolveRolesByIds(request.roleIds());
        }
        if (request.roleIds() == null && request.roleNames() == null && entity.getId() != null) {
            return userRoleBindingService.resolveRolesForUser(entity.getId());
        }
        return userRoleBindingService.resolveRoles(request.roleNames());
    }

    private void apply(UserAccount entity, UserAccountAdminRequest request) {
        entity.setLoginName(validationService.normalizeLoginName(request.loginName()));
        entity.setUserName(validationService.normalizeRequiredValue(request.userName(), "用户姓名"));
        entity.setMobile(validationService.normalizeOptionalValue(request.mobile()));
        validationService.applyDepartment(entity, request.departmentId());
        entity.setPermissionSummary("");
        entity.setStatus(validationService.toStatus(request.status()));
        entity.setRemark(validationService.normalizeOptionalValue(request.remark()));
    }

    private void assertNotLastActiveAdmin(
            UserAccount entity,
            List<RoleSetting> nextRoles,
            UserStatus nextStatus,
            boolean deleting) {
        if (entity.getId() == null || !currentlyHasAdminRole(entity.getId())) {
            return;
        }
        boolean keepsAdmin = !deleting
                && nextStatus == UserStatus.NORMAL
                && nextRoles.stream().anyMatch(this::isAdminRole);
        if (keepsAdmin) {
            return;
        }
        long otherActiveAdmins = casbinPolicyStore
                .countActiveUsersByRoleExcluding(ADMIN_ROLE_CODE, entity.getId());
        if (otherActiveAdmins == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "至少保留一个正常状态的系统管理员");
        }
    }

    private boolean currentlyHasAdminRole(Long userId) {
        return userRoleBindingService.resolveRolesForUser(userId).stream().anyMatch(this::isAdminRole);
    }

    private boolean isAdminRole(RoleSetting role) {
        return role != null && ADMIN_ROLE_CODE.equals(normalizeRoleCode(role.getRoleCode()));
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

    private String normalizeRoleCode(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
