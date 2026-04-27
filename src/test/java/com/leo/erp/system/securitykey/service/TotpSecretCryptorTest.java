package com.leo.erp.system.securitykey.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpSecretCryptorTest {

    private final TotpSecretCryptor cryptor = new TotpSecretCryptor();

    @Test
    void shouldEncryptAndDecryptSecret() {
        String encrypted = cryptor.encrypt("JBSWY3DPEHPK3PXP", "leo-dev-totp-key-change-me-20260425");

        assertThat(encrypted).isNotBlank();
        assertThat(cryptor.decrypt(encrypted, "leo-dev-totp-key-change-me-20260425"))
                .isEqualTo("JBSWY3DPEHPK3PXP");
    }
}
