package com.leo.erp.common.web;

import com.leo.erp.common.api.ApiResponse;
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

    public VersionController(ObjectProvider<BuildProperties> buildProperties,
                             ObjectProvider<GitProperties> gitProperties,
                             @Value("${spring.application.name:leo}") String appName) {
        this.buildProperties = buildProperties;
        this.gitProperties = gitProperties;
        this.appName = appName;
    }

    @PublicAccess
    @GetMapping("/version")
    public ApiResponse<VersionResponse> version() {
        BuildProperties build = buildProperties.getIfAvailable();
        GitProperties git = gitProperties.getIfAvailable();
        return ApiResponse.success(new VersionResponse(
                appName,
                valueOrUnknown(build == null ? null : build.getVersion()),
                valueOrUnknown(git == null ? null : git.getShortCommitId()),
                build == null || build.getTime() == null ? null : build.getTime().toString()
        ));
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
