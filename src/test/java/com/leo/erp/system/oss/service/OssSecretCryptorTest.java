package com.leo.erp.system.oss.service;

import com.leo.erp.system.securitykey.service.SecurityKeyService;
import com.leo.erp.system.securitykey.service.TotpSecretCryptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssSecretCryptorTest {

    @Test
    void shouldEncryptWithActiveTotpMaterial() {
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        TotpSecretCryptor cryptor = mock(TotpSecretCryptor.class);
        when(securityKeyService.getActiveTotpMaterial()).thenReturn(activeMaterial("master-key"));
        when(cryptor.encrypt("plain-secret", "master-key")).thenReturn("encrypted-secret");

        OssSecretCryptor ossSecretCryptor = new OssSecretCryptor(securityKeyService, cryptor);

        assertThat(ossSecretCryptor.encrypt("plain-secret")).isEqualTo("encrypted-secret");
        verify(cryptor).encrypt("plain-secret", "master-key");
    }

    @Test
    void shouldDecryptWithActiveTotpMaterial() {
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        TotpSecretCryptor cryptor = mock(TotpSecretCryptor.class);
        when(securityKeyService.getActiveTotpMaterial()).thenReturn(activeMaterial("master-key"));
        when(cryptor.decrypt("encrypted-secret", "master-key")).thenReturn("plain-secret");

        OssSecretCryptor ossSecretCryptor = new OssSecretCryptor(securityKeyService, cryptor);

        assertThat(ossSecretCryptor.decrypt("encrypted-secret")).isEqualTo("plain-secret");
        verify(cryptor).decrypt("encrypted-secret", "master-key");
    }

    @Test
    void shouldRejectBlankSecrets() {
        OssSecretCryptor ossSecretCryptor = new OssSecretCryptor(
                mock(SecurityKeyService.class),
                mock(TotpSecretCryptor.class)
        );

        assertThatThrownBy(() -> ossSecretCryptor.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OSS Secret Key 不能为空");
        assertThatThrownBy(() -> ossSecretCryptor.encrypt(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OSS Secret Key 不能为空");
        assertThatThrownBy(() -> ossSecretCryptor.decrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OSS Secret Key 未配置");
        assertThatThrownBy(() -> ossSecretCryptor.decrypt(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OSS Secret Key 未配置");
    }

    private SecurityKeyService.ResolvedSecretMaterial activeMaterial(String secretValue) {
        return new SecurityKeyService.ResolvedSecretMaterial(
                SecurityKeyService.SOURCE_DATABASE,
                1,
                secretValue,
                null,
                null,
                "fingerprint"
        );
    }
}
