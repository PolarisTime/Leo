package com.leo.erp.master.project.web.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目请求的允许品牌/材质校验: 空值表示不限制, 但一旦填写, 单向长度与条数上限必须生效。
 * <p>
 * 这里用容器元素约束({@code List<@NotBlank @Size(max = 64) String>})表达逐项校验,
 * 必须用真实 Validator 验证约束确实挂到了元素上, 而不是只挂在列表外层。
 */
class ProjectRequestValidationTest {

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

    @Test
    void valid_request_hasNoViolations() {
        assertThat(validator.validate(request(List.of("沙钢", "永钢"), List.of("HRB400")))).isEmpty();
    }

    @Test
    void absent_allowedSpec_hasNoViolations() {
        assertThat(validator.validate(request(null, null))).isEmpty();
        assertThat(validator.validate(request(List.of(), List.of()))).isEmpty();
    }

    @Test
    void rejectsBlankEntry() {
        Set<String> fields = violationFields(request(List.of("沙钢", "   "), List.of(" ")));

        assertThat(fields).anySatisfy(field -> assertThat(field).startsWith("allowedBrands"));
        assertThat(fields).anySatisfy(field -> assertThat(field).startsWith("allowedMaterials"));
    }

    @Test
    void rejectsOverlongEntry() {
        String overlong = "钢".repeat(65);

        assertThat(violationFields(request(List.of(overlong), List.of(overlong))))
                .anySatisfy(field -> assertThat(field).startsWith("allowedBrands"))
                .anySatisfy(field -> assertThat(field).startsWith("allowedMaterials"));
    }

    @Test
    void rejectsTooManyEntries() {
        List<String> tooMany = java.util.stream.IntStream.rangeClosed(1, 101)
                .mapToObj(index -> "品牌" + index)
                .toList();

        assertThat(violationFields(request(tooMany, null)))
                .anySatisfy(field -> assertThat(field).startsWith("allowedBrands"));
    }

    private Set<String> violationFields(ProjectRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private ProjectRequest request(List<String> allowedBrands, List<String> allowedMaterials) {
        return new ProjectRequest(
                "XM001", "项目一", null, null, null,
                7L, "C007", null, null, "正常",
                null, null, null, null,
                allowedBrands, allowedMaterials, null);
    }
}
