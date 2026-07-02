package com.leo.erp.system.oss.web.dto;

public record OssSettingResponse(
        String storageMode,
        String provider,
        String endpoint,
        String bucket,
        String region,
        String accessKey,
        boolean secretKeyConfigured,
        String keyPrefix,
        boolean pathStyleAccess,
        boolean encryptedStorage,
        boolean serverProxyOnly
) {
}
