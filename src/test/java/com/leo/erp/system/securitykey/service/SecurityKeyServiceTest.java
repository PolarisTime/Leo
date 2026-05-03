package com.leo.erp.system.securitykey.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.jwt.JwtProperties;
import com.leo.erp.security.totp.TotpProperties;
import com.leo.erp.system.securitykey.domain.entity.SecuritySecret;
import com.leo.erp.system.securitykey.repository.SecuritySecretRepository;
import com.leo.erp.system.securitykey.web.dto.SecurityKeyOverviewResponse;
import com.leo.erp.system.securitykey.web.dto.SecurityKeyRotateResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityKeyServiceTest {

    @Test
    void shouldExposeConfigFileAsCurrentSourceWhenDatabaseSecretNotRotated() {
        SecuritySecretRepository repository = mock(SecuritySecretRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        TotpSecretCryptor cryptor = mock(TotpSecretCryptor.class);
        when(repository.findFirstBySecretTypeAndStatusAndDeletedFlagFalseOrderByKeyVersionDesc(any(), eq("ACTIVE")))
                .thenReturn(Optional.empty());
        when(repository.findBySecretTypeAndStatusInAndDeletedFlagFalseOrderByKeyVersionDesc(any(), anyCollection()))
                .thenReturn(List.of());
        when(userAccountRepository.findByTotpSecretIsNotNullAndDeletedFlagFalse()).thenReturn(List.of());

        SecurityKeyService service = new SecurityKeyService(
                repository,
                userAccountRepository,
                idGenerator,
                new JwtProperties("leo-erp", "leo-erp-jwt-secret-key-2026-must-be-long-enough-for-hs512", 1_800_000L, 604_800_000L),
                new TotpProperties("LeoERP", "leo-dev-totp-key-change-me-20260425"),
                cryptor
        );

        SecurityKeyOverviewResponse overview = service.getOverview();

        assertThat(overview.jwt().source()).isEqualTo(SecurityKeyService.SOURCE_CONFIG);
        assertThat(overview.totp().source()).isEqualTo(SecurityKeyService.SOURCE_CONFIG);
        assertThat(overview.jwt().activeVersion()).isZero();
    }

    @Test
    void shouldReadActiveSecretsFromDatabaseWhenFallbackSecretsAreEmpty() {
        SecuritySecretRepository repository = mock(SecuritySecretRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        TotpSecretCryptor cryptor = mock(TotpSecretCryptor.class);

        SecuritySecret jwtSecret = new SecuritySecret();
        jwtSecret.setSecretType(SecurityKeyService.SECRET_TYPE_JWT);
        jwtSecret.setKeyVersion(3);
        jwtSecret.setSecretValue("leo-erp-jwt-secret-key-2027-rotated-signature-material-is-long-enough");
        jwtSecret.setStatus("ACTIVE");

        SecuritySecret totpSecret = new SecuritySecret();
        totpSecret.setSecretType(SecurityKeyService.SECRET_TYPE_TOTP);
        totpSecret.setKeyVersion(4);
        totpSecret.setSecretValue("leo-dev-totp-key-managed-by-database-20260426");
        totpSecret.setStatus("ACTIVE");

        when(repository.findFirstBySecretTypeAndStatusAndDeletedFlagFalseOrderByKeyVersionDesc(
                SecurityKeyService.SECRET_TYPE_JWT, "ACTIVE"))
                .thenReturn(Optional.of(jwtSecret));
        when(repository.findFirstBySecretTypeAndStatusAndDeletedFlagFalseOrderByKeyVersionDesc(
                SecurityKeyService.SECRET_TYPE_TOTP, "ACTIVE"))
                .thenReturn(Optional.of(totpSecret));
        when(repository.findBySecretTypeAndStatusInAndDeletedFlagFalseOrderByKeyVersionDesc(
                eq(SecurityKeyService.SECRET_TYPE_JWT), anyCollection()))
                .thenReturn(List.of(jwtSecret));
        when(userAccountRepository.findByTotpSecretIsNotNullAndDeletedFlagFalse()).thenReturn(List.of());

        SecurityKeyService service = new SecurityKeyService(
                repository,
                userAccountRepository,
                idGenerator,
                new JwtProperties("leo-erp", null, 1_800_000L, 604_800_000L),
                new TotpProperties("LeoERP", null),
                cryptor
        );

        SecurityKeyOverviewResponse overview = service.getOverview();

        assertThat(overview.jwt().source()).isEqualTo(SecurityKeyService.SOURCE_DATABASE);
        assertThat(overview.jwt().activeVersion()).isEqualTo(3);
        assertThat(service.getActiveTotpMaterial().source()).isEqualTo(SecurityKeyService.SOURCE_DATABASE);
        assertThat(service.getActiveTotpMaterial().version()).isEqualTo(4);
    }

    @Test
    void shouldIgnoreRetiredJwtSecretsAfterAccessTokenWindow() {
        SecuritySecretRepository repository = mock(SecuritySecretRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        TotpSecretCryptor cryptor = mock(TotpSecretCryptor.class);

        SecuritySecret active = new SecuritySecret();
        active.setSecretType(SecurityKeyService.SECRET_TYPE_JWT);
        active.setKeyVersion(4);
        active.setSecretValue("leo-erp-jwt-secret-key-2027-active-signature-material-is-long-enough");
        active.setStatus("ACTIVE");

        SecuritySecret recentRetired = new SecuritySecret();
        recentRetired.setSecretType(SecurityKeyService.SECRET_TYPE_JWT);
        recentRetired.setKeyVersion(3);
        recentRetired.setSecretValue("leo-erp-jwt-secret-key-2027-recent-retired-material-is-long-enough");
        recentRetired.setStatus("RETIRED");
        recentRetired.setRetiredAt(LocalDateTime.now().minusMinutes(10));

        SecuritySecret staleRetired = new SecuritySecret();
        staleRetired.setSecretType(SecurityKeyService.SECRET_TYPE_JWT);
        staleRetired.setKeyVersion(1);
        staleRetired.setSecretValue("leo-erp-jwt-secret-key-weak-2026");
        staleRetired.setStatus("RETIRED");
        staleRetired.setRetiredAt(LocalDateTime.now().minusDays(1));

        when(repository.findBySecretTypeAndStatusInAndDeletedFlagFalseOrderByKeyVersionDesc(
                eq(SecurityKeyService.SECRET_TYPE_JWT), anyCollection()))
                .thenReturn(List.of(active, recentRetired, staleRetired));

        SecurityKeyService service = new SecurityKeyService(
                repository,
                userAccountRepository,
                idGenerator,
                new JwtProperties("leo-erp", null, 1_800_000L, 604_800_000L),
                new TotpProperties("LeoERP", null),
                cryptor
        );

        assertThat(service.getJwtVerificationMaterials())
                .extracting(SecurityKeyService.ResolvedSecretMaterial::version)
                .containsExactly(4, 3);
    }

    @Test
    void shouldRotateJwtKeyFromConfigFallback() {
        SecuritySecretRepository repository = mock(SecuritySecretRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        TotpSecretCryptor cryptor = mock(TotpSecretCryptor.class);
        when(idGenerator.nextId()).thenReturn(101L, 102L);
        when(repository.findFirstBySecretTypeAndStatusAndDeletedFlagFalseOrderByKeyVersionDesc(
                SecurityKeyService.SECRET_TYPE_JWT, "ACTIVE"))
                .thenReturn(Optional.empty(), Optional.empty());
        when(repository.findFirstBySecretTypeAndDeletedFlagFalseOrderByKeyVersionDesc(SecurityKeyService.SECRET_TYPE_JWT))
                .thenReturn(Optional.empty());
        when(repository.findBySecretTypeAndStatusInAndDeletedFlagFalseOrderByKeyVersionDesc(
                eq(SecurityKeyService.SECRET_TYPE_JWT), anyCollection()))
                .thenReturn(List.of(new SecuritySecret()));
        when(repository.save(any(SecuritySecret.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SecurityKeyService service = new SecurityKeyService(
                repository,
                userAccountRepository,
                idGenerator,
                new JwtProperties("leo-erp", "leo-erp-jwt-secret-key-2026-must-be-long-enough-for-hs512", 1_800_000L, 604_800_000L),
                new TotpProperties("LeoERP", "leo-dev-totp-key-change-me-20260425"),
                cryptor
        );

        SecurityKeyRotateResponse response = service.rotateJwtMasterKey();
        ArgumentCaptor<SecuritySecret> captor = ArgumentCaptor.forClass(SecuritySecret.class);
        verify(repository, times(2)).save(captor.capture());

        List<SecuritySecret> saved = captor.getAllValues();
        assertThat(saved.get(0).getStatus()).isEqualTo("RETIRED");
        assertThat(saved.get(0).getSecretValue()).isEqualTo("leo-erp-jwt-secret-key-2026-must-be-long-enough-for-hs512");
        assertThat(saved.get(1).getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.get(1).getKeyVersion()).isEqualTo(2);
        assertThat(saved.get(1).getSecretValue()).isNotEqualTo(saved.get(0).getSecretValue());
        assertThat(response.source()).isEqualTo(SecurityKeyService.SOURCE_DATABASE);
        assertThat(response.activeVersion()).isEqualTo(2);
    }

    @Test
    void shouldRotateTotpKeyAndReencryptExistingSecrets() {
        SecuritySecretRepository repository = mock(SecuritySecretRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        TotpSecretCryptor cryptor = mock(TotpSecretCryptor.class);
        when(idGenerator.nextId()).thenReturn(201L, 202L);
        when(repository.findFirstBySecretTypeAndStatusAndDeletedFlagFalseOrderByKeyVersionDesc(
                SecurityKeyService.SECRET_TYPE_TOTP, "ACTIVE"))
                .thenReturn(Optional.empty(), Optional.empty());
        when(repository.findFirstBySecretTypeAndDeletedFlagFalseOrderByKeyVersionDesc(SecurityKeyService.SECRET_TYPE_TOTP))
                .thenReturn(Optional.empty());
        when(repository.findBySecretTypeAndStatusInAndDeletedFlagFalseOrderByKeyVersionDesc(
                eq(SecurityKeyService.SECRET_TYPE_TOTP), anyCollection()))
                .thenReturn(List.of(new SecuritySecret()));
        when(repository.save(any(SecuritySecret.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cryptor.decrypt("old-encrypted", "leo-dev-totp-key-change-me-20260425")).thenReturn("JBSWY3DPEHPK3PXP");
        when(cryptor.encrypt(eq("JBSWY3DPEHPK3PXP"), any())).thenAnswer(invocation -> "new-encrypted-" + invocation.getArgument(1));
        UserAccount account = new UserAccount();
        account.setId(1001L);
        account.setLoginName("admin");
        account.setTotpSecret("old-encrypted");
        when(userAccountRepository.findByTotpSecretIsNotNullAndDeletedFlagFalse()).thenReturn(List.of(account));
        when(userAccountRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SecurityKeyService service = new SecurityKeyService(
                repository,
                userAccountRepository,
                idGenerator,
                new JwtProperties("leo-erp", "leo-erp-jwt-secret-key-2026-must-be-long-enough-for-hs512", 1_800_000L, 604_800_000L),
                new TotpProperties("LeoERP", "leo-dev-totp-key-change-me-20260425"),
                cryptor
        );

        SecurityKeyRotateResponse response = service.rotateTotpMasterKey();

        verify(userAccountRepository).saveAll(List.of(account));
        assertThat(account.getTotpSecret()).startsWith("new-encrypted-");
        assertThat(response.processedRecordCount()).isEqualTo(1);
        assertThat(response.activeVersion()).isEqualTo(2);
    }

    @Test
    void shouldIgnoreInvalidPendingTotpSecretsWhenRotating() {
        SecuritySecretRepository repository = mock(SecuritySecretRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        TotpSecretCryptor cryptor = mock(TotpSecretCryptor.class);
        when(idGenerator.nextId()).thenReturn(301L, 302L);
        when(repository.findFirstBySecretTypeAndStatusAndDeletedFlagFalseOrderByKeyVersionDesc(
                SecurityKeyService.SECRET_TYPE_TOTP, "ACTIVE"))
                .thenReturn(Optional.empty(), Optional.empty());
        when(repository.findFirstBySecretTypeAndDeletedFlagFalseOrderByKeyVersionDesc(SecurityKeyService.SECRET_TYPE_TOTP))
                .thenReturn(Optional.empty());
        when(repository.findBySecretTypeAndStatusInAndDeletedFlagFalseOrderByKeyVersionDesc(
                eq(SecurityKeyService.SECRET_TYPE_TOTP), anyCollection()))
                .thenReturn(List.of(new SecuritySecret()));
        when(repository.save(any(SecuritySecret.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userAccountRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount account = new UserAccount();
        account.setId(2001L);
        account.setLoginName("admin");
        account.setTotpEnabled(Boolean.FALSE);
        account.setTotpSecret("broken-secret");
        when(userAccountRepository.findByTotpSecretIsNotNullAndDeletedFlagFalse()).thenReturn(List.of(account));
        when(cryptor.decrypt("broken-secret", "leo-dev-totp-key-change-me-20260425"))
                .thenThrow(new IllegalStateException("TOTP密钥解密失败"));

        SecurityKeyService service = new SecurityKeyService(
                repository,
                userAccountRepository,
                idGenerator,
                new JwtProperties("leo-erp", "leo-erp-jwt-secret-key-2026-must-be-long-enough-for-hs512", 1_800_000L, 604_800_000L),
                new TotpProperties("LeoERP", "leo-dev-totp-key-change-me-20260425"),
                cryptor
        );

        SecurityKeyRotateResponse response = service.rotateTotpMasterKey();

        verify(userAccountRepository).saveAll(List.of(account));
        assertThat(account.getTotpSecret()).isNull();
        assertThat(response.processedRecordCount()).isEqualTo(1);
    }

    @Test
    void shouldRejectRotationWhenEnabledTotpSecretCannotBeDecrypted() {
        SecuritySecretRepository repository = mock(SecuritySecretRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        TotpSecretCryptor cryptor = mock(TotpSecretCryptor.class);

        when(repository.findFirstBySecretTypeAndStatusAndDeletedFlagFalseOrderByKeyVersionDesc(
                SecurityKeyService.SECRET_TYPE_TOTP, "ACTIVE"))
                .thenReturn(Optional.empty());

        UserAccount account = new UserAccount();
        account.setId(2002L);
        account.setLoginName("locked-user");
        account.setTotpEnabled(Boolean.TRUE);
        account.setTotpSecret("broken-secret");
        when(userAccountRepository.findByTotpSecretIsNotNullAndDeletedFlagFalse()).thenReturn(List.of(account));
        when(cryptor.decrypt("broken-secret", "leo-dev-totp-key-change-me-20260425"))
                .thenThrow(new IllegalStateException("TOTP密钥解密失败"));

        SecurityKeyService service = new SecurityKeyService(
                repository,
                userAccountRepository,
                idGenerator,
                new JwtProperties("leo-erp", "leo-erp-jwt-secret-key-2026-must-be-long-enough-for-hs512", 1_800_000L, 604_800_000L),
                new TotpProperties("LeoERP", "leo-dev-totp-key-change-me-20260425"),
                cryptor
        );

        assertThatThrownBy(service::rotateTotpMasterKey)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("locked-user");
    }
}
