package com.leo.erp.system.oss.web.dto;

import jakarta.validation.constraints.Size;

public record OssSettingRequest(
        @Size(max = 32) String storageMode,
        @Size(max = 32) String provider,
        @Size(max = 255) String endpoint,
        @Size(max = 128) String bucket,
        @Size(max = 64) String region,
        @Size(max = 255) String accessKey,
        @Size(max = 1024) String secretKey,
        @Size(max = 255) String keyPrefix,
        Boolean pathStyleAccess,
        Boolean encryptedStorage,
        Boolean serverProxyOnly
) {
}
