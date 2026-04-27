package com.leo.erp.security.config;

import com.leo.erp.security.jwt.JwtProperties;
import com.leo.erp.security.totp.TotpProperties;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPropertiesValidationTest {

    private static final Validator VALIDATOR = validator();

    @Test
    void shouldRejectNonPositiveExpirationsAndAllowFallbackSecretToBeValidatedAtRuntime() {
        Set<String> violations = VALIDATOR.validate(new JwtProperties("leo", "short-jwt-secret", 0, -1)).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(violations).containsExactlyInAnyOrder("accessExpirationMs", "refreshExpirationMs");
    }

    @Test
    void shouldAllowMissingFallbackSecretsButRejectBlankIssuer() {
        Set<String> violations = VALIDATOR.validate(new TotpProperties(" ", "")).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(violations).containsExactly("issuer");
    }

    @Test
    void shouldAllowMissingJwtFallbackSecret() {
        Set<String> violations = VALIDATOR.validate(new JwtProperties("leo", null, 1, 1)).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(violations).isEmpty();
    }

    private static Validator validator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.afterPropertiesSet();
        return bean;
    }
}
