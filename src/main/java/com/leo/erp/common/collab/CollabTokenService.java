package com.leo.erp.common.collab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 协作房间短票据（JWT HS256）的签发与自检。
 *
 * <p><b>为什么手写 JWS 而不是用 jjwt 的 builder</b>：jjwt 的 {@code signWith(HMAC)} 强制密钥
 * ≥ 256 bit（{@code WeakKeyException}），而边车开发态兜底密钥
 * {@code leo-collab-dev-insecure-secret} 只有 30 字节；两边必须用**同一份原始密钥字节**做
 * HMAC，才能在本地不配环境变量时也互通。这里用 JDK 自带的 {@code Mac} 直接拼 JWS 紧凑格式，
 * 与 {@code jose} 的 HS256 完全一致（header 只放 {@code alg/typ}，payload 为 JSON，
 * 签名为 {@code base64url(HMAC-SHA256(header + "." + payload))}，无 padding）。</p>
 *
 * <p><b>票据只授予一个房间</b>：claims 里是单数 {@code room}（边车两种形态都兼容，这里统一签发
 * 单房间形态），杜绝"拿一张票逛所有供应商"。</p>
 *
 * <p><b>强校验点在边车</b>：浏览器不可信，边车才是唯一强制校验者。本类的
 * {@link #verify(String, String)} 用于服务端自检（单测、以及将来 Java 侧直连/转发场景），
 * 规则与边车逐条对齐：签名 → HS256 → sub/exp 必填 → 过期（含 5 秒时钟漂移容忍）→ 房间匹配。</p>
 */
@Slf4j
@Service
public class CollabTokenService {

    /** JWS header 里的算法名；只接受这一种，防 {@code alg: none} 降级。 */
    static final String HEADER_ALGORITHM = "HS256";
    static final String HEADER_TYPE = "JWT";

    /** 与边车 jose 的 {@code clockTolerance: 5} 对齐：三台机器的时钟不可能完全同步。 */
    static final long CLOCK_SKEW_SECONDS = 5L;

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String CLAIM_SUBJECT = "sub";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROOM = "room";
    private static final String CLAIM_WRITABLE = "writable";
    private static final String CLAIM_COLOR = "color";
    private static final String CLAIM_EXPIRATION = "exp";
    private static final int JWS_PART_COUNT = 3;

    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final CollabSessionProperties properties;
    private final ObjectMapper objectMapper;

    public CollabTokenService(CollabSessionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 签发一个只授予 {@code room} 的短票据。
     *
     * @param userId    用户 id（雪花，转成十进制字符串放进 {@code sub}）
     * @param name      用户显示名
     * @param room      房间名，形如 {@code supplier-price-list:<listId>}
     * @param writable  是否允许写（无 update 权限时传 false）
     * @param color     光标颜色，形如 {@code #1677ff}
     * @param expiresAt 过期时间（写入 {@code exp}，秒级时间戳）
     * @return JWS 紧凑序列化的 JWT
     */
    public String issue(Long userId,
                        String name,
                        String room,
                        boolean writable,
                        String color,
                        Instant expiresAt) {
        if (userId == null) {
            throw new IllegalArgumentException("协作票据缺少用户 id");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("协作票据缺少过期时间");
        }

        ObjectNode header = objectMapper.createObjectNode();
        header.put("alg", HEADER_ALGORITHM);
        header.put("typ", HEADER_TYPE);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(CLAIM_SUBJECT, String.valueOf(userId));
        payload.put(CLAIM_NAME, name);
        payload.put(CLAIM_ROOM, room);
        payload.put(CLAIM_WRITABLE, writable);
        payload.put(CLAIM_COLOR, color);
        payload.put(CLAIM_EXPIRATION, expiresAt.getEpochSecond());

        String signingInput = encodeJson(header) + "." + encodeJson(payload);
        return signingInput + "." + encodeBytes(sign(signingInput));
    }

    /**
     * 校验票据并解出身份；规则与边车逐条对齐。
     *
     * @param token       浏览器带来的 JWT
     * @param expectedRoom 本次要进入的房间
     * @throws CollabTokenException 签名/结构/过期（{@link CollabTokenException.Reason#INVALID}
     *                              {@link CollabTokenException.Reason#EXPIRED}）或房间不匹配
     *                              （{@link CollabTokenException.Reason#ROOM_MISMATCH}）
     */
    public CollabTokenClaims verify(String token, String expectedRoom) {
        String rawToken = token == null ? "" : token.trim();
        if (rawToken.isEmpty()) {
            throw new CollabTokenException(CollabTokenException.Reason.INVALID, "缺少协作票据");
        }
        String secret = properties.getJwtSecret();
        if (secret == null || secret.isBlank()) {
            // 绝不拿空密钥验签：那样任何人都能伪造票据。
            throw new CollabTokenException(CollabTokenException.Reason.INVALID, "协作票据密钥未配置");
        }

        String[] parts = rawToken.split("\\.");
        if (parts.length != JWS_PART_COUNT) {
            throw new CollabTokenException(CollabTokenException.Reason.INVALID, "协作票据结构非法");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expectedSignature = sign(signingInput);
        byte[] actualSignature = decodeSignature(parts[2]);
        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            throw new CollabTokenException(CollabTokenException.Reason.INVALID, "协作票据签名校验失败");
        }

        if (!HEADER_ALGORITHM.equals(textOrNull(readJson(parts[0]).get("alg")))) {
            throw new CollabTokenException(CollabTokenException.Reason.INVALID, "协作票据仅支持 HS256");
        }

        JsonNode payload = readJson(parts[1]);
        String userId = textOrNull(payload.get(CLAIM_SUBJECT));
        if (userId == null) {
            throw new CollabTokenException(CollabTokenException.Reason.INVALID, "协作票据缺少用户标识（sub）");
        }
        JsonNode expirationNode = payload.get(CLAIM_EXPIRATION);
        if (expirationNode == null || !expirationNode.isNumber()) {
            throw new CollabTokenException(CollabTokenException.Reason.INVALID, "协作票据缺少过期时间（exp）");
        }
        long expiration = expirationNode.asLong();
        if (Instant.now().getEpochSecond() > expiration + CLOCK_SKEW_SECONDS) {
            throw new CollabTokenException(CollabTokenException.Reason.EXPIRED, "协作票据已过期");
        }

        String room = textOrNull(payload.get(CLAIM_ROOM));
        if (room == null || !room.equals(expectedRoom)) {
            throw new CollabTokenException(CollabTokenException.Reason.ROOM_MISMATCH,
                    "协作票据不包含房间 " + expectedRoom);
        }

        return new CollabTokenClaims(
                userId,
                textOrNull(payload.get(CLAIM_NAME)),
                room,
                payload.path(CLAIM_WRITABLE).asBoolean(false),
                textOrNull(payload.get(CLAIM_COLOR)),
                Instant.ofEpochSecond(expiration));
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("计算协作票据签名失败", ex);
        }
    }

    private byte[] decodeSignature(String encoded) {
        try {
            return BASE64_URL_DECODER.decode(encoded);
        } catch (IllegalArgumentException ex) {
            throw new CollabTokenException(CollabTokenException.Reason.INVALID, "协作票据签名编码非法", ex);
        }
    }

    private String encodeJson(JsonNode node) {
        try {
            return encodeBytes(objectMapper.writeValueAsBytes(node));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化协作票据失败", ex);
        }
    }

    private String encodeBytes(byte[] bytes) {
        return BASE64_URL_ENCODER.encodeToString(bytes);
    }

    private JsonNode readJson(String encoded) {
        try {
            return objectMapper.readTree(BASE64_URL_DECODER.decode(encoded));
        } catch (IllegalArgumentException | IOException ex) {
            throw new CollabTokenException(CollabTokenException.Reason.INVALID, "协作票据内容非法", ex);
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
