package com.leo.erp.common.config;

import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * CORS 配置的生产环境启动校验 —— **唯一来源**。
 *
 * <p>背景：{@code application.yml} 为 {@code leo.security.web.cors.allowed-origins} 留了
 * {@code http://localhost:3100,http://127.0.0.1:3100} 的开发默认值，而
 * {@code application-prod.yml} 此前完全没有覆盖 CORS。生产实例若忘记设置
 * {@code LEO_CORS_ALLOWED_ORIGINS}，就会带着「localhost 白名单 + allow-credentials=true」
 * 静默启动：当前因来源限 localhost 不可利用，但这是「只要忘记就会静默降级」的隐患，
 * 且没有任何日志或告警提示。</p>
 *
 * <p>本类把该隐患变成**启动期失败**：prod profile 下来源缺失/为空/仍是本机地址/形态不合法时
 * 直接抛出 {@link IllegalStateException}，错误信息点名要设置的环境变量并给出示例。</p>
 *
 * <p>为什么用代码校验而不是只写 {@code ${LEO_CORS_ALLOWED_ORIGINS}}（无默认值占位）：那样
 * 确实也会启动失败，但报错是 Spring 的占位符解析异常，既没说清「这是 CORS 的生产必填项」，
 * 也接不住三类**同样静默**的误配：{@code LEO_CORS_ALLOWED_ORIGINS=}（空值）、
 * 把 dev 的 localhost 值原样带进生产、以及带路径/末尾斜杠（origin 永不匹配，前端只会看到
 * 一堆无 CORS 头的 403）。两层都做：yaml 占位符兜住「变量不存在」，本校验兜住其余三类。</p>
 *
 * <p>开发/测试不受影响：只有 {@code prod} profile 生效，dev 保持零配置可启动。</p>
 */
public final class ProdCorsStartupValidator {

    /** 缺失时必须设置的环境变量名（错误信息里逐字出现，方便运维直接复制）。 */
    public static final String ALLOWED_ORIGINS_ENV = "LEO_CORS_ALLOWED_ORIGINS";

    /** allow-credentials 的 env 名（仅在非法组合的报错里引用）。 */
    public static final String ALLOW_CREDENTIALS_ENV = "LEO_CORS_ALLOW_CREDENTIALS";

    private static final String PROD_PROFILE = "prod";

    /** 合法 origin 形态：scheme + authority，**不带路径、不带末尾斜杠、不带查询串**。 */
    private static final Pattern ORIGIN_SHAPE = Pattern.compile("^https?://[^/\\s]+$");

    private static final String WILDCARD_ORIGIN = "*";

    private ProdCorsStartupValidator() {
    }

    /** 是否处于 prod profile（支持 {@code SPRING_PROFILES_ACTIVE=prod,xxx} 这类组合）。 */
    public static boolean isProdProfile(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if (PROD_PROFILE.equalsIgnoreCase(profile.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验 prod 下的 CORS 来源配置；不合法直接抛出，让应用启动失败。
     *
     * <p>非 prod 直接返回：开发环境零配置必须能启动。</p>
     *
     * @param environment           Spring 环境（用于判断 profile）
     * @param allowedOrigins        {@code leo.security.web.cors.allowed-origins} 的绑定结果，可为 null
     * @param allowCredentials      {@code leo.security.web.cors.allow-credentials} 的绑定结果
     */
    public static void validate(Environment environment,
                                List<String> allowedOrigins,
                                boolean allowCredentials) {
        if (!isProdProfile(environment)) {
            return;
        }

        List<String> origins = normalize(allowedOrigins);
        if (origins.isEmpty()) {
            throw new IllegalStateException(missingOriginsMessage());
        }

        List<String> loopbackOrigins = origins.stream()
                .filter(ProdCorsStartupValidator::isLoopbackOrigin)
                .toList();
        if (!loopbackOrigins.isEmpty()) {
            throw new IllegalStateException(loopbackOriginsMessage(loopbackOrigins, origins));
        }

        // 通配符优先于形态校验: "*" 本身不满足 scheme://host 形态, 若放在后面会被
        // 报成「格式不合法」, 掩盖真正的问题(通配符 + 凭证是 CORS 规范禁止的组合)。
        if (allowCredentials && origins.contains(WILDCARD_ORIGIN)) {
            throw new IllegalStateException(
                    "生产环境 CORS 配置非法: allow-credentials=true 时 allowed-origins 不能包含通配符 \"*\"\n"
                            + "  CORS 规范禁止该组合，浏览器会直接拒绝响应。\n"
                            + "  请改用逐条列出的真实来源：\n"
                            + "    " + ALLOWED_ORIGINS_ENV + "=https://erp.example.com\n"
                            + "  或显式关闭凭证：\n"
                            + "    " + ALLOW_CREDENTIALS_ENV + "=false"
            );
        }

        List<String> malformedOrigins = origins.stream()
                .filter(origin -> !WILDCARD_ORIGIN.equals(origin))
                .filter(origin -> !ORIGIN_SHAPE.matcher(origin).matches())
                .toList();
        if (!malformedOrigins.isEmpty()) {
            throw new IllegalStateException(malformedOriginsMessage(malformedOrigins));
        }
    }

    /** 「变量没设」的标准错误文案：点名变量、给示例、说明后果。 */
    static String missingOriginsMessage() {
        return "生产环境缺少 CORS 允许来源配置: 环境变量 " + ALLOWED_ORIGINS_ENV + " 未设置或为空\n"
                + "  生产启动必须显式声明前端来源，已拒绝回落到开发默认值 "
                + "(http://localhost:3100, http://127.0.0.1:3100)。\n"
                + "  请设置后重启：\n"
                + "    " + ALLOWED_ORIGINS_ENV + "=https://erp.example.com,https://admin.example.com\n"
                + "  多个来源用英文逗号分隔；每项必须是完整 origin（scheme + host + 可选端口），"
                + "不带路径与末尾斜杠。";
    }

    private static String loopbackOriginsMessage(List<String> loopbackOrigins, List<String> allOrigins) {
        return "生产环境 CORS 来源不能包含本机地址: " + String.join(", ", loopbackOrigins) + "\n"
                + "  这通常意味着开发默认值被原样带进了生产：浏览器侧前端域名不会匹配，"
                + "所有跨域请求都会因缺少 CORS 头而失败。\n"
                + "  请把 " + ALLOWED_ORIGINS_ENV + " 设为真实前端域名，例如 https://erp.example.com\n"
                + "  当前值: " + String.join(", ", allOrigins);
    }

    private static String malformedOriginsMessage(List<String> malformedOrigins) {
        return "生产环境 CORS 来源格式不合法: " + String.join(", ", malformedOrigins) + "\n"
                + "  origin 必须严格等于浏览器 Origin 头的值（scheme + host + 可选端口），"
                + "多一个末尾斜杠或路径都会导致永不匹配。\n"
                + "  正确: " + ALLOWED_ORIGINS_ENV + "=https://erp.example.com\n"
                + "  错误: https://erp.example.com/  或  https://erp.example.com/app";
    }

    /** 去空白、丢空项：{@code LEO_CORS_ALLOWED_ORIGINS=a, ,b} 等价于 {@code a,b}。 */
    static List<String> normalize(List<String> allowedOrigins) {
        if (allowedOrigins == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>(allowedOrigins.size());
        for (String origin : allowedOrigins) {
            if (origin == null) {
                continue;
            }
            String trimmed = origin.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    /** 判定是否为回环地址（含 IPv6 字面量 {@code [::1]}），只看 authority 部分，避免误判域名。 */
    static boolean isLoopbackOrigin(String origin) {
        String normalized = origin.toLowerCase(Locale.ROOT);
        int schemeEnd = normalized.indexOf("://");
        String remainder = schemeEnd >= 0 ? normalized.substring(schemeEnd + 3) : normalized;
        int pathStart = remainder.indexOf('/');
        String authority = pathStart >= 0 ? remainder.substring(0, pathStart) : remainder;
        String host;
        if (authority.startsWith("[")) {
            int closingBracket = authority.indexOf(']');
            host = closingBracket >= 0 ? authority.substring(0, closingBracket + 1) : authority;
        } else {
            host = authority.split(":", 2)[0];
        }
        return "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
    }
}
