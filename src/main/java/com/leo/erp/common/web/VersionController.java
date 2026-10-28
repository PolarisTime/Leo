package com.leo.erp.common.web;

import com.leo.erp.common.api.ApiVersion;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 版本信息端点。
 *
 * <p><b>为什么需要登录</b>：返回体包含精确提交哈希（{@code gitCommit}）。哈希可让攻击者
 * 把「盲扫」变成「定向利用」——据此定位该提交之后修复的缺陷。因此本端点<b>不再</b>标注
 * {@code @PublicAccess}：匿名请求在安全过滤链就被拒绝，能拿到响应的都是已认证用户，
 * 提交哈希因而可以直接返回（不再需要 {@code expose-commit-hash} 开关）。</p>
 */
@RestController
@Validated
public class VersionController {

    private static final String UNKNOWN = "unknown";

    private final ObjectProvider<BuildProperties> buildProperties;
    private final ObjectProvider<GitProperties> gitProperties;
    private final String appName;

    public VersionController(ObjectProvider<BuildProperties> buildProperties,
                             ObjectProvider<GitProperties> gitProperties,
                             @Value("${spring.application.name:leo}") String appName) {
        this.buildProperties = buildProperties;
        this.gitProperties = gitProperties;
        this.appName = appName;
    }

    @GetMapping(ApiVersion.V2_PREFIX + "/version")
    public VersionResponse version() {
        return currentVersion();
    }

    private VersionResponse currentVersion() {
        BuildProperties build = buildProperties.getIfAvailable();
        GitProperties git = gitProperties.getIfAvailable();
        return new VersionResponse(
                appName,
                valueOrUnknown(build == null ? null : build.getVersion()),
                git == null ? null : valueOrNull(git.getShortCommitId()),
                build == null || build.getTime() == null ? null : build.getTime().toString()
        );
    }

    /** 端点只输出真实值；缺失时为 null（前端渲染成 `--`），不要伪装成 "unknown"。 */
    private static String valueOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }

    public record VersionResponse(
            String app,
            String version,
            String gitCommit,
            String buildTime
    ) {
    }
}
