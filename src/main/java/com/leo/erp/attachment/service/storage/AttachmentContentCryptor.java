package com.leo.erp.attachment.service.storage;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.securitykey.service.SecurityKeyService;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

@Component
public class AttachmentContentCryptor {

    private static final byte[] MAGIC = "LEOENC1".getBytes(StandardCharsets.US_ASCII);
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecurityKeyService securityKeyService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AttachmentContentCryptor(SecurityKeyService securityKeyService) {
        this.securityKeyService = securityKeyService;
    }

    public byte[] encrypt(byte[] plainContent) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainContent);
            return ByteBuffer.allocate(MAGIC.length + iv.length + encrypted.length)
                    .put(MAGIC)
                    .put(iv)
                    .put(encrypted)
                    .array();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件加密失败");
        }
    }

    public byte[] decrypt(byte[] encryptedContent) {
        if (encryptedContent.length <= MAGIC.length + GCM_IV_LENGTH || !hasMagic(encryptedContent)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件加密内容格式错误");
        }
        try {
            byte[] iv = Arrays.copyOfRange(encryptedContent, MAGIC.length, MAGIC.length + GCM_IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(encryptedContent, MAGIC.length + GCM_IV_LENGTH, encryptedContent.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(cipherText);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件解密失败");
        }
    }

    public byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean hasMagic(byte[] content) {
        if (content.length < MAGIC.length) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (content[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private SecretKeySpec key() {
        String material = securityKeyService.getActiveDataMaterial().secretValue();
        if (material == null || material.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件加密主密钥未配置");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("attachment-content:" + material).getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件加密主密钥初始化失败");
        }
    }
}
