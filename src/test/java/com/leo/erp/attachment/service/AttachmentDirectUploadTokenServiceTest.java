package com.leo.erp.attachment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentDirectUploadTokenServiceTest {

    @Test
    void shouldIssueAndVerifyDirectUploadToken() {
        AttachmentDirectUploadTokenService service = new AttachmentDirectUploadTokenService("secret");
        AttachmentDirectUploadTokenService.DirectUploadTokenPayload payload = payload(
                Instant.now().plusSeconds(60).getEpochSecond()
        );

        String token = service.issue(payload);
        AttachmentDirectUploadTokenService.DirectUploadTokenPayload verified =
                service.verify(token, 1L, " sales-order ", 42L);

        assertThat(verified).isEqualTo(payload);
    }

    @Test
    void shouldIssueAndVerifyTokenWithJwtPropertiesSecret() {
        AttachmentDirectUploadTokenService service = new AttachmentDirectUploadTokenService(
                new JwtProperties("leo", "jwt-secret", 60_000L, 60_000L)
        );
        AttachmentDirectUploadTokenService.DirectUploadTokenPayload payload = payload(
                Instant.now().plusSeconds(60).getEpochSecond()
        );

        String token = service.issue(payload);

        assertThat(service.verify(token, 1L, "sales-order", 42L)).isEqualTo(payload);
    }

    @Test
    void shouldSerializeNullPayloadFieldsAsBlankValues() {
        AttachmentDirectUploadTokenService service = new AttachmentDirectUploadTokenService("secret");
        AttachmentDirectUploadTokenService.DirectUploadTokenPayload payload =
                new AttachmentDirectUploadTokenService.DirectUploadTokenPayload(
                        1L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        128L,
                        null,
                        null,
                        42L,
                        null,
                        Instant.now().plusSeconds(60).getEpochSecond()
                );

        AttachmentDirectUploadTokenService.DirectUploadTokenPayload verified =
                service.verify(service.issue(payload), 1L, null, 42L);

        assertThat(verified.objectKey()).isEmpty();
        assertThat(verified.storagePath()).isEmpty();
        assertThat(verified.moduleKey()).isEmpty();
        assertThat(verified.sha256Hex()).isEmpty();
    }

    @Test
    void shouldRejectBlankToken() {
        AttachmentDirectUploadTokenService service = new AttachmentDirectUploadTokenService("secret");

        assertThatThrownBy(() -> service.verify(" ", 1L, "sales-order", 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传凭证无效");
    }

    @Test
    void shouldRejectNullToken() {
        AttachmentDirectUploadTokenService service = new AttachmentDirectUploadTokenService("secret");

        assertThatThrownBy(() -> service.verify(null, 1L, "sales-order", 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传凭证无效");
    }

    @Test
    void shouldRejectTamperedTokenSignature() {
        AttachmentDirectUploadTokenService service = new AttachmentDirectUploadTokenService("secret");
        String token = service.issue(payload(Instant.now().plusSeconds(60).getEpochSecond()));

        assertThatThrownBy(() -> service.verify(token + "x", 1L, "sales-order", 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传凭证无效");
    }

    @Test
    void shouldRejectTokenWhenAttachmentOrModuleDoesNotMatch() {
        AttachmentDirectUploadTokenService service = new AttachmentDirectUploadTokenService("secret");
        String token = service.issue(payload(Instant.now().plusSeconds(60).getEpochSecond()));

        assertThatThrownBy(() -> service.verify(token, 2L, "sales-order", 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传凭证无效");
        assertThatThrownBy(() -> service.verify(token, 1L, "purchase-order", 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传凭证无效");
    }

    @Test
    void shouldRejectTokenWithBlankParts() {
        AttachmentDirectUploadTokenService service = new AttachmentDirectUploadTokenService("secret");

        assertThatThrownBy(() -> service.verify(".signature", 1L, "sales-order", 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传凭证无效");
        assertThatThrownBy(() -> service.verify("payload.", 1L, "sales-order", 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传凭证无效");
    }

    @Test
    void shouldRejectExpiredToken() {
        AttachmentDirectUploadTokenService service = new AttachmentDirectUploadTokenService("secret");
        String token = service.issue(payload(Instant.now().minusSeconds(60).getEpochSecond()));

        assertThatThrownBy(() -> service.verify(token, 1L, "sales-order", 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传凭证已过期");
    }

    @Test
    void shouldRejectMalformedSignedPayload() throws Exception {
        AttachmentDirectUploadTokenService service = new AttachmentDirectUploadTokenService("secret");
        String encodedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("bad-payload".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.verify(
                encodedPayload + "." + signature(service, encodedPayload),
                1L,
                "sales-order",
                42L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传凭证无效");
    }

    @Test
    void shouldRejectMissingSigningSecret() {
        AttachmentDirectUploadTokenService service = new AttachmentDirectUploadTokenService(" ");

        assertThatThrownBy(() -> service.issue(payload(Instant.now().plusSeconds(60).getEpochSecond())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传凭证签名失败");
    }

    @Test
    void shouldRejectMissingJwtSigningSecret() {
        AttachmentDirectUploadTokenService service = new AttachmentDirectUploadTokenService(
                new JwtProperties("leo", null, 60_000L, 60_000L)
        );

        assertThatThrownBy(() -> service.issue(payload(Instant.now().plusSeconds(60).getEpochSecond())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传凭证签名失败");
    }

    @Test
    void shouldRejectMissingJwtProperties() {
        AttachmentDirectUploadTokenService service = new AttachmentDirectUploadTokenService((JwtProperties) null);

        assertThatThrownBy(() -> service.issue(payload(Instant.now().plusSeconds(60).getEpochSecond())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直传凭证签名失败");
    }

    private String signature(AttachmentDirectUploadTokenService service, String encodedPayload) throws Exception {
        Method method = AttachmentDirectUploadTokenService.class.getDeclaredMethod("signature", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, encodedPayload);
    }

    private AttachmentDirectUploadTokenService.DirectUploadTokenPayload payload(long expiresAtEpochSecond) {
        return new AttachmentDirectUploadTokenService.DirectUploadTokenPayload(
                1L,
                "attachments/1",
                "s3:bucket/attachments/1",
                "stored.pdf",
                "original.pdf",
                "application/pdf",
                128L,
                "DIRECT",
                "sales-order",
                42L,
                "sha256",
                expiresAtEpochSecond
        );
    }
}
