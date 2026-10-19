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

    /**
     * 已移除的历史状态不得再被解析出来。
     *
     * <p>「预出库」「已发出」是旧流程的取值，已确认生产库 37 个 status 列命中 0 处后删除。
     * 本测试锁住「删除是彻底的」：若有人把它们加回枚举，这里会红。</p>
     */
    @Test
    void removedLegacyStatusesShouldNoLongerResolve() {
        assertThat(DocumentStatus.fromLabel("预出库")).isEmpty();
        assertThat(DocumentStatus.fromLabel("已发出")).isEmpty();
        assertThat(DocumentStatus.fromCode("PRE_OUTBOUND")).isEmpty();
        assertThat(DocumentStatus.fromCode("ISSUED")).isEmpty();
        assertThat(DocumentStatus.fromLabel("已付款")).isEmpty();
        assertThat(DocumentStatus.fromLabel("已收款")).isEmpty();
        // 字面量仍保留供守卫使用（历史数据若存在必须只读），但不再属于枚举
        assertThat(StatusConstants.LEGACY_PAID).isEqualTo("已付款");
        assertThat(StatusConstants.LEGACY_RECEIVED).isEqualTo("已收款");
    }

    @Test
    void shouldCoverAllRequiredBusinessStatuses() {
        assertThat(DocumentStatus.fromLabel(StatusConstants.NORMAL)).contains(DocumentStatus.NORMAL);
        assertThat(DocumentStatus.fromLabel(StatusConstants.DISABLED)).contains(DocumentStatus.DISABLED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.DRAFT)).contains(DocumentStatus.DRAFT);
        assertThat(DocumentStatus.fromLabel(StatusConstants.AUDITED)).contains(DocumentStatus.AUDITED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.CONTRACT_REVIEWED))
                .contains(DocumentStatus.CONTRACT_REVIEWED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.CONTRACT_ISSUED))
                .contains(DocumentStatus.CONTRACT_ISSUED);
        assertThat(DocumentStatus.CONTRACT_REVIEWED.label()).isEqualTo("审核");
        assertThat(DocumentStatus.CONTRACT_ISSUED.label()).isEqualTo("签发");
        assertThat(DocumentStatus.fromLabel("审核")).contains(DocumentStatus.CONTRACT_REVIEWED);
        assertThat(DocumentStatus.fromLabel("签发")).contains(DocumentStatus.CONTRACT_ISSUED);
        assertThat(DocumentStatus.fromLabel(StatusConstants.COMPLETED)).contains(DocumentStatus.COMPLETED);
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
