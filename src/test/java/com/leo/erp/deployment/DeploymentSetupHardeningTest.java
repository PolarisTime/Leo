package com.leo.erp.deployment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentSetupHardeningTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();
    private static final String SETUP_TOKEN_KEY = "LEO_SETUP_BOOTSTRAP_TOKEN";

    @TempDir
    Path tempDir;

    @Test
    void localDeploymentShouldPreserveExistingSetupTokenWithoutLoggingIt() throws Exception {
        ScriptFixture fixture = createScriptFixture();
        Path deploymentRoot = tempDir.resolve("existing-token-root");
        Files.createDirectories(deploymentRoot.resolve("shared"));
        String existingToken = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        Files.writeString(
                deploymentRoot.resolve("shared/steelx.env"),
                "SERVER_PORT=12345\n" + SETUP_TOKEN_KEY + "=" + existingToken + "\n",
                StandardCharsets.UTF_8
        );

        ScriptResult result = runDeploymentScript(fixture, deploymentRoot);

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(envAssignments(deploymentRoot)).containsExactly(SETUP_TOKEN_KEY + "=" + existingToken);
        assertThat(result.output()).doesNotContain(existingToken);
    }

    @Test
    void localDeploymentShouldGenerateBase64UrlSetupTokenWithoutLoggingIt() throws Exception {
        ScriptFixture fixture = createScriptFixture();
        Path deploymentRoot = tempDir.resolve("generated-token-root");

        ScriptResult result = runDeploymentScript(fixture, deploymentRoot);

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(envAssignments(deploymentRoot))
                .containsExactly(SETUP_TOKEN_KEY + "=" + fixture.expectedGeneratedToken());
        assertThat(fixture.expectedGeneratedToken()).hasSize(43).matches("[A-Za-z0-9_-]{43}");
        assertThat(result.output()).doesNotContain(fixture.expectedGeneratedToken());
    }

    @Test
    void nginxTemplatesShouldRestrictSetupPathParameterWritesBeforeGenericApiProxy() throws IOException {
        for (String relativePath : List.of("deploy/nginx/leo.conf", "deploy/nginx/steelx.conf")) {
            String config = Files.readString(PROJECT_ROOT.resolve(relativePath));
            int setupPrefixLocation = config.indexOf("location ^~ /api/setup {");
            int genericApiLocation = config.indexOf("location /api/ {");
            String upstream = relativePath.endsWith("steelx.conf")
                    ? "http://127.0.0.1:57217"
                    : "http://127.0.0.1:11211";

            assertThat(setupPrefixLocation).as(relativePath).isGreaterThanOrEqualTo(0);
            assertThat(genericApiLocation).as(relativePath).isGreaterThan(setupPrefixLocation);
            assertThat(occurrences(config, "limit_except GET HEAD OPTIONS {")).as(relativePath).isEqualTo(1);
            assertThat(occurrences(config, "allow 127.0.0.1;")).as(relativePath).isEqualTo(1);
            assertThat(occurrences(config, "allow ::1;")).as(relativePath).isEqualTo(1);
            assertThat(occurrences(config, "deny all;")).as(relativePath).isEqualTo(1);
            assertThat(config).as(relativePath)
                    .contains("proxy_pass " + upstream + ";")
                    .doesNotContain("location = /api/setup {")
                    .doesNotContain("location ^~ /api/setup/ {");
        }
    }

    @Test
    void deploymentGuideShouldDocumentSetupTokenAndAccessBoundary() throws IOException {
        String guide = Files.readString(PROJECT_ROOT.resolve("docs/deployment/production-cicd.md"));

        assertThat(guide)
                .contains("LEO_SETUP_BOOTSTRAP_TOKEN=")
                .contains("GET /api/setup/status")
                .contains("127.0.0.1")
                .contains("::1");
    }

    private ScriptFixture createScriptFixture() throws IOException {
        Path fixtureRoot = tempDir.resolve("fixture-" + System.nanoTime());
        Path deployDir = fixtureRoot.resolve("scripts/deploy");
        Path envDir = fixtureRoot.resolve("scripts/env");
        Path fakeBin = fixtureRoot.resolve("fake-bin");
        Files.createDirectories(deployDir);
        Files.createDirectories(envDir);
        Files.createDirectories(fakeBin);

        Files.copy(
                PROJECT_ROOT.resolve("scripts/deploy/trigger-local-steelx-deploy.sh"),
                deployDir.resolve("trigger-local-steelx-deploy.sh"),
                StandardCopyOption.REPLACE_EXISTING
        );
        writeExecutable(deployDir.resolve("build-local-release.sh"), "#!/usr/bin/env bash\nexit 0\n");
        writeExecutable(deployDir.resolve("install-production-release.sh"), "#!/usr/bin/env bash\nexit 0\n");
        writeExecutable(deployDir.resolve("steelx-process.sh"), "#!/usr/bin/env bash\nexit 0\n");
        Files.writeString(envDir.resolve("dev.sh"), "#!/usr/bin/env bash\n", StandardCharsets.UTF_8);

        byte[] generatedBytes = new byte[32];
        Arrays.fill(generatedBytes, (byte) 0xfb);
        String standardBase64 = Base64.getEncoder().encodeToString(generatedBytes);
        String expectedBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(generatedBytes);
        writeExecutable(fakeBin.resolve("openssl"), """
                #!/usr/bin/env bash
                printf '%%s\n' '%s'
                """.formatted(standardBase64));
        writeExecutable(fakeBin.resolve("mvn"), "#!/usr/bin/env bash\nprintf '%s\n' '2.1.0'\n");
        writeExecutable(fakeBin.resolve("psql"), "#!/usr/bin/env bash\ncat >/dev/null\n");
        writeExecutable(fakeBin.resolve("redis-cli"), "#!/usr/bin/env bash\nexit 0\n");
        return new ScriptFixture(deployDir.resolve("trigger-local-steelx-deploy.sh"), fakeBin, expectedBase64Url);
    }

    private ScriptResult runDeploymentScript(ScriptFixture fixture, Path deploymentRoot)
            throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "bash",
                fixture.script().toString(),
                "--confirm",
                "--skip-tests",
                "--root",
                deploymentRoot.toString(),
                "--db-admin-password",
                "test-admin-password"
        );
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put(
                "PATH",
                fixture.fakeBin() + ":" + System.getenv().getOrDefault("PATH", "/usr/bin:/bin")
        );
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return new ScriptResult(exitCode, output);
    }

    private List<String> envAssignments(Path deploymentRoot) throws IOException {
        return Files.readAllLines(deploymentRoot.resolve("shared/steelx.env"), StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith(SETUP_TOKEN_KEY + "="))
                .toList();
    }

    private void writeExecutable(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
        assertThat(path.toFile().setExecutable(true)).isTrue();
    }

    private int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }

    private record ScriptFixture(Path script, Path fakeBin, String expectedGeneratedToken) {
    }

    private record ScriptResult(int exitCode, String output) {
    }
}
