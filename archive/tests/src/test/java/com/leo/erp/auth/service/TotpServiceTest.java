package com.leo.erp.auth.service;

import com.leo.erp.security.totp.TotpProperties;
import com.leo.erp.system.securitykey.service.SecurityKeyService;
import com.leo.erp.system.securitykey.service.TotpSecretCryptor;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TotpServiceTest {

    @Test
    void generateSecretShouldReturnNonBlankString() {
        TotpService service = new TotpService(
                new TotpProperties("TestIssuer", null), null, null
        );

        String secret = service.generateSecret();

        assertThat(secret).isNotBlank();
    }

    @Test
    void buildQrCodeUriShouldContainIssuerAndLoginName() {
        TotpService service = new TotpService(
                new TotpProperties("LeoERP", null), null, null
        );

        String uri = service.buildQrCodeUri("JBSWY3DPEHPK3PXP", "tester");

        assertThat(uri).contains("LeoERP").contains("tester");
    }

    @Test
    void encryptSecretShouldDelegateToCryptor() {
        SecurityKeyService keyService = mock(SecurityKeyService.class);
        TotpSecretCryptor cryptor = mock(TotpSecretCryptor.class);
        when(keyService.getActiveTotpMaterial()).thenReturn(
                new SecurityKeyService.ResolvedSecretMaterial(
                        "DATABASE", 1, "key-material", null, null, "AB12CD"
                )
        );
        when(cryptor.encrypt("plain", "key-material")).thenReturn("encrypted-value");

        TotpService service = new TotpService(
                new TotpProperties("test", null), keyService, cryptor
        );

        String result = service.encryptSecret("plain");

        assertThat(result).isEqualTo("encrypted-value");
        verify(cryptor).encrypt("plain", "key-material");
    }

    @Test
    void decryptSecretShouldDelegateToCryptor() {
        SecurityKeyService keyService = mock(SecurityKeyService.class);
        TotpSecretCryptor cryptor = mock(TotpSecretCryptor.class);
        when(keyService.getActiveTotpMaterial()).thenReturn(
                new SecurityKeyService.ResolvedSecretMaterial(
                        "DATABASE", 1, "key-material", null, null, "AB12CD"
                )
        );
        when(cryptor.decrypt(anyString(), anyString())).thenReturn("decrypted-secret");

        TotpService service = new TotpService(
                new TotpProperties("test", null), keyService, cryptor
        );

        String result = service.decryptSecret("encrypted");

        assertThat(result).isEqualTo("decrypted-secret");
    }

    @Test
    void generateQrCodeImageShouldReturnPngBytes() {
        TotpService service = new TotpService(
                new TotpProperties("LeoERP", null), null, null
        );

        byte[] image = service.generateQrCodeImage("JBSWY3DPEHPK3PXP", "tester");

        assertThat(image).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
    }

    @Test
    void generateQrCodeImageShouldWrapGeneratorFailure() {
        TotpService service = new TotpService(
                new TotpProperties("LeoERP", null), null, null
        );

        try (var generators = Mockito.mockConstruction(ZxingPngQrGenerator.class, (generator, context) ->
                Mockito.when(generator.generate(Mockito.any(QrData.class)))
                        .thenThrow(new QrGenerationException("failed", new RuntimeException("boom"))))) {
            assertThatThrownBy(() -> service.generateQrCodeImage("JBSWY3DPEHPK3PXP", "tester"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("QR码生成失败")
                    .hasCauseInstanceOf(QrGenerationException.class);
        }
    }

    @Test
    void verifyCodeShouldRejectInvalidCode() {
        TotpService service = new TotpService(
                new TotpProperties("LeoERP", null), null, null
        );

        assertThat(service.verifyCode("JBSWY3DPEHPK3PXP", "000000")).isFalse();
    }
}
