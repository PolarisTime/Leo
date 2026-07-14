package com.leo.erp.common.web;

import com.leo.erp.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VersionControllerTest {

    @Test
    void shouldReturnBuildAndGitMetadataWhenAvailable() {
        ObjectProvider<BuildProperties> buildProvider = mock();
        ObjectProvider<GitProperties> gitProvider = mock();
        BuildProperties buildProperties = new BuildProperties(properties(
                "name", "leo",
                "version", "1.1.2",
                "time", "2026-07-05T03:30:00Z"
        ));
        GitProperties gitProperties = new GitProperties(properties(
                "commit.id.abbrev", "abcdef1",
                "commit.time", "2026-07-05T03:20:00Z"
        ));
        when(buildProvider.getIfAvailable()).thenReturn(buildProperties);
        when(gitProvider.getIfAvailable()).thenReturn(gitProperties);
        VersionController controller = new VersionController(buildProvider, gitProvider, "leo");

        ApiResponse<VersionController.VersionResponse> response = controller.version();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().app()).isEqualTo("leo");
        assertThat(response.data().version()).isEqualTo("1.1.2");
        assertThat(response.data().gitCommit()).isEqualTo("abcdef1");
        assertThat(response.data().buildTime()).isEqualTo("2026-07-05T03:30:00Z");
    }

    @Test
    void shouldReturnUnknownValuesWhenBuildOrGitMetadataIsMissing() {
        ObjectProvider<BuildProperties> buildProvider = mock();
        ObjectProvider<GitProperties> gitProvider = mock();
        when(buildProvider.getIfAvailable()).thenReturn(null);
        when(gitProvider.getIfAvailable()).thenReturn(null);
        VersionController controller = new VersionController(buildProvider, gitProvider, "leo");

        ApiResponse<VersionController.VersionResponse> response = controller.version();

        assertThat(response.data().app()).isEqualTo("leo");
        assertThat(response.data().version()).isEqualTo("unknown");
        assertThat(response.data().gitCommit()).isEqualTo("unknown");
        assertThat(response.data().buildTime()).isNull();
    }

    private static Properties properties(String... values) {
        Properties properties = new Properties();
        for (int index = 0; index < values.length; index += 2) {
            properties.setProperty(values[index], values[index + 1]);
        }
        return properties;
    }
}
