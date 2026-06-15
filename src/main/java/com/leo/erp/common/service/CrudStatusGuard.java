package com.leo.erp.common.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

public class CrudStatusGuard {

    public Optional<String> resolveStatus(Object entity) {
        try {
            Method getter = entity.getClass().getMethod("getStatus");
            Object value = getter.invoke(entity);
            if (value == null) {
                return Optional.empty();
            }
            String status = String.valueOf(value).trim();
            return status.isBlank() ? Optional.empty() : Optional.of(status);
        } catch (NoSuchMethodException ignored) {
            return Optional.empty();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("读取单据状态失败", ex);
        }
    }

    public String normalizeRequiredStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "状态不能为空");
        }
        return status.trim();
    }

    public void assertEditAllowed(Object entity, boolean allowProtectedStatusUpdate) {
        resolveStatus(entity).ifPresent(status -> {
            if (StatusConstants.PROTECTED_DOCUMENT_STATUS.contains(status) && !allowProtectedStatusUpdate) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "当前单据状态为「" + status + "」，不能编辑"
                );
            }
        });
    }

    public void assertDeleteAllowed(Object entity) {
        resolveStatus(entity).ifPresent(status -> {
            if (StatusConstants.PROTECTED_DOCUMENT_STATUS.contains(status)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "当前单据状态为「" + status + "」，不能删除"
                );
            }
        });
    }

    public void assertRequestStatusTransitionAllowed(Object entity,
                                                    Optional<String> currentStatus,
                                                    Set<String> allowedTransitions) {
        if (allowedTransitions.isEmpty()) {
            return;
        }
        Optional<String> nextStatus = resolveStatus(entity);
        if (currentStatus.isEmpty() || nextStatus.isEmpty() || currentStatus.get().equals(nextStatus.get())) {
            return;
        }
        validateStatusTransition(allowedTransitions, currentStatus.get(), nextStatus.get());
    }

    public void assertRequestDidNotWriteFinalStatus(Object entity) {
        resolveStatus(entity).ifPresent(status -> {
            if (StatusConstants.PROTECTED_DOCUMENT_STATUS.contains(status) && !StatusConstants.AUDITED.equals(status)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "完成态状态必须通过专用状态接口变更"
                );
            }
        });
    }

    public void validateStatusTransition(Set<String> allowedTransitions, String currentStatus, String nextStatus) {
        if (allowedTransitions.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前模块不支持状态变更");
        }
        String transition = currentStatus + "->" + nextStatus;
        if (!allowedTransitions.contains(transition)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "当前单据状态不能从「" + currentStatus + "」变更为「" + nextStatus + "」"
            );
        }
    }

    public void writeStatus(Object entity, String status) {
        try {
            Method setter = entity.getClass().getMethod("setStatus", String.class);
            setter.invoke(entity, status);
        } catch (NoSuchMethodException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前模块不支持状态变更");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("写入单据状态失败", ex);
        }
    }

    public void markDeletedStatus(Object entity, boolean shouldMarkDeletedStatus) {
        if (!shouldMarkDeletedStatus) {
            return;
        }
        try {
            Method setter = entity.getClass().getMethod("setStatus", String.class);
            setter.invoke(entity, StatusConstants.DELETED);
        } catch (NoSuchMethodException ignored) {
            // Entities without a main status field do not need deleted status tagging.
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("写入单据删除状态失败", ex);
        }
    }
}
