package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.mapper.UserAccountAdminMapper;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.LoginNameAvailabilityResponse;
import com.leo.erp.auth.web.dto.UserAccountAdminRequest;
import com.leo.erp.auth.web.dto.UserAccountAdminResponse;
import com.leo.erp.auth.web.dto.UserAccountCreateResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountAdminService {

    private final UserAccountRepository repository;
    private final SnowflakeIdGenerator idGenerator;
    private final PasswordEncoder passwordEncoder;
    private final UserAccountAdminMapper userAccountAdminMapper;
    private final UserAccountValidationService validationService;
    private final UserAccountCacheService cacheService;

    public UserAccountAdminService(
            UserAccountRepository repository,
            SnowflakeIdGenerator idGenerator,
            PasswordEncoder passwordEncoder,
            UserAccountAdminMapper userAccountAdminMapper,
            UserAccountValidationService validationService,
            UserAccountCacheService cacheService) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.passwordEncoder = passwordEncoder;
        this.userAccountAdminMapper = userAccountAdminMapper;
        this.validationService = validationService;
        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    public Page<UserAccountAdminResponse> page(PageQuery query, String keyword, String status) {
        Specification<UserAccount> spec = Specs.<UserAccount>notDeleted()
                .and(Specs.keywordLike(keyword, "loginName", "userName", "mobile"));
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, criteriaQuery, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), validationService.toStatus(status)));
        }
        return repository.findAll(spec, query.toPageable("id"))
                .map(userAccountAdminMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserAccountAdminResponse detail(Long id) {
        return userAccountAdminMapper.toResponse(getEntity(id));
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
        return new UserAccountCreateResponse(save(entity, request), initialPassword);
    }

    @Transactional
    public UserAccountAdminResponse update(Long id, UserAccountAdminRequest request) {
        UserAccount entity = getEntity(id);
        String normalizedLoginName = validationService.normalizeLoginName(request.loginName());
        validationService.ensureLoginNameAvailable(normalizedLoginName, entity.getId());
        return save(entity, request);
    }

    @Transactional
    public void delete(Long id) {
        UserAccount entity = getEntity(id);
        assertSystemRemainsAccessible(entity, UserStatus.DISABLED, true);
        entity.setDeletedFlag(true);
        repository.save(entity);
        evictCaches(entity, entity.getLoginName());
    }

    private UserAccount getEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private UserAccountAdminResponse save(UserAccount entity, UserAccountAdminRequest request) {
        String previousLoginName = entity.getLoginName();
        UserStatus nextStatus = validationService.toStatus(request.status());
        assertSystemRemainsAccessible(entity, nextStatus, false);
        apply(entity, request, nextStatus);
        try {
            UserAccount saved = repository.save(entity);
            evictCaches(saved, previousLoginName);
            return userAccountAdminMapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            cacheService.evictLoginNameCache(previousLoginName, entity.getLoginName());
            if (isLoginNameConflict(ex)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "登录账号已存在");
            }
            throw ex;
        }
    }

    private void apply(UserAccount entity, UserAccountAdminRequest request, UserStatus nextStatus) {
        entity.setLoginName(validationService.normalizeLoginName(request.loginName()));
        entity.setUserName(validationService.normalizeRequiredValue(request.userName(), "用户姓名"));
        entity.setMobile(validationService.normalizeOptionalValue(request.mobile()));
        entity.setStatus(nextStatus);
        entity.setRemark(validationService.normalizeOptionalValue(request.remark()));
    }

    /** 账号可用性属于系统不变量，不依赖授权模型。 */
    private void assertSystemRemainsAccessible(UserAccount entity, UserStatus nextStatus, boolean deleting) {
        if (entity.getId() == null || entity.getStatus() != UserStatus.NORMAL) {
            return;
        }
        if (!deleting && nextStatus == UserStatus.NORMAL) {
            return;
        }
        if (repository.findActiveUsersForUpdate(UserStatus.NORMAL).size() <= 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "系统至少需要保留一个正常状态的账号");
        }
    }

    private void evictCaches(UserAccount entity, String previousLoginName) {
        cacheService.evictLoginNameCache(previousLoginName, entity.getLoginName());
        cacheService.evictAuthenticatedUser(entity.getId());
        cacheService.evictDashboard(entity.getId());
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
