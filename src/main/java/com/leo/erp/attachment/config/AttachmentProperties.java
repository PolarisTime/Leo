package com.leo.erp.attachment.config;

import com.leo.erp.attachment.support.AttachmentStorageTypes;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "leo.attachment")
public class AttachmentProperties {

    private DataSize maxFileSize = DataSize.ofMegabytes(20);

    private Storage storage = new Storage();

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public static class Storage {

        private String type = AttachmentStorageTypes.LOCAL;

        private String keyPrefix = "attachments";

        private Local local = new Local();

        private S3 s3 = new S3();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public Local getLocal() {
            return local;
        }

        public void setLocal(Local local) {
            this.local = local;
        }

        public S3 getS3() {
            return s3;
        }

        public void setS3(S3 s3) {
            this.s3 = s3;
        }
    }

    public static class Local {

        private String path = "/tmp/leo/uploads";

        /** 本地存储是否使用系统数据密钥加密附件内容。 */
        private boolean encryptedStorage = false;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isEncryptedStorage() {
            return encryptedStorage;
        }

        public void setEncryptedStorage(boolean encryptedStorage) {
            this.encryptedStorage = encryptedStorage;
        }
    }

    public static class S3 {

        private String endpoint;

        private String region = "us-east-1";

        private String bucket;

        private String accessKey;

        private String secretKey;

        /** STS 临时凭证的 SecurityToken; 为空表示使用长期密钥。 */
        private String sessionToken;

        private boolean pathStyleAccess = false;

        /**
         * 是否启用 AWS SDK 的 aws-chunked 传输编码与 trailer checksum。
         * 阿里云 OSS 等部分 S3 兼容服务不支持, 会以 400 STREAMING-UNSIGNED-PAYLOAD-TRAILER 拒绝写入。
         */
        private boolean chunkedEncodingEnabled = true;

        /**
         * 预签名下载是否覆盖 response-content-type。
         * 阿里云 OSS 拒绝该覆盖(400 Can not override response header on content-type);
         * 上传时已写入 content-type, 故默认不覆盖。
         */
        private boolean overrideResponseContentType = false;

        private boolean encryptedStorage = false;

        private boolean serverProxyOnly = true;

        private Duration connectTimeout = Duration.ofSeconds(10);

        private Duration readTimeout = Duration.ofSeconds(30);

        private Duration presignUploadTtl = Duration.ofMinutes(10);

        private Duration presignPreviewTtl = Duration.ofMinutes(5);

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getSessionToken() {
            return sessionToken;
        }

        public void setSessionToken(String sessionToken) {
            this.sessionToken = sessionToken;
        }

        public boolean isChunkedEncodingEnabled() {
            return chunkedEncodingEnabled;
        }

        public void setChunkedEncodingEnabled(boolean chunkedEncodingEnabled) {
            this.chunkedEncodingEnabled = chunkedEncodingEnabled;
        }

        public boolean isOverrideResponseContentType() {
            return overrideResponseContentType;
        }

        public void setOverrideResponseContentType(boolean overrideResponseContentType) {
            this.overrideResponseContentType = overrideResponseContentType;
        }

        public boolean isPathStyleAccess() {
            return pathStyleAccess;
        }

        public void setPathStyleAccess(boolean pathStyleAccess) {
            this.pathStyleAccess = pathStyleAccess;
        }

        public boolean isEncryptedStorage() {
            return encryptedStorage;
        }

        public void setEncryptedStorage(boolean encryptedStorage) {
            this.encryptedStorage = encryptedStorage;
        }

        public boolean isServerProxyOnly() {
            return serverProxyOnly;
        }

        public void setServerProxyOnly(boolean serverProxyOnly) {
            this.serverProxyOnly = serverProxyOnly;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public Duration getPresignUploadTtl() {
            return presignUploadTtl;
        }

        public void setPresignUploadTtl(Duration presignUploadTtl) {
            this.presignUploadTtl = presignUploadTtl;
        }

        public Duration getPresignPreviewTtl() {
            return presignPreviewTtl;
        }

        public void setPresignPreviewTtl(Duration presignPreviewTtl) {
            this.presignPreviewTtl = presignPreviewTtl;
        }
    }
}
