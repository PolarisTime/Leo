package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DocumentStatus 枚举与 StatusConstants 常量一致性回归测试。
 */
class DocumentStatusTest {

    @Test
    void everyEnumLabel_shouldMatchCorrespondingStatusConstant() throws Exception {
        for (DocumentStatus status : DocumentStatus.values()) {
            Field field = StatusConstants.class.getField(status.name());
            assertThat(field.get(null)).isEqualTo(status.label());
        }
    }

    @Test
    void shouldCoverAllRequiredBusinessStatuses() {
        assertThat(DocumentStatus.fromLabel(StatusConstants.NORMAL)).contains(DocumentStatus.NORMAL);
        assertThat(DocumentStatus.fromLabel(StatusConstants.DISABLED)).contains(DocumentStatus.DISABLED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.DRAFT)).contains(DocumentStatus.DRAFT);
        assertThat(DocumentStatus.fromLabel(StatusConstants.AUDITED)).contains(DocumentStatus.AUDITED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.COMPLETED)).contains(DocumentStatus.COMPLETED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.PRE_OUTBOUND)).contains(DocumentStatus.PRE_OUTBOUND);
        assertThat(DocumentStatus.fromLabel(StatusConstants.PAID)).contains(DocumentStatus.LEGACY_PAID);
        assertThat(DocumentStatus.fromLabel(StatusConstants.RECEIVED)).contains(DocumentStatus.LEGACY_RECEIVED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.LEGACY_PAID)).contains(DocumentStatus.LEGACY_PAID);
        assertThat(DocumentStatus.fromLabel(StatusConstants.LEGACY_RECEIVED)).contains(DocumentStatus.LEGACY_RECEIVED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.PURCHASE_COMPLETED)).contains(DocumentStatus.PURCHASE_COMPLETED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.SALES_COMPLETED)).contains(DocumentStatus.SALES_COMPLETED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.INBOUND_COMPLETED)).contains(DocumentStatus.INBOUND_COMPLETED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.DELIVERY_VERIFICATION)).contains(DocumentStatus.DELIVERY_VERIFICATION);
        assertThat(DocumentStatus.fromLabel(StatusConstants.SIGNED)).contains(DocumentStatus.SIGNED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.UNSIGNED)).contains(DocumentStatus.UNSIGNED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.UNAUDITED)).contains(DocumentStatus.UNAUDITED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.EXECUTING)).contains(DocumentStatus.EXECUTING);
        assertThat(DocumentStatus.fromLabel(StatusConstants.ARCHIVED)).contains(DocumentStatus.ARCHIVED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.PENDING_CONFIRM)).contains(DocumentStatus.PENDING_CONFIRM);
        assertThat(DocumentStatus.fromLabel(StatusConstants.CONFIRMED)).contains(DocumentStatus.CONFIRMED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.PENDING_AUDIT)).contains(DocumentStatus.PENDING_AUDIT);
    }

    @Test
    void fromLabel_shouldRoundTripAndTrimWhitespace() {
        for (DocumentStatus status : DocumentStatus.values()) {
            assertThat(DocumentStatus.fromLabel(status.label())).contains(status);
            assertThat(DocumentStatus.fromLabel("  " + status.label() + " ")).contains(status);
        }
    }

    @Test
    void fromLabel_shouldReturnEmptyForUnknownOrBlank() {
        assertThat(DocumentStatus.fromLabel(null)).isEmpty();
        assertThat(DocumentStatus.fromLabel("")).isEmpty();
        assertThat(DocumentStatus.fromLabel("   ")).isEmpty();
        assertThat(DocumentStatus.fromLabel("不存在的状态")).isEmpty();
    }

    @Test
    void fromCode_shouldRoundTrip() {
        for (DocumentStatus status : DocumentStatus.values()) {
            assertThat(DocumentStatus.fromCode(status.code())).contains(status);
        }
    }

    @Test
    void fromCode_shouldReturnEmptyForUnknownOrBlank() {
        assertThat(DocumentStatus.fromCode(null)).isEmpty();
        assertThat(DocumentStatus.fromCode("")).isEmpty();
        assertThat(DocumentStatus.fromCode("   ")).isEmpty();
        assertThat(DocumentStatus.fromCode("NOT_A_STATUS")).isEmpty();
    }

    @Test
    void labelOf_shouldResolveEveryCode() {
        for (DocumentStatus status : DocumentStatus.values()) {
            assertThat(DocumentStatus.labelOf(status.code())).isEqualTo(status.label());
        }
    }

    @Test
    void labelOf_shouldReturnNullForUnknownOrBlank() {
        assertThat(DocumentStatus.labelOf(null)).isNull();
        assertThat(DocumentStatus.labelOf("")).isNull();
        assertThat(DocumentStatus.labelOf("   ")).isNull();
        assertThat(DocumentStatus.labelOf("NOT_A_STATUS")).isNull();
    }

    @Test
    void labels_shouldBeUniqueAndUnmodifiable() {
        assertThat(DocumentStatus.labels()).hasSize(DocumentStatus.values().length);
        for (DocumentStatus status : DocumentStatus.values()) {
            assertThat(DocumentStatus.labels()).contains(status.label());
            assertThat(StatusConstants.labels()).contains(status.label());
        }
        assertThatThrownBy(() -> DocumentStatus.labels().add("非法状态"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
