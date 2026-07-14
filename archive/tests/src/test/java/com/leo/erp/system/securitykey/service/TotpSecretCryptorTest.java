package com.leo.erp.system.securitykey.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpSecretCryptorTest {

    private final TotpSecretCryptor cryptor = new TotpSecretCryptor();

    @Test
    void shouldEncryptAndDecryptSecret() {
        String encrypted = cryptor.encrypt("JBSWY3DPEHPK3PXP", "leo-dev-totp-key-change-me-20260425");

        assertThat(encrypted).isNotBlank();
        assertThat(encrypted).startsWith("TINK1:");
        assertThat(cryptor.decrypt(encrypted, "leo-dev-totp-key-change-me-20260425"))
                .isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void shouldRejectUnprefixedLegacySecret() {
        String legacyEncrypted = "q7pXYKxXiK3k89hwd9n84prwXFlzde+MlF0ZyaeShv2BV3ilN2Gs";

        assertThatThrownBy(() -> cryptor.decrypt(legacyEncrypted, "leo-dev-totp-key-change-me-20260425"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOTP密钥密文格式不受支持");
    }

    @Test
    void shouldEncryptAndDecryptTinkSecretWithPrefix() {
        String encrypted = cryptor.encrypt("JBSWY3DPEHPK3PXP", "leo-dev-totp-key-change-me-20260425");

        assertThat(encrypted).startsWith("TINK1:");
        assertThat(cryptor.decrypt(encrypted, "leo-dev-totp-key-change-me-20260425"))
                .isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void shouldRejectBrokenTinkSecretWithoutLegacyFallback() {
        assertThatThrownBy(() -> cryptor.decrypt("TINK1:not-base64", "leo-dev-totp-key-change-me-20260425"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOTP密钥解密失败");
    }

    @Test
    void shouldWrapDecryptionFailure() {
        assertThatThrownBy(() -> cryptor.decrypt("not-base64", "leo-dev-totp-key-change-me-20260425"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOTP密钥密文格式不受支持");

        String encrypted = cryptor.encrypt("JBSWY3DPEHPK3PXP", "leo-dev-totp-key-change-me-20260425");

        assertThatThrownBy(() -> cryptor.decrypt(encrypted, "wrong-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOTP密钥解密失败");
    }

}
