package com.leo.erp.attachment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.jwt.JwtProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Service
public class AttachmentDirectUploadTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_SEPARATOR = ".";

    private final JwtProperties jwtProperties;
    private final String explicitSecret;

    @Autowired
    public AttachmentDirectUploadTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.explicitSecret = null;
    }

    AttachmentDirectUploadTokenService(String explicitSecret) {
        this.jwtProperties = null;
        this.explicitSecret = explicitSecret;
    }

    public String issue(DirectUploadTokenPayload payload) {
        String encodedPayload = encode(payload.serialize());
        return encodedPayload + TOKEN_SEPARATOR + signature(encodedPayload);
    }

    public DirectUploadTokenPayload verify(
            String token, Long expectedAttachmentId, String expectedModuleKey, Long expectedOwnerUserId) {
        DirectUploadTokenPayload payload = parseAndVerify(token);
        if (!payload.attachmentId().equals(expectedAttachmentId)
                || !safeEquals(payload.moduleKey(), normalizeModuleKey(expectedModuleKey))
                || !payload.ownerUserId().equals(expectedOwnerUserId)) {
            throw invalidToken();
        }
        if (payload.expiresAtEpochSecond() < Instant.now().getEpochSecond()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "直传凭证已过期");
        }
        return payload;
    }

    private DirectUploadTokenPayload parseAndVerify(String token) {
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw invalidToken();
        }
        String expectedSignature = signature(parts[0]);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw invalidToken();
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            return DirectUploadTokenPayload.deserialize(decoded);
        } catch (IllegalArgumentException ex) {
            throw invalidToken();
        }
    }

    private String signature(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return encode(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "直传凭证签名失败");
        }
    }

    private String signingSecret() {
        String secret = explicitSecret != null ? explicitSecret : jwtProperties == null ? null : jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "直传凭证密钥未配置");
        }
        return secret;
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String encode(String value) {
        return encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean safeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeModuleKey(String moduleKey) {
        return moduleKey == null ? "" : moduleKey.trim();
    }

    private BusinessException invalidToken() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "直传凭证无效");
    }

    public record DirectUploadTokenPayload(
            Long attachmentId,
            String objectKey,
            String storagePath,
            String storedFileName,
            String originalFileName,
            String contentType,
            long fileSize,
            String sourceType,
            String moduleKey,
            Long ownerUserId,
            String sha256Hex,
            long expiresAtEpochSecond
    ) {

        private static final int FIELD_COUNT = 12;

        private String serialize() {
            return String.join("\n",
                    String.valueOf(attachmentId),
                    encodeField(objectKey),
                    encodeField(storagePath),
                    encodeField(storedFileName),
                    encodeField(originalFileName),
                    encodeField(contentType),
                    String.valueOf(fileSize),
                    encodeField(sourceType),
                    encodeField(moduleKey),
                    String.valueOf(ownerUserId),
                    encodeField(sha256Hex),
                    String.valueOf(expiresAtEpochSecond)
            );
        }

        private static DirectUploadTokenPayload deserialize(String value) {
            String[] fields = value.split("\n", -1);
            if (fields.length != FIELD_COUNT) {
                throw new IllegalArgumentException("invalid field count");
            }
            return new DirectUploadTokenPayload(
                    Long.valueOf(fields[0]),
                    decodeField(fields[1]),
                    decodeField(fields[2]),
                    decodeField(fields[3]),
                    decodeField(fields[4]),
                    decodeField(fields[5]),
                    Long.parseLong(fields[6]),
                    decodeField(fields[7]),
                    decodeField(fields[8]),
                    Long.valueOf(fields[9]),
                    decodeField(fields[10]),
                    Long.parseLong(fields[11])
            );
        }

        private static String encodeField(String value) {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        }

        private static String decodeField(String value) {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        }
    }
}
