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
    void everyEnumCode_shouldMatchCorrespondingStatusConstant() throws Exception {
        for (DocumentStatus status : DocumentStatus.values()) {
            Field field = StatusConstants.class.getField(status.name());
            assertThat(field.get(null)).isEqualTo(status.code());
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
        // 旧命名常量仍保留供守卫使用（历史数据若存在必须只读），值已改为稳定 code
        assertThat(StatusConstants.LEGACY_PAID).isEqualTo("PAID");
        assertThat(StatusConstants.LEGACY_RECEIVED).isEqualTo("RECEIVED");
        // 迁移期双读：旧中文 label 仍能被归一化为 code
        assertThat(StatusConstants.toCode("已付款")).isEqualTo("PAID");
        assertThat(StatusConstants.toCode("已收款")).isEqualTo("RECEIVED");
        assertThat(StatusConstants.toCode("报价")).isEqualTo("QUOTING");
        assertThat(StatusConstants.toCode("成功")).isEqualTo("SUCCESS");
        assertThat(StatusConstants.toCode("失败")).isEqualTo("FAILED");
    }

    @Test
    void shouldCoverAllRequiredBusinessStatuses() {
        assertThat(DocumentStatus.fromCode(StatusConstants.NORMAL)).contains(DocumentStatus.NORMAL);
        assertThat(DocumentStatus.fromCode(StatusConstants.DISABLED)).contains(DocumentStatus.DISABLED);
        assertThat(DocumentStatus.fromCode(StatusConstants.DRAFT)).contains(DocumentStatus.DRAFT);
        assertThat(DocumentStatus.fromCode(StatusConstants.AUDITED)).contains(DocumentStatus.AUDITED);
        assertThat(DocumentStatus.fromCode(StatusConstants.CONTRACT_REVIEWED))
                .contains(DocumentStatus.CONTRACT_REVIEWED);
        assertThat(DocumentStatus.fromCode(StatusConstants.CONTRACT_ISSUED))
                .contains(DocumentStatus.CONTRACT_ISSUED);
        // 中文 label 只保留为旧数据/旧客户端兼容映射
        assertThat(DocumentStatus.CONTRACT_REVIEWED.label()).isEqualTo("审核");
        assertThat(DocumentStatus.CONTRACT_ISSUED.label()).isEqualTo("签发");
        assertThat(DocumentStatus.fromLabel("审核")).contains(DocumentStatus.CONTRACT_REVIEWED);
        assertThat(DocumentStatus.fromLabel("签发")).contains(DocumentStatus.CONTRACT_ISSUED);
        assertThat(StatusConstants.toCode("审核")).isEqualTo(StatusConstants.CONTRACT_REVIEWED);
        assertThat(StatusConstants.toCode("签发")).isEqualTo(StatusConstants.CONTRACT_ISSUED);
        assertThat(DocumentStatus.fromCode(StatusConstants.COMPLETED)).contains(DocumentStatus.COMPLETED);
        assertThat(DocumentStatus.fromCode(StatusConstants.PURCHASE_COMPLETED)).contains(DocumentStatus.PURCHASE_COMPLETED);
        assertThat(DocumentStatus.fromCode(StatusConstants.SALES_COMPLETED)).contains(DocumentStatus.SALES_COMPLETED);
        assertThat(DocumentStatus.fromCode(StatusConstants.INBOUND_COMPLETED)).contains(DocumentStatus.INBOUND_COMPLETED);
        assertThat(DocumentStatus.fromCode(StatusConstants.DELIVERY_VERIFICATION)).contains(DocumentStatus.DELIVERY_VERIFICATION);
        assertThat(DocumentStatus.fromCode(StatusConstants.SIGNED)).contains(DocumentStatus.SIGNED);
        assertThat(DocumentStatus.fromCode(StatusConstants.UNSIGNED)).contains(DocumentStatus.UNSIGNED);
        assertThat(DocumentStatus.fromCode(StatusConstants.UNAUDITED)).contains(DocumentStatus.UNAUDITED);
        assertThat(DocumentStatus.fromCode(StatusConstants.EXECUTING)).contains(DocumentStatus.EXECUTING);
        assertThat(DocumentStatus.fromCode(StatusConstants.ARCHIVED)).contains(DocumentStatus.ARCHIVED);
        assertThat(DocumentStatus.fromCode(StatusConstants.PENDING_CONFIRM)).contains(DocumentStatus.PENDING_CONFIRM);
        assertThat(DocumentStatus.fromCode(StatusConstants.CONFIRMED)).contains(DocumentStatus.CONFIRMED);
        assertThat(DocumentStatus.fromCode(StatusConstants.PENDING_AUDIT)).contains(DocumentStatus.PENDING_AUDIT);
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
    void codes_shouldBeUniqueAndUnmodifiable() {
        assertThat(DocumentStatus.codes()).hasSize(DocumentStatus.values().length);
        for (DocumentStatus status : DocumentStatus.values()) {
            assertThat(DocumentStatus.codes()).contains(status.code());
            assertThat(StatusConstants.codes()).contains(status.code());
        }
        assertThatThrownBy(() -> DocumentStatus.codes().add("非法状态"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void legacyLabels_shouldStayAvailableForCompatibilityOnly() {
        assertThat(DocumentStatus.labels()).hasSize(DocumentStatus.values().length);
        for (DocumentStatus status : DocumentStatus.values()) {
            assertThat(DocumentStatus.labels()).contains(status.label());
        }
    }
}
