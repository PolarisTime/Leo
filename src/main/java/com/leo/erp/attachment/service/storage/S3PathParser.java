package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class S3PathParser {

    private static final String PREFIX = "s3:";

    public record ParsedStoragePath(String bucket, String objectKey) {}

    public ParsedStoragePath parseStoragePath(String storagePath) {
        if (storagePath == null || !storagePath.startsWith(PREFIX)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "S3 附件路径格式错误");
        }
        String value = storagePath.substring(PREFIX.length());
        int slashIndex = value.indexOf('/');
        if (slashIndex <= 0 || slashIndex == value.length() - 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "S3 附件路径格式错误");
        }
        return new ParsedStoragePath(value.substring(0, slashIndex), value.substring(slashIndex + 1));
    }

    public String buildStoragePath(String bucket, String objectKey) {
        return PREFIX + bucket + "/" + objectKey;
    }

    public URI buildUri(String objectKey, AttachmentProperties.S3 s3) {
        URI endpoint = parseEndpoint(s3.getEndpoint());
        boolean pathStyle = s3.isPathStyleAccess();
        String encodedKey = encodeObjectKey(objectKey);
        String endpointPath = normalizedEndpointPath(endpoint.getPath());
        String path = pathStyle
                ? endpointPath + "/" + urlEncodeSegment(s3.getBucket()) + "/" + encodedKey
                : endpointPath + "/" + encodedKey;
        String host = pathStyle ? endpoint.getHost() : s3.getBucket() + "." + endpoint.getHost();
        try {
            return new URI(endpoint.getScheme(), endpoint.getUserInfo(), host, endpoint.getPort(), path, null, null);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "S3 Endpoint 配置错误");
        }
    }

    public String encodeObjectKey(String objectKey) {
        String[] segments = objectKey.split("/");
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                encoded.append('/');
            }
            encoded.append(urlEncodeSegment(segments[i]));
        }
        return encoded.toString();
    }

    public String urlEncodeSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public String hostHeader(URI uri) {
        int port = uri.getPort();
        if (port < 0) {
            return uri.getHost();
        }
        boolean defaultPort = ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443);
        return defaultPort ? uri.getHost() : uri.getHost() + ":" + port;
    }

    public String normalizedEndpointPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank() || "/".equals(rawPath)) {
            return "";
        }
        return rawPath.endsWith("/") ? rawPath.substring(0, rawPath.length() - 1) : rawPath;
    }

    public URI parseEndpoint(String endpoint) {
        try {
            return URI.create(endpoint);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "S3 Endpoint 配置错误");
        }
    }

    public boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
