package com.leo.erp.common.validation;

import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求体雪花 ID 校验回归测试。
 *
 * <p>回归背景：路径参数里的非法 ID 最坏结果是查不到（404），但<b>请求体里的关联 ID
 * 是外键</b>——负数会被当作合法值写入数据库，形成无法通过正常渠道修复的脏数据。
 * 本测试锁定「请求体关联 ID 必须为正整数」这一契约。</p>
 */
class SnowflakeIdRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private SalesOrderItemRequest item(Long id, Long materialId, Long sourceInboundItemId,
                                       Long sourcePurchaseOrderItemId, Long warehouseId) {
        return new SalesOrderItemRequest(
                id, materialId, "M-001", "品牌", "类目", "材质", "规格", "12m", "吨",
                sourceInboundItemId, sourcePurchaseOrderItemId, warehouseId, "一号仓", null,
                1, "件", new BigDecimal("0.1"), 10, new BigDecimal("1.0"),
                new BigDecimal("100.00"), new BigDecimal("100.00"));
    }

    private Set<String> violatedFields(Object target) {
        Set<ConstraintViolation<Object>> violations = validator.validate(target);
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void negativeId_shouldBeRejected() {
        assertThat(violatedFields(item(-1L, null, null, null, null))).contains("id");
    }

    @Test
    void zeroId_shouldBeRejected() {
        assertThat(violatedFields(item(0L, null, null, null, null))).contains("id");
    }

    @Test
    void negativeForeignKey_shouldBeRejected() {
        // 关联 ID 才是真正的脏数据入口：负数会被当合法外键写入
        assertThat(violatedFields(item(null, -5L, null, null, null))).contains("materialId");
        assertThat(violatedFields(item(null, null, -5L, null, null))).contains("sourceInboundItemId");
        assertThat(violatedFields(item(null, null, null, -5L, null))).contains("sourcePurchaseOrderItemId");
        assertThat(violatedFields(item(null, null, null, null, -5L))).contains("warehouseId");
    }

    @Test
    void nullOptionalId_shouldBeAccepted() {
        // 可空性不归 @SnowflakeId 管：可选关联字段为空时不得报错
        assertThat(violatedFields(item(null, null, null, null, null))).isEmpty();
    }

    @Test
    void positiveId_shouldBeAccepted() {
        assertThat(violatedFields(item(1L, 2L, 3L, 4L, 5L))).isEmpty();
    }

    @Test
    void maxLongId_shouldBeAccepted() {
        // 雪花 ID 上限是 Long.MAX_VALUE（9223372036854775807），不可误判为溢出
        assertThat(violatedFields(item(Long.MAX_VALUE, null, null, null, null))).isEmpty();
    }

}
