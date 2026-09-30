package com.leo.erp.market;

import com.leo.erp.market.mysteel.MysteelProperties;
import com.leo.erp.market.steelx.SteelxProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 取数重试配置绑定测试：直接读取真实的 {@code application.yml}，
 * 确认两个数据源的 {@code retry} 段能绑定到各自的 Properties，
 * 且默认值与 {@code RetryProperties} 一致（配置写错 key 时这里会立即失败，
 * 而不是等到线上抓取失败才发现重试没生效）。
 */
class MarketRetryConfigBindingTest {

    private StandardEnvironment environment() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        yaml.forEach(sources::addLast);
        return environment;
    }

    @Test
    void mysteel重试配置绑定成功() throws IOException {
        MysteelProperties properties = Binder.get(environment())
                .bind("leo.market.steel-quote", MysteelProperties.class)
                .orElseThrow(() -> new AssertionError("application.yml 未绑定该配置段"));

        assertThat(properties.getRetry()).isNotNull();
        assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
        assertThat(properties.getRetry().getInitialBackoffMillis()).isEqualTo(1000L);
        assertThat(properties.getRetry().getMultiplier()).isEqualTo(2.0);
        assertThat(properties.getRetry().getMaxBackoffMillis()).isEqualTo(8000L);
    }

    @Test
    void 西本重试配置绑定成功() throws IOException {
        SteelxProperties properties = Binder.get(environment())
                .bind("leo.market.steelx-quote", SteelxProperties.class)
                .orElseThrow(() -> new AssertionError("application.yml 未绑定该配置段"));

        assertThat(properties.getRetry()).isNotNull();
        assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
        assertThat(properties.getRetry().getInitialBackoffMillis()).isEqualTo(1000L);
        assertThat(properties.getRetry().getMultiplier()).isEqualTo(2.0);
        assertThat(properties.getRetry().getMaxBackoffMillis()).isEqualTo(8000L);
    }
}
