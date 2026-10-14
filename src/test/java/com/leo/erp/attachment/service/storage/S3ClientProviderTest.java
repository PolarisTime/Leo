package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S3 客户端凭证与指纹契约。
 *
 * <p>阿里云 OSS / AWS 的 STS 临时凭证必须同时携带 SecurityToken，否则服务端以
 * InvalidAccessKeyId 或 InvalidSecurityToken 拒绝；而 STS token 会轮换，指纹若不含 token，
 * 缓存下来的客户端会继续使用已失效凭证。这里锁定这两条不变量。</p>
 */
class S3ClientProviderTest {

    private final S3ClientProvider provider = new S3ClientProvider();

    private AttachmentProperties.S3 s3(String sessionToken) {
        AttachmentProperties.S3 s3 = new AttachmentProperties.S3();
        s3.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        s3.setRegion("cn-hangzhou");
        s3.setBucket("oss-test-tmp");
        s3.setAccessKey("STS.TESTKEY");
        s3.setSecretKey("test-secret");
        s3.setSessionToken(sessionToken);
        return s3;
    }

    @Test
    void credentials_useLongTermKeyWhenSessionTokenAbsent() {
        assertThat(provider.credentials(s3(null)).resolveCredentials())
                .isInstanceOf(AwsBasicCredentials.class);
    }

    @Test
    void credentials_useBlankTokenAsLongTermKey() {
        assertThat(provider.credentials(s3("   ")).resolveCredentials())
                .isInstanceOf(AwsBasicCredentials.class);
    }

    @Test
    void credentials_useSessionCredentialsWhenTokenPresent() {
        var resolved = provider.credentials(s3("sts-token-value")).resolveCredentials();

        assertThat(resolved).isInstanceOf(AwsSessionCredentials.class);
        assertThat(((AwsSessionCredentials) resolved).sessionToken()).isEqualTo("sts-token-value");
    }

    @Test
    void credentials_trimSurroundingWhitespaceOfToken() {
        // 环境变量或配置文件常带首尾空白, 直接透传会让服务端判定 token 无效
        var resolved = provider.credentials(s3("  sts-token-value\n")).resolveCredentials();

        assertThat(((AwsSessionCredentials) resolved).sessionToken()).isEqualTo("sts-token-value");
    }

    @Test
    void fingerprint_changesWhenSessionTokenRotates() {
        String before = provider.fingerprint(s3("token-a"));
        String after = provider.fingerprint(s3("token-b"));

        assertThat(before).isNotEqualTo(after);
    }

    @Test
    void fingerprint_changesWhenChunkedEncodingToggles() {
        AttachmentProperties.S3 enabled = s3(null);
        enabled.setChunkedEncodingEnabled(true);
        AttachmentProperties.S3 disabled = s3(null);
        disabled.setChunkedEncodingEnabled(false);

        assertThat(provider.fingerprint(enabled)).isNotEqualTo(provider.fingerprint(disabled));
    }

    @Test
    void defaults_keepExistingDeploymentBehaviour() {
        AttachmentProperties.S3 defaults = new AttachmentProperties.S3();

        // 未配置 STS 时行为不变; chunked encoding 默认沿用 SDK 行为;
        // 预签名下载默认不再覆盖 response-content-type(阿里云 OSS 会 400 拒绝该覆盖)
        assertThat(defaults.getSessionToken()).isNull();
        assertThat(defaults.isChunkedEncodingEnabled()).isTrue();
        assertThat(defaults.isOverrideResponseContentType()).isFalse();
    }
}
