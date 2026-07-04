package com.leo.erp.system.securitykey.service;

import java.nio.charset.StandardCharsets;

final class AesKeyMaterial {

    private static final int AES_256_KEY_LENGTH = 32;

    private AesKeyMaterial() {
    }

    static byte[] derive256BitKey(String encryptionKey) {
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[AES_256_KEY_LENGTH];
        System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, AES_256_KEY_LENGTH));
        return padded;
    }
}
