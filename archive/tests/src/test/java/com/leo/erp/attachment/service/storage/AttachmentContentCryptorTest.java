package com.leo.erp.attachment.service.storage;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.system.securitykey.service.SecurityKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttachmentContentCryptorTest {

    @Test
    void shouldEncryptAndDecryptContent() {
        AttachmentContentCryptor cryptor = new AttachmentContentCryptor(securityKeyService("attachment-master-key"));
        byte[] plainContent = "hello attachment".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = cryptor.encrypt(plainContent);
        byte[] decrypted = cryptor.decrypt(encrypted);

        assertThat(encrypted).isNotEqualTo(plainContent);
        assertThat(new String(encrypted, 0, "LEOENC1".length(), StandardCharsets.US_ASCII)).isEqualTo("LEOENC1");
        assertThat(decrypted).isEqualTo(plainContent);
    }

    @Test
    void shouldRejectEncryptedContentWithoutMagicHeader() {
        AttachmentContentCryptor cryptor = new AttachmentContentCryptor(securityKeyService("attachment-master-key"));

        assertThatThrownBy(() -> cryptor.decrypt("plain-content-long-enough".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件加密内容格式错误");
    }

    @Test
    void shouldRejectTooShortEncryptedContent() {
        AttachmentContentCryptor cryptor = new AttachmentContentCryptor(securityKeyService("attachment-master-key"));

        assertThatThrownBy(() -> cryptor.decrypt("LEOENC1".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件加密内容格式错误");
    }

    @Test
    void shouldRejectTamperedEncryptedContent() {
        AttachmentContentCryptor cryptor = new AttachmentContentCryptor(securityKeyService("attachment-master-key"));
        byte[] encrypted = cryptor.encrypt("hello attachment".getBytes(StandardCharsets.UTF_8));
        encrypted[encrypted.length - 1] ^= 1;

        assertThatThrownBy(() -> cryptor.decrypt(encrypted))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件解密失败");
    }

    @Test
    void shouldRejectBlankMasterKeyWhenEncrypting() {
        AttachmentContentCryptor cryptor = new AttachmentContentCryptor(securityKeyService(" "));

        assertThatThrownBy(() -> cryptor.encrypt("hello".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件加密失败");
    }

    @Test
    void shouldRejectNullMasterKeyWhenEncrypting() {
        AttachmentContentCryptor cryptor = new AttachmentContentCryptor(securityKeyService(null));

        assertThatThrownBy(() -> cryptor.encrypt("hello".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件加密失败");
    }

    @Test
    void shouldDetectMagicHeaderBoundaries() {
        AttachmentContentCryptor cryptor = new AttachmentContentCryptor(securityKeyService("attachment-master-key"));

        Boolean tooShort = ReflectionTestUtils.invokeMethod(cryptor, "hasMagic", "LEO".getBytes(StandardCharsets.US_ASCII));
        Boolean wrongHeader = ReflectionTestUtils.invokeMethod(cryptor, "hasMagic", "LEOENC0".getBytes(StandardCharsets.US_ASCII));

        assertThat(tooShort).isFalse();
        assertThat(wrongHeader).isFalse();
    }

    @Test
    void shouldWrapMasterKeyDigestInitializationFailure() {
        AttachmentContentCryptor cryptor = new AttachmentContentCryptor(securityKeyService("attachment-master-key"));

        try (var messageDigest = mockStatic(MessageDigest.class, CALLS_REAL_METHODS)) {
            messageDigest.when(() -> MessageDigest.getInstance(eq("SHA-256")))
                    .thenThrow(new NoSuchAlgorithmException("missing"));

            assertThatThrownBy(() -> cryptor.encrypt("hello".getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("附件加密失败");
        }
    }

    @Test
    void shouldReadAllBytesFromInputStream() throws Exception {
        AttachmentContentCryptor cryptor = new AttachmentContentCryptor(securityKeyService("attachment-master-key"));
        byte[] content = "stream-content".getBytes(StandardCharsets.UTF_8);

        byte[] result = cryptor.readAll(new ByteArrayInputStream(content));

        assertThat(result).isEqualTo(content);
    }

    @Test
    void shouldPropagateReadFailure() {
        AttachmentContentCryptor cryptor = new AttachmentContentCryptor(securityKeyService("attachment-master-key"));
        InputStream broken = new InputStream() {
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                throw new IOException("read failed");
            }

            @Override
            public int read() throws IOException {
                throw new IOException("read failed");
            }
        };

        assertThatThrownBy(() -> cryptor.readAll(broken))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("read failed");
    }

    private SecurityKeyService securityKeyService(String secretValue) {
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        when(securityKeyService.getActiveTotpMaterial()).thenReturn(new SecurityKeyService.ResolvedSecretMaterial(
                "TEST",
                1,
                secretValue,
                null,
                null,
                "fingerprint"
        ));
        return securityKeyService;
    }
}
