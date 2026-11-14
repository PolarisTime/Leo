package com.leo.erp.market.supplierprice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.market.supplierprice.CollabSessionProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * 协作票据的签发/自检契约。
 *
 * <p>锁三件事：</p>
 * <ol>
 *   <li><b>互操作</b>：签出来的是标准 JWS 紧凑格式（HS256），claims 与边车
 *       {@code auth.ts} 读取的字段一一对应；使用边车开发态兜底密钥（30 字节，短于 HS256
 *       建议的 32 字节）也能签/验 —— 这正是不能用 jjwt builder 的原因；</li>
 *   <li><b>安全边界</b>：过期、房间不匹配、换密钥签名、缺 sub、alg 被篡改都必须拒绝，
 *       且失败原因与边车 4401/4403 的语义对齐；</li>
 *   <li><b>只读票据</b>：{@code writable=false} 必须原样保留，不能被解成 true。</li>
 * </ol>
 */
class CollabTokenServiceTest {

    private static final long USER_ID = 900000000000000123L;
    private static final String ROOM = "supplier-price-list:900000000000000201";
    private static final String OTHER_ROOM = "supplier-price-list:900000000000000202";
    private static final String COLOR = "#1677ff";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void issueAndVerify_shouldCarryEveryClaim() {
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);

        String token = service().issue(USER_ID, "张三", ROOM, true, COLOR, expiresAt);
        CollabTokenClaims claims = service().verify(token, ROOM);

