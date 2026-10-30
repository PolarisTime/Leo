package com.leo.erp.common.validation;

import com.leo.erp.attachment.web.dto.AttachmentDirectUploadPrepareRequest;
import com.leo.erp.auth.web.dto.CurrentAccountUpdateRequest;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.market.lengthpremium.web.dto.LengthPremiumRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupAccountRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求 DTO 的字段长度上限必须等于数据库真实列宽。
 *
 * <p>回归背景：{@code sys_user.mobile} 是 varchar(20)、{@code sys_attachment.content_type} 是
 * varchar(128)、各表 {@code status} 是 varchar(16)，而对应 DTO 曾写成 32 / 255 / 32。
 * 上限放宽后超长值能通过 Bean Validation，最终撞库报 500 或被 {{@code DataIntegrityViolation}}
 * 兜成 422，错误定位不到字段。本测试锁定四个 DTO 的上限值。</p>
 */
class FieldLengthContractValidationTest {

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

    private static String repeat(int length) {
        return "A".repeat(length);
    }

    private Set<String> violatedFields(Object target) {
        return validator.validate(target).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    /** 字段上 @Size.max 的声明值；没有 @Size 时返回 -1。 */
    private static int declaredMaxSize(Class<?> type, String fieldName) throws NoSuchFieldException {
        Field field = type.getDeclaredField(fieldName);
        Size size = field.getAnnotation(Size.class);
        return size == null ? -1 : size.max();
    }

    // ---------- 账号手机号: sys_user.mobile varchar(20) ----------

    @Test
    void mobile_maxSizeMatchesDatabaseColumn() throws NoSuchFieldException {
        assertThat(declaredMaxSize(CurrentAccountUpdateRequest.class, "mobile")).isEqualTo(20);
        assertThat(declaredMaxSize(InitialSetupAccountRequest.class, "mobile")).isEqualTo(20);
    }

    @Test
    void mobile_validElevenDigitsAccepted() {
        assertThat(violatedFields(new CurrentAccountUpdateRequest("张三", "13800000000", null))).isEmpty();
    }

    @Test
    void mobile_over20RejectedBySizeConstraint() {
        // 21 位既超出 @Size(20) 也不符合手机号格式; 断言 Size 约束确实存在且上限是 20
        Set<ConstraintViolation<CurrentAccountUpdateRequest>> violations = validator.validate(
                new CurrentAccountUpdateRequest("张三", repeat(21), null));

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("mobile");
                    assertThat(violation.getConstraintDescriptor().getAnnotation())
                            .isInstanceOfSatisfying(Size.class, size -> assertThat(size.max()).isEqualTo(20));
                });
    }

    // ---------- 附件直传: sys_attachment.content_type varchar(128) ----------

    @Test
    void contentType_maxSizeMatchesDatabaseColumn() throws NoSuchFieldException {
        assertThat(declaredMaxSize(AttachmentDirectUploadPrepareRequest.class, "contentType")).isEqualTo(128);
    }

    @Test
    void contentType_at128Accepted() {
        assertThat(violatedFields(attachment(repeat(128)))).isEmpty();
    }

    @Test
    void contentType_over128Rejected() {
        assertThat(violatedFields(attachment(repeat(129)))).contains("contentType");
    }

    private AttachmentDirectUploadPrepareRequest attachment(String contentType) {
        return new AttachmentDirectUploadPrepareRequest(
                "a.pdf", contentType, 1L, "PRINT", repeat(64));
    }

    // ---------- 通用状态: 各表 status varchar(16) ----------

    @Test
    void status_maxSizeMatchesDatabaseColumn() throws NoSuchFieldException {
        assertThat(declaredMaxSize(StatusUpdateRequest.class, "status")).isEqualTo(16);
    }

    @Test
    void status_at16Accepted() {
        assertThat(violatedFields(new StatusUpdateRequest(repeat(16)))).isEmpty();
    }

    @Test
    void status_over16Rejected() {
        assertThat(violatedFields(new StatusUpdateRequest(repeat(17)))).contains("status");
    }

    // ---------- 定尺打印后缀: md_length_premium.spec_suffix varchar(16) ----------

    private LengthPremiumRequest lengthPremium(String specSuffix) {
        return new LengthPremiumRequest("12米", 30, 1, "正常", null, specSuffix);
    }

    @Test
    void specSuffix_maxSizeMatchesDatabaseColumn() throws NoSuchFieldException {
        assertThat(declaredMaxSize(LengthPremiumRequest.class, "specSuffix")).isEqualTo(16);
    }

    @Test
    void specSuffix_at16Accepted() {
        assertThat(violatedFields(lengthPremium(repeat(16)))).isEmpty();
    }

    @Test
    void specSuffix_over16Rejected() {
        assertThat(violatedFields(lengthPremium(repeat(17)))).contains("specSuffix");
    }

    @Test
    void specSuffix_blankAccepted() {
        // 空串表示「该定尺不追加后缀」, 必须能提交
        assertThat(violatedFields(lengthPremium(""))).isEmpty();
        assertThat(violatedFields(lengthPremium(null))).isEmpty();
    }
}
