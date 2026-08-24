package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class TradeItemMaterialSupportTest {

    @Test
    void marksTheDependencyConstructorForSpringInjection() throws NoSuchMethodException {
        Constructor<TradeItemMaterialSupport> constructor = TradeItemMaterialSupport.class
                .getConstructor(MaterialCatalog.class, SnowflakeIdGenerator.class);

        assertThat(constructor.isAnnotationPresent(Autowired.class)).isTrue();
    }
}
