package com.leo.erp.system.securitykey.service;

import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DataSecretCryptor {

    private static final String TINK_PREFIX = "TINK1:";

    private final SecretEncryptionEngine tinkEngine;

    public DataSecretCryptor() {
        this(new TinkAeadSecretEncryptionEngine());
    }

    DataSecretCryptor(SecretEncryptionEngine tinkEngine) {
        this.tinkEngine = Objects.requireNonNull(tinkEngine, "tinkEngine must not be null");
    }

    public String encrypt(String plainSecret, String encryptionKey) {
        return TINK_PREFIX + tinkEngine.encrypt(plainSecret, encryptionKey);
    }

    public String decrypt(String encryptedSecret, String encryptionKey) {
        if (encryptedSecret == null || !encryptedSecret.startsWith(TINK_PREFIX)) {
            throw new IllegalStateException("数据密钥密文格式不受支持");
        }
        return tinkEngine.decrypt(encryptedSecret.substring(TINK_PREFIX.length()), encryptionKey);
    }
}
