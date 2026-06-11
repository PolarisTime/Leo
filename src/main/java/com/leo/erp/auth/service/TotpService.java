package com.leo.erp.auth.service;

import com.leo.erp.security.totp.TotpProperties;
import com.leo.erp.system.securitykey.service.SecurityKeyService;
import com.leo.erp.system.securitykey.service.TotpSecretCryptor;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrDataFactory;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Service;

@Service
public class TotpService {

    private static final int TOTP_SECRET_LENGTH = 20;
    private static final int TOTP_DIGITS = 6;
    private static final int TOTP_PERIOD_SECONDS = 30;
    private static final int QR_CODE_IMAGE_SIZE = 300;

    private final TotpProperties properties;
    private final SecurityKeyService securityKeyService;
    private final TotpSecretCryptor totpSecretCryptor;
    private final DefaultSecretGenerator secretGenerator;
    private final QrDataFactory qrDataFactory;
    private final DefaultCodeVerifier verifier;

    public TotpService(TotpProperties properties,
                       SecurityKeyService securityKeyService,
                       TotpSecretCryptor totpSecretCryptor) {
        this.properties = properties;
        this.securityKeyService = securityKeyService;
        this.totpSecretCryptor = totpSecretCryptor;
        this.secretGenerator = new DefaultSecretGenerator(TOTP_SECRET_LENGTH);
        this.qrDataFactory = new QrDataFactory(HashingAlgorithm.SHA1, TOTP_DIGITS, TOTP_PERIOD_SECONDS);
        this.verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
    }

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public String buildQrCodeUri(String secret, String loginName) {
        return buildQrData(secret, loginName).getUri();
    }

    public byte[] generateQrCodeImage(String secret, String loginName) {
        try {
            ZxingPngQrGenerator generator = new ZxingPngQrGenerator();
            generator.setImageSize(QR_CODE_IMAGE_SIZE);
            return generator.generate(buildQrData(secret, loginName));
        } catch (QrGenerationException e) {
            throw new IllegalStateException("QR码生成失败", e);
        }
    }

    public boolean verifyCode(String secret, String code) {
        return verifier.isValidCode(secret, code);
    }

    public String encryptSecret(String plainSecret) {
        return totpSecretCryptor.encrypt(plainSecret, securityKeyService.getActiveTotpMaterial().secretValue());
    }

    public String decryptSecret(String encryptedSecret) {
        return totpSecretCryptor.decrypt(encryptedSecret, securityKeyService.getActiveTotpMaterial().secretValue());
    }

    private QrData buildQrData(String secret, String loginName) {
        return qrDataFactory.newBuilder()
                .label(loginName)
                .secret(secret)
                .issuer(properties.getIssuer())
                .algorithm(HashingAlgorithm.SHA1)
                .digits(TOTP_DIGITS)
                .period(TOTP_PERIOD_SECONDS)
                .build();
    }
}
