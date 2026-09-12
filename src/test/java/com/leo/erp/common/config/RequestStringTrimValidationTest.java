package com.leo.erp.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求体字符串在反序列化阶段统一 trim，使 {@code @Size} 等约束按归一化后的值判定，
 * 避免首尾空格导致合法值被误拒；密码类字段保留原值。
 */
class RequestStringTrimValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static ObjectMapper configuredMapper() {
        JacksonConfig config = new JacksonConfig("Asia/Shanghai");
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        config.jackson2ObjectMapperBuilderCustomizer().customize(builder);
        return builder.build();
    }

    record TrimSample(
            @Size(max = 3) String code,
            String remark,
            String password,
            String currentPassword) {
    }

    @Test
    void trimsPlainStringsBeforeValidation() throws Exception {
        ObjectMapper mapper = configuredMapper();
        TrimSample sample = mapper.readValue(
                "{\"code\":\" abc \",\"remark\":\"  x  \",\"password\":\" pw \",\"currentPassword\":\" old \"}",
                TrimSample.class);

        assertThat(sample.code()).isEqualTo("abc");
        assertThat(sample.remark()).isEqualTo("x");
        assertThat(VALIDATOR.validate(sample)).isEmpty();
    }

    @Test
    void preservesPasswordLikeFields() throws Exception {
        ObjectMapper mapper = configuredMapper();
        TrimSample sample = mapper.readValue(
                "{\"code\":\"abc\",\"password\":\" pw \",\"currentPassword\":\" old \"}",
                TrimSample.class);

        assertThat(sample.password()).isEqualTo(" pw ");
        assertThat(sample.currentPassword()).isEqualTo(" old ");
    }

    @Test
    void keepsNullAsNull() throws Exception {
        ObjectMapper mapper = configuredMapper();
        TrimSample sample = mapper.readValue("{\"code\":null}", TrimSample.class);

        assertThat(sample.code()).isNull();
    }
}
