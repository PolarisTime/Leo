package com.leo.erp.common.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.StatusAwareEntity;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;

import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class CrudStatusGuard<E> {

    private final Function<E, Optional<String>> statusReader;
    private final BiConsumer<E, String> statusWriter;

    private CrudStatusGuard(Function<E, Optional<String>> statusReader,
                            BiConsumer<E, String> statusWriter) {
        this.statusReader = statusReader;
        this.statusWriter = statusWriter;
    }

    public static <E> CrudStatusGuard<E> withoutStatus() {
        return new CrudStatusGuard<>(
                entity -> Optional.empty(),
                (entity, status) -> {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前模块不支持状态变更");
                }
        );
    }

    public static <E extends StatusAwareEntity> CrudStatusGuard<E> forStatusAwareEntities() {
        return new CrudStatusGuard<>(
                entity -> normalizeStatus(entity.getStatus()),
                StatusAwareEntity::setStatus
        );
    }

    public Optional<String> resolveStatus(E entity) {
        return statusReader.apply(entity);
    }

    private static Optional<String> normalizeStatus(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String status = value.trim();
        return status.isBlank() ? Optional.empty() : Optional.of(status);
    }

    public String normalizeRequiredStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "状态不能为空");
        }
        return status.trim();
    }

    public void assertEditAllowed(E entity, boolean allowProtectedStatusUpdate) {
        resolveStatus(entity).ifPresent(status -> {
            if (StatusConstants.PROTECTED_DOCUMENT_STATUS.contains(status) && !allowProtectedStatusUpdate) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "当前单据状态为「" + status + "」，不能编辑"
                );
            }
        });
    }

    public void assertDeleteAllowed(E entity) {
        resolveStatus(entity).ifPresent(status -> {
            if (StatusConstants.PROTECTED_DOCUMENT_STATUS.contains(status)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "当前单据状态为「" + status + "」，不能删除"
                );
            }
        });
    }

    public void assertRequestStatusTransitionAllowed(E entity,
                                                    Optional<String> currentStatus,
                                                    Set<StatusTransition> allowedTransitions) {
        if (allowedTransitions.isEmpty()) {
            return;
        }
        Optional<String> nextStatus = resolveStatus(entity);
        if (currentStatus.isEmpty() || nextStatus.isEmpty() || currentStatus.get().equals(nextStatus.get())) {
            return;
        }
        validateStatusTransition(allowedTransitions, currentStatus.get(), nextStatus.get());
    }

    public void assertRequestDidNotWriteFinalStatus(E entity) {
        resolveStatus(entity).ifPresent(status -> {
            if (StatusConstants.PROTECTED_DOCUMENT_STATUS.contains(status) && !StatusConstants.AUDITED.equals(status)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "完成态状态必须通过专用状态接口变更"
                );
            }
        });
    }

    public void validateStatusTransition(Set<StatusTransition> allowedTransitions,
                                         String currentStatus,
                                         String nextStatus) {
        if (allowedTransitions.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前模块不支持状态变更");
        }
        boolean invalidStatus = currentStatus == null || currentStatus.isBlank()
                || nextStatus == null || nextStatus.isBlank();
        if (invalidStatus || !allowedTransitions.contains(StatusTransition.of(currentStatus, nextStatus))) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "当前单据状态不能从「" + currentStatus + "」变更为「" + nextStatus + "」"
            );
        }
    }

    public void writeStatus(E entity, String status) {
        statusWriter.accept(entity, status);
    }
}
