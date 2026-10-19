package com.leo.erp.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 生产环境 CORS 配置的**最早**启动门禁：在 Environment 就绪后、任何 Bean 创建前校验。
 *
 * <p>为什么用 {@link EnvironmentPostProcessor} 而不是 {@code @Configuration} 构造函数：
 * 配置类本身也是一个 Bean，它的实例化顺序和其它 Bean 竞争。实测踩到——用
 * {@code @Profile("prod")} 配置类校验时，prod 下若同时漏配 {@code LEO_MACHINE_ID}
 * （{@code strict-machine-id=true} 时 machine-id 不能为 0），{@code SnowflakeIdGenerator}
 * 会先抛异常，CORS 的报错**根本没机会出现**，运维看到的是机器号问题，
 * 修完重启才撞上第二个坑。放到 EnvironmentPostProcessor 里，CORS 校验先于一切 Bean，
 * 保证「先报最该报的那条」。</p>
 *
 * <p>校验逻辑本身在 {@link ProdCorsStartupValidator}（静态纯函数），本类只负责
 * 「在正确时机用与 {@code @ConfigurationProperties} 相同的方式绑定，然后调用它」。</p>
 */
public class ProdCorsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!ProdCorsStartupValidator.isProdProfile(environment)) {
            return;
        }
        // 与 @ConfigurationProperties 的真实绑定路径保持一致（含占位符解析）,
        // 否则会拿到 "${LEO_CORS_ALLOW_CREDENTIALS:true}" 字面量而误判。
        Binder binder = new Binder(
                ConfigurationPropertySources.get(environment),
                new PropertySourcesPlaceholdersResolver(environment));
        WebSecurityProperties properties = binder
                .bind("leo.security.web", Bindable.of(WebSecurityProperties.class))
                .orElseGet(WebSecurityProperties::new);
        WebSecurityProperties.Cors cors = properties.getCors();
        ProdCorsStartupValidator.validate(environment, cors.getAllowedOrigins(), cors.isAllowCredentials());
    }

    @Override
    public int getOrder() {
        // 必须晚于 ConfigDataEnvironmentPostProcessor(负责加载 application-prod.yml),
        // 否则读不到配置文件里的值。LOWEST_PRECEDENCE 保证在全部配置源就绪后执行。
        return Ordered.LOWEST_PRECEDENCE;
    }
}
