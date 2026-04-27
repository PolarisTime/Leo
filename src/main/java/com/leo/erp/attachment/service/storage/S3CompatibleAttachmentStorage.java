package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Component
public class S3CompatibleAttachmentStorage implements AttachmentStorage {

    private static final String PREFIX = "s3:";
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final byte[] EMPTY_BYTES = new byte[0];

    private final AttachmentProperties properties;
    private final S3RequestExecutor requestExecutor;
    private final Clock clock;

    @Autowired
    public S3CompatibleAttachmentStorage(AttachmentProperties properties, S3RequestExecutor requestExecutor) {
        this(properties, requestExecutor, Clock.systemUTC());
    }

    S3CompatibleAttachmentStorage(AttachmentProperties properties, S3RequestExecutor requestExecutor, Clock clock) {
        this.properties = properties;
        this.requestExecutor = requestExecutor;
        this.clock = clock;
    }

    @Override
    public String type() {
        return "s3";
    }

    @Override
    public String store(String objectKey, MultipartFile file) throws IOException {
        AttachmentProperties.S3 s3 = requireS3Config();
        Path tempFile = Files.createTempFile("leo-s3-upload-", ".bin");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        String payloadHash;
        try (InputStream in = Files.newInputStream(tempFile)) {
            payloadHash = hexSha256(in);
        }
        HttpRequest request = signedRequest(
                "PUT",
                objectKey,
                payloadHash,
                file.getContentType(),
                s3,
                HttpRequest.BodyPublishers.ofFile(tempFile)
        );
        try {
            S3RequestExecutor.S3Response response = requestExecutor.execute(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("S3 上传失败: HTTP " + response.statusCode() + " " + new String(response.body(), StandardCharsets.UTF_8));
            }
            return PREFIX + s3.getBucket() + "/" + objectKey;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 上传被中断", ex);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Override
    public Resource load(String storagePath) throws IOException {
        ParsedStoragePath parsed = parseStoragePath(storagePath);
        AttachmentProperties.S3 s3 = requireS3Config();
        if (!parsed.bucket().equals(s3.getBucket())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "当前 S3 配置与附件桶不一致");
        }
        HttpRequest request = signedRequest(
                "GET",
                parsed.objectKey(),
                hexSha256(EMPTY_BYTES),
                null,
                s3,
                HttpRequest.BodyPublishers.noBody()
        );
        try {
            S3RequestExecutor.S3StreamResponse response = requestExecutor.executeForStream(request);
            if (response.statusCode() == 404) {
                response.close();
                throw new BusinessException(ErrorCode.NOT_FOUND, "附件文件不存在");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (response; InputStream body = response.body()) {
                    throw new IOException("S3 下载失败: HTTP " + response.statusCode() + " " + new String(body.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            return new InputStreamResource(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 下载被中断", ex);
        }
    }

    @Override
    public void delete(String storagePath) throws IOException {
        ParsedStoragePath parsed = parseStoragePath(storagePath);
        AttachmentProperties.S3 s3 = requireS3Config();
        if (!parsed.bucket().equals(s3.getBucket())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "当前 S3 配置与附件桶不一致");
        }
        HttpRequest request = signedRequest(
                "DELETE",
                parsed.objectKey(),
                hexSha256(EMPTY_BYTES),
                null,
                s3,
                HttpRequest.BodyPublishers.noBody()
        );
        try {
            S3RequestExecutor.S3Response response = requestExecutor.execute(request);
            if (response.statusCode() == 404) {
                return;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("S3 删除失败: HTTP " + response.statusCode() + " " + new String(response.body(), StandardCharsets.UTF_8));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 删除被中断", ex);
        }
    }

    private HttpRequest signedRequest(String method,
                                      String objectKey,
                                      String payloadHash,
                                      String contentType,
                                      AttachmentProperties.S3 s3,
                                      HttpRequest.BodyPublisher bodyPublisher) {
        Instant now = clock.instant();
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        URI uri = buildUri(objectKey, s3);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("host", hostHeader(uri));
        headers.put("x-amz-content-sha256", payloadHash);
        headers.put("x-amz-date", amzDate);
        if (contentType != null && !contentType.isBlank()) {
            headers.put("content-type", contentType);
        }

        String canonicalRequest = canonicalRequest(method, uri, headers, payloadHash);
        String credentialScope = dateStamp + "/" + s3.getRegion() + "/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" +
                amzDate + "\n" +
                credentialScope + "\n" +
                hexSha256(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] signingKey = signatureKey(s3.getSecretKey(), dateStamp, s3.getRegion(), "s3");
        String signature = toHex(hmacSha256(signingKey, stringToSign));

        String signedHeaders = String.join(";", new TreeMap<>(headers).keySet());
        String authorization = "AWS4-HMAC-SHA256 Credential=" + s3.getAccessKey() + "/" + credentialScope +
                ", SignedHeaders=" + signedHeaders +
                ", Signature=" + signature;

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(nonNullTimeout(s3.getReadTimeout()))
                .header("Authorization", authorization)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash);

        if (contentType != null && !contentType.isBlank()) {
            builder.header("Content-Type", contentType);
        }

        return switch (method) {
            case "PUT" -> builder.PUT(bodyPublisher).build();
            case "GET" -> builder.GET().build();
            case "DELETE" -> builder.DELETE().build();
            default -> throw new IllegalArgumentException("不支持的 S3 请求方法: " + method);
        };
    }

    private URI buildUri(String objectKey, AttachmentProperties.S3 s3) {
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

    private String canonicalRequest(String method, URI uri, Map<String, String> headers, String payloadHash) {
        Map<String, String> sortedHeaders = new TreeMap<>(headers);
        StringBuilder canonicalHeaders = new StringBuilder();
        sortedHeaders.forEach((name, value) -> canonicalHeaders.append(name).append(':').append(value.trim()).append('\n'));
        return method + "\n" +
                canonicalUri(uri.getRawPath()) + "\n" +
                "" + "\n" +
                canonicalHeaders +
                String.join(";", sortedHeaders.keySet()) + "\n" +
                payloadHash;
    }

    private String canonicalUri(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "/";
        }
        return rawPath;
    }

    private ParsedStoragePath parseStoragePath(String storagePath) {
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

    private AttachmentProperties.S3 requireS3Config() {
        AttachmentProperties.S3 s3 = properties.getStorage().getS3();
        if (isBlank(s3.getEndpoint()) || isBlank(s3.getBucket()) || isBlank(s3.getAccessKey()) || isBlank(s3.getSecretKey())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "S3 附件存储配置不完整");
        }
        return s3;
    }

    private URI parseEndpoint(String endpoint) {
        try {
            return URI.create(endpoint);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "S3 Endpoint 配置错误");
        }
    }

    private String hostHeader(URI uri) {
        int port = uri.getPort();
        if (port < 0) {
            return uri.getHost();
        }
        boolean defaultPort = ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443);
        return defaultPort ? uri.getHost() : uri.getHost() + ":" + port;
    }

    private String normalizedEndpointPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank() || "/".equals(rawPath)) {
            return "";
        }
        return rawPath.endsWith("/") ? rawPath.substring(0, rawPath.length() - 1) : rawPath;
    }

    private String encodeObjectKey(String objectKey) {
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

    private String urlEncodeSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private Duration nonNullTimeout(Duration timeout) {
        return timeout == null ? Duration.ofSeconds(30) : timeout;
    }

    private byte[] signatureKey(String secretKey, String dateStamp, String regionName, String serviceName) {
        byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, regionName);
        byte[] kService = hmacSha256(kRegion, serviceName);
        return hmacSha256(kService, "aws4_request");
    }

    private byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HmacSHA256 计算失败", ex);
        }
    }

    private String hexSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private String hexSha256(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private String toHex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte item : data) {
            builder.append(String.format(Locale.ROOT, "%02x", item));
        }
        return builder.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ParsedStoragePath(String bucket, String objectKey) {
    }
}
