package com.leo.erp.common.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

public class BusinessCreateIdResolver {

    private static final String PREALLOCATED_ID_HEADER = "X-Preallocated-Id";
    private static final String BUSINESS_MODULE_KEY_HEADER = "X-Business-Module-Key";

    private final SnowflakeIdGenerator idGenerator;
    private final BusinessPreallocationService businessPreallocationService;
    private final Logger logger;

    public BusinessCreateIdResolver(SnowflakeIdGenerator idGenerator,
                                    BusinessPreallocationService businessPreallocationService,
                                    Class<?> ownerClass) {
        if (idGenerator == null) {
            throw new IllegalArgumentException("SnowflakeIdGenerator must not be null");
        }
        this.idGenerator = idGenerator;
        this.businessPreallocationService = businessPreallocationService;
        this.logger = LoggerFactory.getLogger(ownerClass);
    }

    public CreateEntityId resolve() {
        PreallocatedEntityId preallocatedId = resolvePreallocatedIdFromRequest();
        return preallocatedId != null
                ? new CreateEntityId(preallocatedId.id(), preallocatedId.moduleKey())
                : new CreateEntityId(idGenerator.nextId(), null);
    }

    public void consumeAfterCommit(CreateEntityId createEntityId) {
        if (createEntityId == null || !createEntityId.hasPreallocatedModuleKey() || businessPreallocationService == null) {
            return;
        }
        Runnable consume = () -> {
            try {
                businessPreallocationService.consume(createEntityId.preallocatedModuleKey(), createEntityId.id());
            } catch (RuntimeException ex) {
                logger.warn("Failed to consume preallocated business id: moduleKey={}, id={}, reason={}",
                        createEntityId.preallocatedModuleKey(), createEntityId.id(), ex.getMessage());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            consume.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                consume.run();
            }
        });
    }

    private PreallocatedEntityId resolvePreallocatedIdFromRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }
        String rawValue = servletRequestAttributes.getRequest().getHeader(PREALLOCATED_ID_HEADER);
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            long parsedValue = Long.parseLong(rawValue.trim());
            if (parsedValue <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "预分配雪花ID必须为正整数");
            }
            if (businessPreallocationService == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "预分配服务不可用，无法校验雪花ID");
            }
            String moduleKey = resolveBusinessModuleKey();
            businessPreallocationService.assertReservedByPrincipal(moduleKey, parsedValue, currentPrincipal());
            return new PreallocatedEntityId(parsedValue, moduleKey);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "预分配雪花ID格式不正确");
        }
    }

    private SecurityPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return principal;
    }

    private String resolveBusinessModuleKey() {
        String headerModuleKey = resolveBusinessModuleKeyFromHeader();
        if (!headerModuleKey.isBlank()) {
            return headerModuleKey;
        }

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "无法识别当前请求模块");
        }
        String uri = servletRequestAttributes.getRequest().getRequestURI();
        if (uri == null || uri.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "无法识别当前请求模块");
        }
        String path = uri.startsWith("/api/") ? uri.substring(5) : uri;
        int slashIndex = path.indexOf('/');
        return normalizeRestCollectionModuleKey(slashIndex >= 0 ? path.substring(0, slashIndex) : path);
    }

    private String resolveBusinessModuleKeyFromHeader() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return "";
        }
        String rawValue = servletRequestAttributes.getRequest().getHeader(BUSINESS_MODULE_KEY_HEADER);
        return rawValue == null ? "" : rawValue.trim();
    }

    private String normalizeRestCollectionModuleKey(String rawSegment) {
        String segment = rawSegment == null ? "" : rawSegment.trim();
        if (segment.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "无法识别当前请求模块");
        }
        if (isKnownModuleKey(segment)) {
            return segment;
        }
        if (segment.endsWith("ies") && segment.length() > 3) {
            String candidate = segment.substring(0, segment.length() - 3) + "y";
            if (isKnownModuleKey(candidate)) {
                return candidate;
            }
        }
        if (segment.endsWith("s") && segment.length() > 1) {
            String candidate = segment.substring(0, segment.length() - 1);
            if (isKnownModuleKey(candidate)) {
                return candidate;
            }
        }
        return segment;
    }

    private boolean isKnownModuleKey(String moduleKey) {
        return ResourcePermissionCatalog.isKnownResource(moduleKey)
                || ResourcePermissionCatalog.resolveResourceByMenuCode(moduleKey).isPresent();
    }

    private record PreallocatedEntityId(Long id, String moduleKey) {
    }
}
