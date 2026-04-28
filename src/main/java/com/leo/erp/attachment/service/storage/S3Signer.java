package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Component
public class S3Signer {

    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final S3ChecksumUtil checksumUtil;
    private final S3PathParser pathParser;
    private final Clock clock;

    @Autowired
    public S3Signer(S3ChecksumUtil checksumUtil, S3PathParser pathParser) {
        this(checksumUtil, pathParser, Clock.systemUTC());
    }

    S3Signer(S3ChecksumUtil checksumUtil, S3PathParser pathParser, Clock clock) {
        this.checksumUtil = checksumUtil;
        this.pathParser = pathParser;
        this.clock = clock;
    }

    public HttpRequest signedRequest(String method,
                                     String objectKey,
                                     String payloadHash,
                                     String contentType,
                                     AttachmentProperties.S3 s3,
                                     HttpRequest.BodyPublisher bodyPublisher) {
        Instant now = clock.instant();
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        URI uri = pathParser.buildUri(objectKey, s3);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("host", pathParser.hostHeader(uri));
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
                checksumUtil.hexSha256(canonicalRequest);
        byte[] signingKey = signatureKey(s3.getSecretKey(), dateStamp, s3.getRegion(), "s3");
        String signature = checksumUtil.toHex(hmacSha256(signingKey, stringToSign));

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

    private Duration nonNullTimeout(Duration timeout) {
        return timeout == null ? Duration.ofSeconds(30) : timeout;
    }
}
