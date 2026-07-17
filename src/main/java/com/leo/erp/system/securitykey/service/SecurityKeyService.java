package com.leo.erp.system.securitykey.service;

import com.leo.erp.security.jwt.JwtProperties;
import com.leo.erp.system.securitykey.domain.entity.SecuritySecret;
import com.leo.erp.system.securitykey.repository.SecuritySecretRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class SecurityKeyService {

    public static final String SOURCE_CONFIG = "CONFIG_FILE";
    public static final String SOURCE_DATABASE = "DATABASE";
    public static final String SECRET_TYPE_JWT = "JWT_MASTER";
    public static final String SECRET_TYPE_DATA = "DATA_MASTER";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_RETIRED = "RETIRED";

    private final SecuritySecretRepository repository;
    private final JwtProperties jwtProperties;
    private final String dataEncryptionKey;

    public SecurityKeyService(SecuritySecretRepository repository,
                              JwtProperties jwtProperties,
                              @Value("${leo.security.data-encryption-key:}") String dataEncryptionKey) {
        this.repository = repository;
        this.jwtProperties = jwtProperties;
        this.dataEncryptionKey = dataEncryptionKey;
    }

    public ResolvedSecretMaterial getActiveJwtMaterial() {
        return resolveActiveMaterial(SECRET_TYPE_JWT);
    }

    public ResolvedSecretMaterial getActiveDataMaterial() {
        return resolveActiveMaterial(SECRET_TYPE_DATA);
    }

    public List<ResolvedSecretMaterial> getJwtVerificationMaterials() {
        List<SecuritySecret> dbKeys = repository.findBySecretTypeAndStatusInAndDeletedFlagFalseOrderByKeyVersionDesc(
                SECRET_TYPE_JWT,
                List.of(STATUS_ACTIVE, STATUS_RETIRED)
        );
        if (dbKeys.isEmpty()) {
            return List.of(resolveConfigMaterial(SECRET_TYPE_JWT));
        }
        LocalDateTime now = LocalDateTime.now();
        return dbKeys.stream()
                .filter(secret -> isJwtVerificationMaterialUsable(secret, now))
                .map(this::toMaterial)
                .toList();
    }

    private ResolvedSecretMaterial resolveActiveMaterial(String secretType) {
        return repository.findFirstBySecretTypeAndStatusAndDeletedFlagFalseOrderByKeyVersionDesc(secretType, STATUS_ACTIVE)
                .map(this::toMaterial)
                .orElseGet(() -> resolveConfigMaterial(secretType));
    }

    private ResolvedSecretMaterial toMaterial(SecuritySecret secret) {
        return new ResolvedSecretMaterial(
                SOURCE_DATABASE,
                secret.getKeyVersion(),
                secret.getSecretValue(),
                secret.getActivatedAt(),
                secret.getRetiredAt(),
                fingerprint(secret.getSecretValue())
        );
    }

    private boolean isJwtVerificationMaterialUsable(SecuritySecret secret, LocalDateTime now) {
        if (!STATUS_RETIRED.equals(secret.getStatus()) || secret.getRetiredAt() == null) {
            return true;
        }
        return !secret.getRetiredAt()
                .plus(Duration.ofMillis(jwtProperties.getAccessExpirationMs()))
                .isBefore(now);
    }

    private ResolvedSecretMaterial resolveConfigMaterial(String secretType) {
        String secretValue = SECRET_TYPE_JWT.equals(secretType)
                ? jwtProperties.getSecret()
                : dataEncryptionKey;
        requireConfigMaterial(secretType, secretValue);
        return new ResolvedSecretMaterial(
                SOURCE_CONFIG,
                0,
                secretValue,
                null,
                null,
                fingerprint(secretValue)
        );
    }

    private void requireConfigMaterial(String secretType, String secretValue) {
        if (secretValue != null && !secretValue.isBlank()) {
            if (secretValue.length() < 32) {
                throw new IllegalStateException(configName(secretType) + " 长度不能少于 32 位");
            }
            return;
        }
        throw new IllegalStateException(
                "数据库中未找到可用的" + secretName(secretType) + "，且启动兜底环境变量 "
                        + configName(secretType) + " 未配置"
        );
    }

    private String configName(String secretType) {
        return SECRET_TYPE_JWT.equals(secretType) ? "LEO_JWT_SECRET" : "LEO_DATA_ENCRYPTION_KEY";
    }

    private String secretName(String secretType) {
        return SECRET_TYPE_JWT.equals(secretType) ? "JWT 主密钥" : "数据加密主密钥";
    }

    private String fingerprint(String rawSecret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawSecret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12).toUpperCase();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("生成密钥指纹失败", ex);
        }
    }

    public record ResolvedSecretMaterial(
            String source,
            Integer version,
            String secretValue,
            LocalDateTime activatedAt,
            LocalDateTime retiredAt,
            String fingerprint
    ) {
    }
}
