package com.leo.erp.market.supplierprice;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 供应商价格表实时协作（Node 边车 {@code leo/collab-server}）的票据配置。
 *
 * <p>三个值都必须与边车对齐，否则浏览器拿到的票据连不上：</p>
 * <ul>
 *   <li>{@code url}：下发给前端的 WebSocket 接入地址，边车默认监听 {@code 11212}；</li>
 *   <li>{@code jwt-secret}：HS256 共享密钥，边车用同一个值验签（环境变量同为
 *       {@code COLLAB_JWT_SECRET}）；</li>
 *   <li>{@code token-ttl-seconds}：短票据有效期，过期后前端重新 POST 取票即可，不需要续期接口。</li>
 * </ul>
 *
 * <p><b>开发态兜底密钥</b>与边车 {@code DEV_INSECURE_JWT_SECRET} 取同一个字面量：本地两个人
 * clone 下来不配任何环境变量也能联调通。生产必须显式配置 {@code COLLAB_JWT_SECRET}，
 * 启动时会打 warn 提醒（不 fail-fast：单机开发/演示环境仍需可启动）。</p>
 */
@Slf4j
@Validated
@ConfigurationProperties(prefix = "leo.collab")
public class CollabSessionProperties {

    /**
     * 开发态固定密钥，必须与边车 {@code collab-server/src/config.ts} 的
     * {@code DEV_INSECURE_JWT_SECRET} 完全一致，否则开发环境下 Java 签的票据边车验不过。
     */
    public static final String DEV_INSECURE_JWT_SECRET = "leo-collab-dev-insecure-secret";

    /** 边车 WebSocket 接入地址（浏览器直连或经 nginx 反代）。 */
    private String url = "ws://127.0.0.1:11212/collab";

    /** HS256 共享密钥；改它就等于让所有在线票据失效。 */
    @NotBlank
    private String jwtSecret = DEV_INSECURE_JWT_SECRET;

    /** 短票据有效期（秒）。默认 600 秒 = 10 分钟。 */
    @Positive
    private long tokenTtlSeconds = 600;

    /** 用开发态默认密钥启动时告警，避免「以为配了生产密钥」的静默事故。 */
    @PostConstruct
    void warnOnInsecureDefaultSecret() {
        if (DEV_INSECURE_JWT_SECRET.equals(jwtSecret)) {
            log.warn("leo.collab.jwt-secret 使用开发态固定密钥，生产环境必须配置 COLLAB_JWT_SECRET "
                    + "并保证与协作边车一致");
        }
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(long tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }
}
