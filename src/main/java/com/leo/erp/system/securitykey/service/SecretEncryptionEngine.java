package com.leo.erp.system.securitykey.service;

interface SecretEncryptionEngine {

    String encrypt(String plainSecret, String encryptionKey);

    String decrypt(String encryptedSecret, String encryptionKey);
}
