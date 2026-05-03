package com.leo.erp.system.securitykey.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.jwt.JwtProperties;
import com.leo.erp.security.totp.TotpProperties;
import com.leo.erp.system.securitykey.domain.entity.SecuritySecret;
import com.leo.erp.system.securitykey.repository.SecuritySecretRepository;
import com.leo.erp.system.securitykey.web.dto.SecurityKeyItemResponse;
import com.leo.erp.system.securitykey.web.dto.SecurityKeyOverviewResponse;
import com.leo.erp.system.securitykey.web.dto.SecurityKeyRotateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Service
public class SecurityKeyService {

    public static final String SOURCE_CONFIG = "CONFIG_FILE";
    public static final String SOURCE_DATABASE = "DATABASE";
    public static final String SECRET_TYPE_JWT = "JWT_MASTER";
    public static final String SECRET_TYPE_TOTP = "TOTP_MASTER";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_RETIRED = "RETIRED";

    private final SecuritySecretRepository repository;
    private final UserAccountRepository userAccountRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final JwtProperties jwtProperties;
    private final TotpProperties totpProperties;
    private final TotpSecretCryptor totpSecretCryptor;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecurityKeyService(SecuritySecretRepository repository,
                              UserAccountRepository userAccountRepository,
                              SnowflakeIdGenerator idGenerator,
                              JwtProperties jwtProperties,
                              TotpProperties totpProperties,
                              TotpSecretCryptor totpSecretCryptor) {
        this.repository = repository;
        this.userAccountRepository = userAccountRepository;
        this.idGenerator = idGenerator;
        this.jwtProperties = jwtProperties;
        this.totpProperties = totpProperties;
        this.totpSecretCryptor = totpSecretCryptor;
    }

    public SecurityKeyOverviewResponse getOverview() {
        return new SecurityKeyOverviewResponse(
                buildItemResponse(SECRET_TYPE_JWT, "JWT 主密钥", 0),
                buildItemResponse(
                        SECRET_TYPE_TOTP,
                        "2FA 主密钥",
                        userAccountRepository.findByTotpSecretIsNotNullAndDeletedFlagFalse().size()
                )
        );
    }

    public ResolvedSecretMaterial getActiveJwtMaterial() {
        return resolveActiveMaterial(SECRET_TYPE_JWT);
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
                .map(secret -> new ResolvedSecretMaterial(
                        SOURCE_DATABASE,
                        secret.getKeyVersion(),
                        secret.getSecretValue(),
                        secret.getActivatedAt(),
                        secret.getRetiredAt(),
                        fingerprint(secret.getSecretValue())
                )).toList();
    }

    public ResolvedSecretMaterial getActiveTotpMaterial() {
        return resolveActiveMaterial(SECRET_TYPE_TOTP);
    }

    @Transactional
    public SecurityKeyRotateResponse rotateJwtMasterKey() {
        LocalDateTime now = LocalDateTime.now();
        ResolvedSecretMaterial current = resolveActiveMaterial(SECRET_TYPE_JWT);
        SecuritySecret currentActive = repository
                .findFirstBySecretTypeAndStatusAndDeletedFlagFalseOrderByKeyVersionDesc(SECRET_TYPE_JWT, STATUS_ACTIVE)
                .orElse(null);
        int nextVersion = nextVersion(SECRET_TYPE_JWT);

        if (currentActive != null) {
            retire(currentActive, now, "JWT 历史主密钥");
        } else {
            persistSecret(SECRET_TYPE_JWT, "JWT 历史主密钥", nextVersion, current.secretValue(), STATUS_RETIRED, now, now,
                    "首次轮转时从配置文件导入的历史密钥");
            nextVersion++;
        }

        String newSecret = randomSecret(64);
        SecuritySecret active = persistSecret(
                SECRET_TYPE_JWT,
                "JWT 主密钥",
                nextVersion,
                newSecret,
                STATUS_ACTIVE,
                now,
                null,
                "通过系统设置轮转生成"
        );
        return buildRotateResponse(SECRET_TYPE_JWT, active, 0, "JWT 主密钥轮转完成");
    }

