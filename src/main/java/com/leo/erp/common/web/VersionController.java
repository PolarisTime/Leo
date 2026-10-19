package com.leo.erp.common.web;

import com.leo.erp.common.api.ApiVersion;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class VersionController {

    private static final String UNKNOWN = "unknown";

    private final ObjectProvider<BuildProperties> buildProperties;
    private final ObjectProvider<GitProperties> gitProperties;
    private final String appName;
    /**
     * 是否允许公开精确提交哈希。
     *
     * <p>{@code /version} 标注了 {@code @PublicAccess}，匿名即可拿到 {@code gitCommit}。
     * 提交哈希会把「盲扫」变成「定向利用」：攻击者可据此定位该提交之后修复的缺陷。
     * 版本号本身通常可接受，哈希不应公开。默认关闭，仅开发/测试环境打开以便排障。</p>
     */
    private final boolean exposeCommitHash;

    public VersionController(ObjectProvider<BuildProperties> buildProperties,
                             ObjectProvider<GitProperties> gitProperties,
                             @Value("${spring.application.name:leo}") String appName,
                             @Value("${leo.surface.version.expose-commit-hash:false}") boolean exposeCommitHash) {
        this.buildProperties = buildProperties;
        this.gitProperties = gitProperties;
        this.appName = appName;
        this.exposeCommitHash = exposeCommitHash;
    }

    @PublicAccess
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
                exposeCommitHash && git != null ? valueOrUnknown(git.getShortCommitId()) : null,
                build == null || build.getTime() == null ? null : build.getTime().toString()
        );
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
