package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.ApiKey;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.ApiKeyStatus;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.ApiKeyRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.support.ApiKeySupport;
import com.leo.erp.auth.web.dto.ApiKeyActionOptionResponse;
import com.leo.erp.auth.web.dto.ApiKeyRequest;
import com.leo.erp.auth.web.dto.ApiKeyResourceOptionResponse;
import com.leo.erp.auth.web.dto.ApiKeyResponse;
import com.leo.erp.auth.web.dto.ApiKeyUserOptionResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ApiKeyAdminService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String EXPIRED_STATUS = "已过期";
    private static final Set<String> ALLOWED_STATUS = Set.of(
            ApiKeyStatus.ACTIVE.displayName(),
            EXPIRED_STATUS,
            ApiKeyStatus.DISABLED.displayName()
    );
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final long MAX_EXPIRE_DAYS = 3650L;

    private final ApiKeyRepository apiKeyRepository;
    private final UserAccountRepository userAccountRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final PermissionService permissionService;

    public ApiKeyAdminService(ApiKeyRepository apiKeyRepository,
                              UserAccountRepository userAccountRepository,
                              SnowflakeIdGenerator idGenerator,
                              PermissionService permissionService) {
        this.apiKeyRepository = apiKeyRepository;
        this.userAccountRepository = userAccountRepository;
        this.idGenerator = idGenerator;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public Page<ApiKeyResponse> page(PageQuery query, String keyword, Long userId, String status, String usageScope) {
        SecurityPrincipal operator = currentPrincipal();
        boolean adminOperator = isAdmin(operator);
        Specification<ApiKey> spec = Specs.<ApiKey>notDeleted()
                .and(Specs.keywordLike(keyword, "keyName", "keyPrefix"));
        if (userId != null && !adminOperator && !operator.id().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看自己的 API Key");
        }
        Long effectiveUserId = adminOperator ? userId : operator.id();
        if (effectiveUserId != null) {
            spec = spec.and((root, criteriaQuery, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("userId"), effectiveUserId));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and(buildStatusSpec(status));
        }
        if (usageScope != null && !usageScope.isBlank()) {
            String normalizedUsageScope = normalizeUsageScope(usageScope);
            spec = spec.and((root, criteriaQuery, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("usageScope"), normalizedUsageScope));
        }

        Page<ApiKey> page = apiKeyRepository.findAll(spec, query.toPageable("id"));
        Map<Long, UserAccount> userMap = loadUserMap(page.getContent());

        return page.map(entity -> toResponse(entity, userMap, null));
    }

    @Transactional(readOnly = true)
    public ApiKeyResponse detail(Long id) {
        ApiKey entity = getEntity(id);
        assertCurrentPrincipalCanManageApiKey(entity, "只能查看自己的 API Key");
        Map<Long, UserAccount> userMap = loadUserMap(List.of(entity));
        return toResponse(entity, userMap, null);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyUserOptionResponse> listAvailableUsers(String keyword) {
        Specification<UserAccount> spec = Specs.<UserAccount>notDeleted()
                .and((root, criteriaQuery, criteriaBuilder) ->
                        criteriaBuilder.equal(root.get("status"), UserStatus.NORMAL))
                .and((root, criteriaQuery, criteriaBuilder) ->
                        criteriaBuilder.isTrue(root.get("totpEnabled")))
                .and((root, criteriaQuery, criteriaBuilder) ->
                        criteriaBuilder.isNotNull(root.get("totpSecret")))
                .and(Specs.keywordLike(keyword, "loginName", "userName", "mobile"));
        return userAccountRepository.findAll(
                        spec,
                        PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "loginName"))
                )
                .stream()
                .map(user -> new ApiKeyUserOptionResponse(
                        user.getId(),
                        user.getLoginName(),
                        user.getUserName(),
                        user.getMobile()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResourceOptionResponse> listResourceOptions() {
        return ResourcePermissionCatalog.entries().stream()
                .map(entry -> new ApiKeyResourceOptionResponse(entry.code(), entry.title(), entry.group()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApiKeyActionOptionResponse> listActionOptions() {
        return ResourcePermissionCatalog.actionOptions().stream()
                .map(option -> new ApiKeyActionOptionResponse(option.code(), option.title()))
                .toList();
    }

    @Transactional
    public ApiKeyResponse generate(Long userId, ApiKeyRequest request) {
        SecurityPrincipal operator = currentPrincipal();
        boolean adminOperator = isAdmin(operator);
        if (!adminOperator && !operator.id().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能为当前登录用户生成 API Key");
        }

        UserAccount user = userAccountRepository.findByIdAndDeletedFlagFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "目标用户不存在"));
        if (user.getStatus() != UserStatus.NORMAL) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "目标用户已禁用，不能生成 API Key");
        }
        if (!Boolean.TRUE.equals(user.getTotpEnabled()) || user.getTotpSecret() == null || user.getTotpSecret().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "目标用户未启用2FA，不能生成 API Key");
        }
        List<String> allowedResources = normalizeAllowedResourceList(request.allowedResources());
        List<String> allowedActions = normalizeAllowedActionList(request.allowedActions());
        ensurePermissionUpperBound(userId, allowedResources, allowedActions, "目标用户权限不足，不能生成包含该资源或动作的 API Key");
        if (!operator.id().equals(userId)) {
            ensurePermissionUpperBound(operator.id(), allowedResources, allowedActions, "当前操作者权限不足，不能生成包含该资源或动作的 API Key");
        }

        String rawKey = "leo_" + Base64.getUrlEncoder().withoutPadding().encodeToString(
                generateRandomBytes(32));
        String keyHash = ApiKeySupport.hashKey(rawKey);
        String keyPrefix = rawKey.substring(0, PrecisionConstants.ID_PREFIX_LENGTH);

        ApiKey entity = new ApiKey();
        entity.setId(idGenerator.nextId());
        entity.setUserId(userId);
        entity.setKeyName(normalizeKeyName(request.keyName()));
        entity.setUsageScope(normalizeUsageScope(request.usageScope()));
        entity.setAllowedResources(ApiKeySupport.joinAllowedResources(allowedResources));
        entity.setAllowedActions(ApiKeySupport.joinAllowedActions(allowedActions));
        entity.setKeyPrefix(keyPrefix);
        entity.setKeyHash(keyHash);
        entity.setStatus(ApiKeyStatus.ACTIVE);

        Long expireDays = normalizeExpireDays(request.expireDays());
        if (expireDays != null) {
            entity.setExpiresAt(LocalDateTime.now().plusDays(expireDays));
        }

        apiKeyRepository.save(entity);

        return toResponse(entity, Map.of(userId, user), rawKey);
    }

    @Transactional
    public void revoke(Long id) {
        ApiKey entity = getEntity(id);
        assertCurrentPrincipalCanManageApiKey(entity, "只能禁用自己的 API Key");
        String displayStatus = resolveStatus(entity);
        if (ApiKeyStatus.DISABLED.displayName().equals(displayStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "API Key 已被禁用");
        }
        if (EXPIRED_STATUS.equals(displayStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "API Key 已过期");
        }
        entity.setStatus(ApiKeyStatus.DISABLED);
        apiKeyRepository.save(entity);
    }

    private ApiKeyResponse toResponse(ApiKey entity, Map<Long, UserAccount> userMap, String rawKey) {
        UserAccount user = userMap.get(entity.getUserId());
        String loginName = user != null ? user.getLoginName() : String.valueOf(entity.getUserId());
        String userName = user != null ? user.getUserName() : "--";

        return new ApiKeyResponse(
                entity.getId(),
                entity.getUserId(),
                loginName,
                userName,
                entity.getKeyName(),
                entity.getUsageScope(),
                ApiKeySupport.parseAllowedResources(entity.getAllowedResources()),
                ApiKeySupport.parseAllowedActions(entity.getAllowedActions()),
                entity.getKeyPrefix(),
                rawKey,
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getLastUsedAt(),
                resolveStatus(entity)
        );
    }

    private ApiKey getEntity(Long id) {
        return apiKeyRepository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在"));
    }

    private Map<Long, UserAccount> loadUserMap(List<ApiKey> entities) {
        return entities.stream()
                .map(ApiKey::getUserId)
                .distinct()
                .map(id -> userAccountRepository.findByIdAndDeletedFlagFalse(id).orElse(null))
                .filter(u -> u != null)
                .collect(Collectors.toMap(UserAccount::getId, u -> u));
    }

    private Specification<ApiKey> buildStatusSpec(String status) {
        String normalizedStatus = normalizeStatus(status);
        return switch (normalizedStatus) {
            case "有效" -> (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.and(
                    criteriaBuilder.equal(root.get("status"), ApiKeyStatus.ACTIVE),
                    criteriaBuilder.or(
                            criteriaBuilder.isNull(root.get("expiresAt")),
                            criteriaBuilder.greaterThan(root.get("expiresAt"), LocalDateTime.now())
                    )
            );
            case "已过期" -> (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.and(
                    criteriaBuilder.equal(root.get("status"), ApiKeyStatus.ACTIVE),
                    criteriaBuilder.isNotNull(root.get("expiresAt")),
                    criteriaBuilder.lessThanOrEqualTo(root.get("expiresAt"), LocalDateTime.now())
            );
            case "已禁用" -> (root, criteriaQuery, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), ApiKeyStatus.DISABLED);
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API Key 状态不合法");
        };
    }

    private String resolveStatus(ApiKey entity) {
        if (entity.getStatus() != ApiKeyStatus.ACTIVE) {
            return ApiKeyStatus.DISABLED.displayName();
        }
        if (entity.getExpiresAt() != null && !entity.getExpiresAt().isAfter(LocalDateTime.now())) {
            return EXPIRED_STATUS;
        }
        return ApiKeyStatus.ACTIVE.displayName();
    }

    private byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    private String normalizeKeyName(String keyName) {
        if (keyName == null || keyName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "密钥名称不能为空");
        }
        String normalized = keyName.trim();
        if (normalized.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "密钥名称长度不能超过64");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API Key 状态不能为空");
        }
        String normalized = status.trim();
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API Key 状态不合法");
        }
        return normalized;
    }

    private Long normalizeExpireDays(Long expireDays) {
        if (expireDays == null) {
            return null;
        }
        if (expireDays <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "有效天数必须大于0");
        }
        if (expireDays > MAX_EXPIRE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "有效天数不能超过3650");
        }
        return expireDays;
    }

    private String normalizeAllowedResources(List<String> allowedResources) {
        try {
            return ApiKeySupport.joinAllowedResources(normalizeAllowedResourceList(allowedResources));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private String normalizeAllowedActions(List<String> allowedActions) {
        try {
            return ApiKeySupport.joinAllowedActions(normalizeAllowedActionList(allowedActions));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private List<String> normalizeAllowedResourceList(List<String> allowedResources) {
        try {
            return ApiKeySupport.normalizeAllowedResources(allowedResources);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private List<String> normalizeAllowedActionList(List<String> allowedActions) {
        try {
            return ApiKeySupport.normalizeAllowedActions(allowedActions);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private void ensurePermissionUpperBound(Long userId,
                                            List<String> allowedResources,
                                            List<String> allowedActions,
                                            String message) {
        Map<String, Set<String>> permissionMap = permissionService.getUserPermissionMap(userId);
        for (String resource : allowedResources) {
            Set<String> userActions = permissionMap.get(resource);
            if (userActions == null || userActions.isEmpty()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, message);
            }
            for (String action : allowedActions) {
                if (!ResourcePermissionCatalog.isAllowed(resource, action) || !userActions.contains(action)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN, message);
                }
            }
        }
    }

    private SecurityPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return principal;
    }

    private void assertCurrentPrincipalCanManageApiKey(ApiKey entity, String message) {
        SecurityPrincipal operator = currentPrincipal();
        if (!isAdmin(operator) && !operator.id().equals(entity.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }

    private boolean isAdmin(SecurityPrincipal principal) {
        return principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority != null)
                .map(authority -> authority.trim().toUpperCase(Locale.ROOT))
                .anyMatch(ROLE_ADMIN::equals);
    }

    private String normalizeUsageScope(String usageScope) {
        if (usageScope == null || usageScope.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API Key 使用范围不能为空");
        }
        String normalized = usageScope.trim();
        if (!ApiKeySupport.ALLOWED_USAGE_SCOPE.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API Key 使用范围不合法");
        }
        return normalized;
    }
}