    @Transactional
    public SecurityKeyRotateResponse rotateTotpMasterKey() {
        LocalDateTime now = LocalDateTime.now();
        ResolvedSecretMaterial current = resolveActiveMaterial(SECRET_TYPE_TOTP);
        String newSecret = randomSecret(48);
        List<UserAccount> accounts = new ArrayList<>(userAccountRepository.findByTotpSecretIsNotNullAndDeletedFlagFalse());
        List<String> invalidEnabledAccounts = new ArrayList<>();
        for (UserAccount account : accounts) {
            String encryptedSecret = account.getTotpSecret();
            if (encryptedSecret == null || encryptedSecret.isBlank()) {
                if (!Boolean.TRUE.equals(account.getTotpEnabled())) {
                    account.setTotpSecret(null);
                    continue;
                }
                invalidEnabledAccounts.add(account.getLoginName());
                continue;
            }
            try {
                String plainSecret = totpSecretCryptor.decrypt(encryptedSecret, current.secretValue());
                account.setTotpSecret(totpSecretCryptor.encrypt(plainSecret, newSecret));
            } catch (IllegalStateException ex) {
                if (Boolean.TRUE.equals(account.getTotpEnabled())) {
                    invalidEnabledAccounts.add(account.getLoginName());
                } else {
                    account.setTotpSecret(null);
                }
            }
        }
        if (!invalidEnabledAccounts.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "以下账号的2FA密钥无法使用当前主密钥解密，请先禁用并重新生成后再轮转: " + String.join(", ", invalidEnabledAccounts)
            );
        }
        if (!accounts.isEmpty()) {
            userAccountRepository.saveAll(accounts);
        }

        SecuritySecret currentActive = repository
                .findFirstBySecretTypeAndStatusAndDeletedFlagFalseOrderByKeyVersionDesc(SECRET_TYPE_TOTP, STATUS_ACTIVE)
                .orElse(null);
        int nextVersion = nextVersion(SECRET_TYPE_TOTP);
        if (currentActive != null) {
            retire(currentActive, now, "2FA 历史主密钥");
        } else {
            persistSecret(SECRET_TYPE_TOTP, "2FA 历史主密钥", nextVersion, current.secretValue(), STATUS_RETIRED, now, now,
                    "首次轮转时从配置文件导入的历史密钥");
            nextVersion++;
        }

