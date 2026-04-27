package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgresJdbcUrlParserTest {

    @Test
    void shouldParseJdbcUrlWithExplicitPortAndQueryParameters() {
        PostgresJdbcUrlParser.ParsedJdbcUrl parsed = PostgresJdbcUrlParser.parse(
                "jdbc:postgresql://db.internal:5544/leo_prod?sslmode=require&targetServerType=primary"
        );

        assertThat(parsed.host()).isEqualTo("db.internal");
        assertThat(parsed.port()).isEqualTo(5544);
        assertThat(parsed.database()).isEqualTo("leo_prod");
    }

    @Test
    void shouldUseDefaultPortWhenJdbcUrlOmitsPort() {
        PostgresJdbcUrlParser.ParsedJdbcUrl parsed = PostgresJdbcUrlParser.parse(
                "jdbc:postgresql://localhost/leo"
        );

        assertThat(parsed.host()).isEqualTo("localhost");
        assertThat(parsed.port()).isEqualTo(5432);
        assertThat(parsed.database()).isEqualTo("leo");
    }

    @Test
    void shouldRejectInvalidJdbcUrl() {
        assertThatThrownBy(() -> PostgresJdbcUrlParser.parse("jdbc:mysql://localhost:3306/leo"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JDBC URL");
    }
}
