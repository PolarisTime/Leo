package com.leo.erp.common.service;

import com.leo.erp.common.support.ValidationMessages;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.StatusAwareEntity;
import com.leo.erp.common.support.DocumentStatus;
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
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, ValidationMessages.STATUS_CHANGE_UNSUPPORTED);
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

    /** 实体状态归一化为稳定 code：兼容回滚环境下的旧中文 label。 */
    private static Optional<String> normalizeStatus(String value) {
        String status = StatusConstants.toCode(value);
        return status == null ? Optional.empty() : Optional.of(status);
    }

    /** 请求状态归一化为稳定 code：接受 code 与迁移期旧中文 label，空值按统一文案拒绝。 */
    public String normalizeRequiredStatus(String status) {
        String code = StatusConstants.toCode(status);
        if (code == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ValidationMessages.STATUS_REQUIRED);
        }
        return code;
    }

    public void assertEditAllowed(E entity, boolean allowProtectedStatusUpdate) {
        resolveStatus(entity).ifPresent(status -> {
            if (StatusConstants.PROTECTED_DOCUMENT_STATUS.contains(status) && !allowProtectedStatusUpdate) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "当前单据状态为「" + displayStatus(status) + "」，不能编辑"
                );
            }
        });
    }

    public void assertDeleteAllowed(E entity) {
        resolveStatus(entity).ifPresent(status -> {
            if (StatusConstants.PROTECTED_DOCUMENT_STATUS.contains(status)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "当前单据状态为「" + displayStatus(status) + "」，不能删除"
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
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, ValidationMessages.STATUS_CHANGE_UNSUPPORTED);
        }
        String currentCode = StatusConstants.toCode(currentStatus);
        String nextCode = StatusConstants.toCode(nextStatus);
        boolean invalidStatus = currentCode == null || currentCode.isBlank()
                || nextCode == null || nextCode.isBlank();
        if (invalidStatus || !allowedTransitions.contains(StatusTransition.of(currentCode, nextCode))) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "当前单据状态不能从「" + displayStatus(currentCode) + "」变更为「" + displayStatus(nextCode) + "」"
            );
        }
    }

    /** 错误文案展示用：code 转回中文 label（未知值原样），API/DB 仍只使用 code。 */
    private static String displayStatus(String status) {
        if (status == null) {
            return null;
        }
        return DocumentStatus.fromCode(status).map(DocumentStatus::label).orElse(status);
    }

    public void writeStatus(E entity, String status) {
        statusWriter.accept(entity, status);
    }
}