        SecuritySecret active = persistSecret(
                SECRET_TYPE_TOTP,
                "2FA 主密钥",
                nextVersion,
                newSecret,
                STATUS_ACTIVE,
                now,
                null,
                "通过系统设置轮转生成"
        );
        return buildRotateResponse(SECRET_TYPE_TOTP, active, accounts.size(), "2FA 主密钥轮转并完成密钥重加密");
    }

    private SecurityKeyItemResponse buildItemResponse(String secretType, String keyName, int protectedCount) {
        ResolvedSecretMaterial active = resolveActiveMaterial(secretType);
        int retiredCount = repository.findBySecretTypeAndStatusInAndDeletedFlagFalseOrderByKeyVersionDesc(
                secretType,
                List.of(STATUS_RETIRED)
        ).size();
        String remark = SOURCE_CONFIG.equals(active.source())
                ? "当前使用配置文件主密钥，执行轮转后会切换为数据库托管"
                : "当前使用数据库托管主密钥，轮转后立即对新会话生效";
        return new SecurityKeyItemResponse(
                secretType,
                keyName,
                active.source(),
                active.version(),
                active.fingerprint(),
                active.activatedAt(),
                retiredCount,
                protectedCount,
                remark
        );
    }

    private ResolvedSecretMaterial resolveActiveMaterial(String secretType) {
        return repository.findFirstBySecretTypeAndStatusAndDeletedFlagFalseOrderByKeyVersionDesc(secretType, STATUS_ACTIVE)
                .map(secret -> new ResolvedSecretMaterial(
                        SOURCE_DATABASE,
                        secret.getKeyVersion(),
                        secret.getSecretValue(),
                        secret.getActivatedAt(),
                        secret.getRetiredAt(),
                        fingerprint(secret.getSecretValue())
                ))
                .orElseGet(() -> resolveConfigMaterial(secretType));
    }

    private boolean isJwtVerificationMaterialUsable(SecuritySecret secret, LocalDateTime now) {
        if (!STATUS_RETIRED.equals(secret.getStatus())) {
            return true;
        }
        LocalDateTime retiredAt = secret.getRetiredAt();
        if (retiredAt == null) {
            return true;
        }
        LocalDateTime legacyWindowEnd = retiredAt.plus(Duration.ofMillis(jwtProperties.accessExpirationMs()));
        return !legacyWindowEnd.isBefore(now);
    }

    private ResolvedSecretMaterial resolveConfigMaterial(String secretType) {
        String secretValue = SECRET_TYPE_JWT.equals(secretType) ? jwtProperties.secret() : totpProperties.encryptionKey();
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
            if (SECRET_TYPE_JWT.equals(secretType) && secretValue.length() < 32) {
                throw new IllegalStateException("启动兜底环境变量 LEO_JWT_SECRET 长度不能少于 32 位");
            }
            return;
        }

        String configName = SECRET_TYPE_JWT.equals(secretType) ? "LEO_JWT_SECRET" : "TOTP_ENCRYPTION_KEY";
        String secretName = SECRET_TYPE_JWT.equals(secretType) ? "JWT 主密钥" : "2FA 主密钥";
        throw new IllegalStateException(
                "数据库中未找到可用的" + secretName + "，且启动兜底环境变量 " + configName + " 未配置"
        );
    }

    private SecuritySecret persistSecret(String secretType,
                                         String secretName,
                                         int version,
                                         String secretValue,
                                         String status,
                                         LocalDateTime activatedAt,
                                         LocalDateTime retiredAt,
                                         String remark) {
        SecuritySecret secret = new SecuritySecret();
        secret.setId(idGenerator.nextId());
        secret.setSecretType(secretType);
        secret.setSecretName(secretName);
        secret.setKeyVersion(version);
        secret.setSecretValue(secretValue);
        secret.setStatus(status);
        secret.setActivatedAt(Objects.requireNonNullElseGet(activatedAt, LocalDateTime::now));
        secret.setRetiredAt(retiredAt);
        secret.setRemark(remark);
        return repository.save(secret);
    }

    private void retire(SecuritySecret secret, LocalDateTime now, String secretName) {
        secret.setStatus(STATUS_RETIRED);
        secret.setRetiredAt(now);
        secret.setSecretName(secretName);
        repository.save(secret);
    }

    private int nextVersion(String secretType) {
        return repository.findFirstBySecretTypeAndDeletedFlagFalseOrderByKeyVersionDesc(secretType)
                .map(secret -> secret.getKeyVersion() + 1)
                .orElse(1);
    }

    private SecurityKeyRotateResponse buildRotateResponse(String keyCode,
                                                          SecuritySecret active,
                                                          int processedRecordCount,
                                                          String remark) {
        int retiredCount = repository.findBySecretTypeAndStatusInAndDeletedFlagFalseOrderByKeyVersionDesc(
                keyCode,
                List.of(STATUS_RETIRED)
        ).size();
        return new SecurityKeyRotateResponse(
                keyCode,
                SOURCE_DATABASE,
                active.getKeyVersion(),
                fingerprint(active.getSecretValue()),
                active.getActivatedAt(),
                processedRecordCount,
                retiredCount,
                remark
        );
    }

    private String randomSecret(int byteSize) {
        byte[] bytes = new byte[byteSize];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String fingerprint(String rawSecret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawSecret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12).toUpperCase();
        } catch (Exception e) {
            throw new IllegalStateException("生成密钥指纹失败", e);
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
