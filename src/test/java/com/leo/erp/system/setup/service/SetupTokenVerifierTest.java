package com.leo.erp.system.setup.service;

import com.leo.erp.system.setup.config.InitialSetupProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SetupTokenVerifierTest {

    private static final byte[] TOKEN_BYTES = new byte[32];
    private static final String VALID_TOKEN = Base64.getUrlEncoder().withoutPadding().encodeToString(TOKEN_BYTES);

    @Test
    void shouldAcceptMatchingThirtyTwoByteBase64UrlToken() {
        SetupTokenVerifier verifier = verifier(VALID_TOKEN);

        assertThat(verifier.matches(VALID_TOKEN)).isTrue();
    }

    @Test
    void shouldAcceptPaddedBase64UrlConfiguration() {
        String paddedToken = Base64.getUrlEncoder().encodeToString(TOKEN_BYTES);
        SetupTokenVerifier verifier = verifier(paddedToken);

        assertThat(verifier.matches(paddedToken)).isTrue();
    }

    @Test
    void shouldRejectMissingWrongOrMalformedToken() {
        SetupTokenVerifier verifier = verifier(VALID_TOKEN);
        byte[] otherBytes = TOKEN_BYTES.clone();
        otherBytes[31] = 1;
        String otherToken = Base64.getUrlEncoder().withoutPadding().encodeToString(otherBytes);

        assertThat(verifier.matches(null)).isFalse();
        assertThat(verifier.matches("")).isFalse();
        assertThat(verifier.matches(otherToken)).isFalse();
        assertThat(verifier.matches("not-a-token")).isFalse();
    }

    @Test
    void shouldFailClosedWhenConfiguredTokenIsMissingOrNotThirtyTwoBytes() {
        assertThat(verifier(null).matches(VALID_TOKEN)).isFalse();
        assertThat(verifier("").matches(VALID_TOKEN)).isFalse();
        assertThat(verifier(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[31]))
                .matches(VALID_TOKEN)).isFalse();
    }

    private SetupTokenVerifier verifier(String configuredToken) {
        InitialSetupProperties properties = new InitialSetupProperties();
        properties.setBootstrapToken(configuredToken);
        return new SetupTokenVerifier(properties);
    }
}