        assertThat(claims.userId()).isEqualTo(String.valueOf(USER_ID));
        assertThat(claims.name()).isEqualTo("张三");
        assertThat(claims.room()).isEqualTo(ROOM);
        assertThat(claims.writable()).isTrue();
        assertThat(claims.color()).isEqualTo(COLOR);
        assertThat(claims.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void issuedToken_shouldBeStandardJwsCompactWithHs256Header() throws Exception {
        String token = service().issue(USER_ID, "张三", ROOM, true, COLOR,
                Instant.now().plus(10, ChronoUnit.MINUTES));

        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        JsonNode header = OBJECT_MAPPER.readTree(
                Base64.getUrlDecoder().decode(parts[0]));
        assertThat(header.get("alg").asText()).isEqualTo("HS256");
        assertThat(header.get("typ").asText()).isEqualTo("JWT");

        JsonNode payload = OBJECT_MAPPER.readTree(
                Base64.getUrlDecoder().decode(parts[1]));
        // sub 必须是字符串：雪花 ID 用数字会丢精度，边车也是按字符串读的。
        assertThat(payload.get("sub").isTextual()).isTrue();
        assertThat(payload.get("sub").asText()).isEqualTo(String.valueOf(USER_ID));
        assertThat(payload.get("exp").isNumber()).isTrue();
    }

    @Test
    void issue_shouldMatchKnownGoodHs256Vector() {
        // 该向量由独立实现（python hmac/hashlib）按边车 jose 的 HS256 规则算出：
        // header {"alg":"HS256","typ":"JWT"} + 下列 claims + 开发态兜底密钥。
        // 锁住它等于锁住与边车的互操作 —— claims 字段名/顺序、exp 为数字、中文按 UTF-8 原样、
        // base64url 无 padding，任何一处漂移都会让边车按 4401 拒连。
        String token = service().issue(USER_ID, "张三", "supplier-price-list:900000000000000201",
                true, COLOR, Instant.ofEpochSecond(1_763_100_000L));

        assertThat(token).isEqualTo(
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                        + ".eyJzdWIiOiI5MDAwMDAwMDAwMDAwMDAxMjMiLCJuYW1lIjoi5byg5LiJIiwicm9vbSI6"
                        + "InN1cHBsaWVyLXByaWNlLWxpc3Q6OTAwMDAwMDAwMDAwMDAwMjAxIiwid3JpdGFibGUi"
                        + "OnRydWUsImNvbG9yIjoiIzE2NzdmZiIsImV4cCI6MTc2MzEwMDAwMH0"
                        + ".jJubt1j3oWRU91e1E5m-vVVqc-EG6cU4Ud1aRd2LgG8");
    }

    @Test
    void verify_expiredToken_shouldThrowExpired() {
        String token = service().issue(USER_ID, "张三", ROOM, true, COLOR,
                Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> service().verify(token, ROOM))
                .isInstanceOfSatisfying(CollabTokenException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(CollabTokenException.Reason.EXPIRED));
    }

    @Test
    void verify_tokenIssuedForAnotherRoom_shouldThrowRoomMismatch() {
        String token = service().issue(USER_ID, "张三", ROOM, true, COLOR,
                Instant.now().plus(10, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> service().verify(token, OTHER_ROOM))
                .isInstanceOfSatisfying(CollabTokenException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(CollabTokenException.Reason.ROOM_MISMATCH));
    }

    @Test
    void verify_tokenSignedWithAnotherSecret_shouldThrowInvalid() {
        CollabTokenService attacker = new CollabTokenService(properties("attacker-secret"), OBJECT_MAPPER);
        String token = attacker.issue(USER_ID, "张三", ROOM, true, COLOR,
                Instant.now().plus(10, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> service().verify(token, ROOM))
                .isInstanceOfSatisfying(CollabTokenException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(CollabTokenException.Reason.INVALID));
    }

    @Test
    void verify_malformedToken_shouldThrowInvalid() {
        assertThatThrownBy(() -> service().verify("not-a-jwt", ROOM))
                .isInstanceOfSatisfying(CollabTokenException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(CollabTokenException.Reason.INVALID));
        assertThatThrownBy(() -> service().verify("  ", ROOM))
                .isInstanceOfSatisfying(CollabTokenException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(CollabTokenException.Reason.INVALID));
    }

    @Test
    void verify_readOnlyToken_shouldKeepWritableFalse() {
        String token = service().issue(USER_ID, "张三", ROOM, false, COLOR,
                Instant.now().plus(10, ChronoUnit.MINUTES));

        assertThat(service().verify(token, ROOM).writable()).isFalse();
    }

    @Test
    void verify_tokenWithoutSubject_shouldThrowInvalid() throws Exception {
        String token = signRaw("""
                {"alg":"HS256","typ":"JWT"}
                """, """
                {"name":"张三","room":"%s","writable":true,"color":"%s","exp":%d}
                """.formatted(ROOM, COLOR, Instant.now().plusSeconds(60).getEpochSecond()));

        assertThatThrownBy(() -> service().verify(token, ROOM))
                .isInstanceOfSatisfying(CollabTokenException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(CollabTokenException.Reason.INVALID));
    }

    @Test
    void verify_tokenWithoutExpiration_shouldThrowInvalid() throws Exception {
        String token = signRaw("""
                {"alg":"HS256","typ":"JWT"}
                """, """
                {"sub":"%d","name":"张三","room":"%s","writable":true,"color":"%s"}
                """.formatted(USER_ID, ROOM, COLOR));

        assertThatThrownBy(() -> service().verify(token, ROOM))
                .isInstanceOfSatisfying(CollabTokenException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(CollabTokenException.Reason.INVALID));
    }

    @Test
    void verify_tokenWithAlgNoneHeader_shouldThrowInvalid() throws Exception {
        // 签名用正确密钥（能过签名校验），但 header 声称 alg=none —— 必须被拒，防算法降级。
        String token = signRaw("""
                {"alg":"none","typ":"JWT"}
                """, """
                {"sub":"%d","name":"张三","room":"%s","writable":false,"color":"%s","exp":%d}
                """.formatted(USER_ID, ROOM, COLOR, Instant.now().plusSeconds(60).getEpochSecond()));

        assertThatThrownBy(() -> service().verify(token, ROOM))
                .isInstanceOfSatisfying(CollabTokenException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(CollabTokenException.Reason.INVALID));
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private CollabTokenService service() {
        return new CollabTokenService(properties(CollabSessionProperties.DEV_INSECURE_JWT_SECRET),
                OBJECT_MAPPER);
    }

    private CollabSessionProperties properties(String secret) {
        CollabSessionProperties properties = new CollabSessionProperties();
        properties.setJwtSecret(secret);
        return properties;
    }

    /** 用与生产相同的 HMAC 手工拼一个 JWS，用来构造「签名合法但 claims 非法」的负例。 */
    private String signRaw(String headerJson, String payloadJson) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String signingInput = encoder.encodeToString(
                headerJson.trim().getBytes(StandardCharsets.UTF_8))
                + "."
                + encoder.encodeToString(payloadJson.trim().getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                CollabSessionProperties.DEV_INSECURE_JWT_SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        return signingInput + "."
                + encoder.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
    }
}
