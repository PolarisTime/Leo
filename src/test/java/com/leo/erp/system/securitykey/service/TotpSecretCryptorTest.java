package com.leo.erp.system.securitykey.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpSecretCryptorTest {

    private final TotpSecretCryptor cryptor = new TotpSecretCryptor();

    @Test
    void shouldEncryptAndDecryptSecret() {
        String encrypted = cryptor.encrypt("JBSWY3DPEHPK3PXP", "leo-dev-totp-key-change-me-20260425");

        assertThat(encrypted).isNotBlank();
        assertThat(cryptor.decrypt(encrypted, "leo-dev-totp-key-change-me-20260425"))
                .isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void shouldWrapEncryptionFailure() {
        try (var secureRandom = Mockito.mockStatic(SecureRandom.class)) {
            secureRandom.when(SecureRandom::getInstanceStrong)
                    .thenThrow(new NoSuchAlgorithmException("random unavailable"));

            assertThatThrownBy(() -> cryptor.encrypt("JBSWY3DPEHPK3PXP", "leo-dev-totp-key-change-me-20260425"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TOTP密钥加密失败")
                    .hasCauseInstanceOf(NoSuchAlgorithmException.class);
        }
    }

    @Test
    void shouldWrapDecryptionFailure() {
        assertThatThrownBy(() -> cryptor.decrypt("not-base64", "leo-dev-totp-key-change-me-20260425"))
                .isInstanceOf(IllegalArgumentException.class);

        String encrypted = cryptor.encrypt("JBSWY3DPEHPK3PXP", "leo-dev-totp-key-change-me-20260425");

        assertThatThrownBy(() -> cryptor.decrypt(encrypted, "wrong-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOTP密钥解密失败");
    }
}
