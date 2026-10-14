package com.leo.erp.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 版本信息里「编译时间」的防回归守卫: {@code META-INF/build-info.properties} 的 {@code build.time}
 * 必须是<b>本次构建的真实时刻</b>, 不能是任何占位/哨兵值。
 *
 * <p>背景(线上实测): 前端底栏显示「后端 v13.0.0 · 编译时间 1980年02月01日 08:00:00」。
 * 原因是 {@code spring-boot-maven-plugin:build-info} 的 {@code time} 默认取
 * {@code project.build.outputTimestamp}, 而构建环境把它固定成了可复现构建的 ZIP 纪元哨兵
 * {@code 1980-02-01T00:00:00Z}(实测: 显式 {@code -Dproject.build.outputTimestamp=1980-02-01T00:00:00Z}
 * 即精确复现该值; {@code SOURCE_DATE_EPOCH=0/空} 在 Maven 3.9.11 下不会触发);
 * 该 instant 渲染到 Asia/Shanghai 正好是 08:00, 于是"编译时间"变成 1980 年。
 * pom 里已显式注入 {@code ${maven.build.timestamp}}。</p>
 *
 * <p>红验证: 把 pom 里 build-info 的 {@code <time>} 去掉、并以
 * {@code -Dproject.build.outputTimestamp=1980-02-01T00:00:00Z} 构建, 本类的断言必须失败。</p>
 */
class BuildInfoFreshnessTest {

    /** 已知占位/哨兵: Unix 纪元、Maven 可复现构建 ZIP 纪元、Spring Boot DATE_MIN(1980-01-01T00:00:02Z)。 */
    private static final Set<Instant> PLACEHOLDER_INSTANTS = Set.of(
            Instant.parse("1970-01-01T00:00:00Z"),
            Instant.parse("1980-01-01T00:00:00Z"),
            Instant.parse("1980-01-01T00:00:02Z"),
            Instant.parse("1980-02-01T00:00:00Z"));

    /** 编译时间必须晚于该下界, 否则视为没被真实注入。 */
    private static final Instant LOWER_BOUND = Instant.parse("2020-01-01T00:00:00Z");

    /** 允许的时钟偏差: 构建产物时间不可能超前于当前时间(留一点构建机时钟偏差余量)。 */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(10);

    /** build-info.properties 的键前缀。 */
    private static final String BUILD_INFO_PREFIX = "build.";

    @Test
    void buildTimeIsRealBuildMomentNotPlaceholder() throws Exception {
        String raw = requireBuildTime();

        Instant time = Instant.parse(raw);
        assertThat(time)
                .as("编译时间不能是占位/哨兵值(实测=%s)", raw)
                .isNotIn(PLACEHOLDER_INSTANTS);
        assertThat(time)
                .as("编译时间必须晚于 %s(实测=%s), 否则说明构建期没有真实注入", LOWER_BOUND, raw)
                .isAfter(LOWER_BOUND);
        assertThat(time)
                .as("编译时间不能在未来(实测=%s)", raw)
                .isBefore(Instant.now().plus(CLOCK_SKEW));
    }

    /** 端点必须原样透出同一份真实构建时刻(而不是另算一份或退化成 null)。 */
    @Test
    void versionEndpointExposesTheSameRealBuildTime() throws Exception {
        BuildProperties buildProperties = new BuildProperties(unprefixed(loadBuildInfo()));
        VersionController controller = new VersionController(
                providerOf(buildProperties), emptyProvider(), "leo");

        String buildTime = controller.version().buildTime();
        assertThat(buildTime).isNotBlank().isEqualTo(buildProperties.getTime().toString());
        assertThat(Instant.parse(buildTime))
                .as("端点透出的编译时间同样不能是占位值(实测=%s)", buildTime)
                .isNotIn(PLACEHOLDER_INSTANTS);
    }

    /**
     * {@link BuildProperties} 的键是<b>去掉 {@code build.} 前缀</b>的
     * ({@code version}/{@code time}/...); 生产侧由 Spring Boot 的
     * {@code BuildPropertiesConfiguration} 以 "build" 前缀加载后再交给它, 这里等价地剥掉前缀。
     */
    private static Properties unprefixed(Properties buildInfoProperties) {
        Properties stripped = new Properties();
        for (String name : buildInfoProperties.stringPropertyNames()) {
            String key = name.startsWith(BUILD_INFO_PREFIX)
                    ? name.substring(BUILD_INFO_PREFIX.length())
                    : name;
            stripped.setProperty(key, buildInfoProperties.getProperty(name));
        }
        return stripped;
    }

    private static String requireBuildTime() throws Exception {
        String raw = loadBuildInfo().getProperty("build.time");
        assertThat(raw)
                .as("构件里必须有 build.time(spring-boot-maven-plugin:build-info 未生效?)")
                .isNotBlank();
        return raw.trim();
    }

    private static Properties loadBuildInfo() throws Exception {
        try (InputStream in = BuildInfoFreshnessTest.class.getResourceAsStream("/META-INF/build-info.properties")) {
            assertThat(in).as("类路径上必须存在 META-INF/build-info.properties").isNotNull();
            Properties properties = new Properties();
            properties.load(in);
            return properties;
        }
    }

    private static ObjectProvider<BuildProperties> providerOf(BuildProperties buildProperties) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("buildProperties", buildProperties);
        return factory.getBeanProvider(BuildProperties.class);
    }

    private static ObjectProvider<GitProperties> emptyProvider() {
        return new StaticListableBeanFactory().getBeanProvider(GitProperties.class);
    }
}
