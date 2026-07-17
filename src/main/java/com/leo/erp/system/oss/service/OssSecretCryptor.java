package com.leo.erp.system.oss.service;

import com.leo.erp.system.securitykey.service.SecurityKeyService;
import com.leo.erp.system.securitykey.service.DataSecretCryptor;
import org.springframework.stereotype.Component;

@Component
public class OssSecretCryptor {

    private final SecurityKeyService securityKeyService;
    private final DataSecretCryptor cryptor;

    public OssSecretCryptor(SecurityKeyService securityKeyService, DataSecretCryptor cryptor) {
        this.securityKeyService = securityKeyService;
        this.cryptor = cryptor;
    }

    public String encrypt(String plainSecret) {
        if (plainSecret == null || plainSecret.isBlank()) {
            throw new IllegalArgumentException("OSS Secret Key 不能为空");
        }
        return cryptor.encrypt(plainSecret, encryptionKey());
    }

    public String decrypt(String encryptedSecret) {
        if (encryptedSecret == null || encryptedSecret.isBlank()) {
            throw new IllegalArgumentException("OSS Secret Key 未配置");
        }
        return cryptor.decrypt(encryptedSecret, encryptionKey());
    }

    private String encryptionKey() {
        return securityKeyService.getActiveDataMaterial().secretValue();
    }
}
