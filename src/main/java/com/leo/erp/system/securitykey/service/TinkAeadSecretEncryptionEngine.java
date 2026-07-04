package com.leo.erp.system.securitykey.service;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeyStatus;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.AesGcmKey;
import com.google.crypto.tink.aead.AesGcmParameters;
import com.google.crypto.tink.util.SecretBytes;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

final class TinkAeadSecretEncryptionEngine implements SecretEncryptionEngine {

    private static final int TINK_KEY_ID = 0x1E0A0D01;
    private static final byte[] ASSOCIATED_DATA = "leo.security.encryption.tink1".getBytes(StandardCharsets.UTF_8);

    TinkAeadSecretEncryptionEngine() {
        try {
            AeadConfig.register();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("初始化Tink AEAD失败", e);
        }
    }

    @Override
    public String encrypt(String plainSecret, String encryptionKey) {
        try {
            byte[] encrypted = aead(encryptionKey).encrypt(plainSecret.getBytes(StandardCharsets.UTF_8), ASSOCIATED_DATA);
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP密钥加密失败", e);
        }
    }

    @Override
    public String decrypt(String encryptedSecret, String encryptionKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedSecret);
            byte[] decrypted = aead(encryptionKey).decrypt(decoded, ASSOCIATED_DATA);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("TOTP密钥解密失败", e);
        }
    }

    private Aead aead(String encryptionKey) throws GeneralSecurityException {
        AesGcmParameters parameters = AesGcmParameters.builder()
                .setKeySizeBytes(32)
                .setIvSizeBytes(12)
                .setTagSizeBytes(16)
                .setVariant(AesGcmParameters.Variant.TINK)
                .build();
        AesGcmKey key = AesGcmKey.builder()
                .setParameters(parameters)
                .setKeyBytes(SecretBytes.copyFrom(AesKeyMaterial.derive256BitKey(encryptionKey), InsecureSecretKeyAccess.get()))
                .setIdRequirement(TINK_KEY_ID)
                .build();
        KeysetHandle keysetHandle = KeysetHandle.newBuilder()
                .addEntry(KeysetHandle.importKey(key)
                        .withFixedId(TINK_KEY_ID)
                        .setStatus(KeyStatus.ENABLED)
                        .makePrimary())
                .build();
        return keysetHandle.getPrimitive(Aead.class);
    }
}
