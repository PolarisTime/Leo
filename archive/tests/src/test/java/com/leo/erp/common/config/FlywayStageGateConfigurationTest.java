package com.leo.erp.common.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class FlywayStageGateConfigurationTest {

    @Test
    void productionRequiresAnExplicitFlywayTargetWhileOtherProfilesValidateLatest() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var application = loader.load("application", new ClassPathResource("application.yml")).getFirst();
        var production = loader.load(
                "application-prod",
                new ClassPathResource("application-prod.yml")
        ).getFirst();

        assertThat(application.getProperty("spring.flyway.target"))
                .isEqualTo("${SPRING_FLYWAY_TARGET:latest}");
        assertThat(production.getProperty("spring.flyway.target"))
                .isEqualTo("${SPRING_FLYWAY_TARGET:}");
    }

    @Test
    void productionWorkflowVerifiesTheRequestedTargetBeforeEitherDeploymentPath() throws Exception {
        String workflow = Files.readString(
                Path.of(".github/workflows/deploy-production.yml"),
                StandardCharsets.UTF_8
        );

        assertThat(workflow)
                .contains("      flyway_target:")
                .contains("        description: \"Highest Flyway version allowed in production\"")
                .contains("        required: true")
                .contains("        default: \"19\"")
                .contains("        type: string")
                .contains("FLYWAY_TARGET: ${{ inputs.flyway_target || vars.PROD_FLYWAY_TARGET }}")
                .contains("Verify local production Flyway target")
                .contains("Verify SSH production Flyway target")
                .contains(": \"${FLYWAY_TARGET:?Missing production Flyway target}\"")
                .contains("[[ ! \"$FLYWAY_TARGET\" =~ ^[1-9][0-9]*$ ]]")
                .contains("$0 ~ /^SPRING_FLYWAY_TARGET=/")
                .contains("if (count != 1) exit 1")
                .contains("[[ \"$runtime_flyway_target\" != \"$expected_flyway_target\" ]]")
                .doesNotContain("source \"$PROD_SHARED_DIR/steelx.env\"")
                .doesNotContain("source \"$env_file\"")
                .doesNotContain("FLYWAY_TARGET: latest")
                .doesNotContain("SPRING_FLYWAY_TARGET: latest");
    }

    @Test
    void productionWorkflowPassesAnExplicitTargetToBothMavenFlywayChecks() throws Exception {
        String workflow = Files.readString(
                Path.of(".github/workflows/deploy-production.yml"),
                StandardCharsets.UTF_8
        );

        assertThat(workflow)
                .contains("FLYWAY_TARGET: ${{ inputs.flyway_target || vars.PROD_FLYWAY_TARGET }}")
                .contains("Validate production Flyway target")
                .contains(": \"${FLYWAY_TARGET:?Missing production Flyway target}\"")
                .contains("[[ ! \"$FLYWAY_TARGET\" =~ ^[1-9][0-9]*$ ]]")
                .contains("-Dflyway.target=\"$FLYWAY_TARGET\"")
                .contains("--arg flywayTarget \"$FLYWAY_TARGET\"")
                .contains("flywayTarget: $flywayTarget")
                .doesNotContain("inputs.flyway_target || 'latest'");
        assertThat(workflow.indexOf("Validate production Flyway target"))
                .isLessThan(workflow.indexOf("mvn -B -ntp flyway:migrate"));
        assertThat(workflow.indexOf("-Dflyway.target=\"$FLYWAY_TARGET\""))
                .isGreaterThanOrEqualTo(0);
        assertThat(workflow.lastIndexOf("-Dflyway.target=\"$FLYWAY_TARGET\""))
                .isGreaterThan(workflow.indexOf("-Dflyway.target=\"$FLYWAY_TARGET\""));
    }

    @Test
    void productionTriggerPassesTheExplicitFlywayTargetToWorkflowDispatch() throws Exception {
        String deploymentScript = Files.readString(
                Path.of("scripts/deploy/trigger-production-deploy.sh"),
                StandardCharsets.UTF_8
        );

        assertThat(deploymentScript)
                .contains("--flyway-target <version>")
                .contains("FLYWAY_TARGET must be a positive integer")
                .contains("-f \"flyway_target=$FLYWAY_TARGET\"");
    }

    @Test
    void localDeploymentEntryWritesTheExplicitFlywayTargetIntoProductionEnvironment() throws Exception {
        String deploymentScript = Files.readString(
                Path.of("scripts/deploy/trigger-local-steelx-deploy.sh"),
                StandardCharsets.UTF_8
        );

        assertThat(deploymentScript)
                .contains("--flyway-target <version>")
                .contains("SPRING_FLYWAY_TARGET=$FLYWAY_TARGET")
                .contains("FLYWAY_TARGET must be a positive integer");
    }

    @Test
    void productionRuntimeGateRejectsMissingTarget() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withUserConfiguration(FlywayStageGateConfiguration.class)
                .withPropertyValues("spring.flyway.target=")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseMessage("Missing production Flyway target"));
    }

    @Test
    void productionRuntimeGateRejectsNonPositiveIntegerTarget() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withUserConfiguration(FlywayStageGateConfiguration.class)
                .withPropertyValues("spring.flyway.target=latest")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseMessage("Production Flyway target must be a positive integer version: latest"));
    }

    @Test
    void productionRuntimeGateAcceptsPositiveIntegerTarget() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withUserConfiguration(FlywayStageGateConfiguration.class)
                .withPropertyValues("spring.flyway.target=19")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(FlywayStageGateConfiguration.class)
                        .hasSingleBean(FlywayMigrationStrategy.class));
    }

    @Test
    void nonProductionProfileDoesNotRequireAStageTarget() {
        new ApplicationContextRunner()
                .withUserConfiguration(FlywayStageGateConfiguration.class)
                .withPropertyValues("spring.flyway.target=latest")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(FlywayStageGateConfiguration.class));
    }

    @Test
    void productionRuntimeGateRejectsInvalidTargetBeforeFlywayMigrate() {
        Flyway flyway = mock(Flyway.class);
        FlywayMigrationStrategy strategy = FlywayStageGateConfiguration.guardedMigrationStrategy("latest");
        FlywayMigrationInitializer initializer = new FlywayMigrationInitializer(flyway, strategy);

        assertThatThrownBy(initializer::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production Flyway target must be a positive integer version: latest");
        verifyNoInteractions(flyway);
    }

    @Test
    void productionRuntimeGateMigratesOnlyAfterAcceptingTarget() throws Exception {
        Flyway flyway = mock(Flyway.class);
        FlywayMigrationStrategy strategy = FlywayStageGateConfiguration.guardedMigrationStrategy("19");
        FlywayMigrationInitializer initializer = new FlywayMigrationInitializer(flyway, strategy);

        initializer.afterPropertiesSet();

        verify(flyway).migrate();
    }
}
