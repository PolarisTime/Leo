package com.leo.erp.security.permission;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionScopeKeyParserTest {

    @Test
    void shouldCreateKey() {
        String key = PermissionScopeKeyParser.key("material", "read");
        assertThat(key).isEqualTo("material:read");
    }

    @Test
    void shouldReturnEmptyKeyWhenResourceBlank() {
        assertThat(PermissionScopeKeyParser.key("", "read")).isEmpty();
        assertThat(PermissionScopeKeyParser.key(null, "read")).isEmpty();
        assertThat(PermissionScopeKeyParser.key("  ", "read")).isEmpty();
    }

    @Test
    void shouldReturnEmptyKeyWhenActionBlank() {
        assertThat(PermissionScopeKeyParser.key("material", "")).isEmpty();
        assertThat(PermissionScopeKeyParser.key("material", null)).isEmpty();
        assertThat(PermissionScopeKeyParser.key("material", "  ")).isEmpty();
    }

    @Test
    void shouldNormalizeKey() {
        String result = PermissionScopeKeyParser.normalize("material:read");
        assertThat(result).isEqualTo("material:read");
    }

    @Test
    void shouldNormalizeKeyWithWhitespace() {
        String result = PermissionScopeKeyParser.normalize("  material  :  read  ");
        assertThat(result).isEqualTo("material:read");
    }

    @Test
    void shouldReturnEmptyForInvalidKey() {
        assertThat(PermissionScopeKeyParser.normalize(null)).isEmpty();
        assertThat(PermissionScopeKeyParser.normalize("")).isEmpty();
        assertThat(PermissionScopeKeyParser.normalize("  ")).isEmpty();
        assertThat(PermissionScopeKeyParser.normalize("noseparator")).isEmpty();
        assertThat(PermissionScopeKeyParser.normalize(":read")).isEmpty();
        assertThat(PermissionScopeKeyParser.normalize("material:")).isEmpty();
    }

    @Test
    void shouldParseResource() {
        assertThat(PermissionScopeKeyParser.parseResource("material:read")).isEqualTo("material");
    }

    @Test
    void shouldReturnEmptyResourceForInvalidKey() {
        assertThat(PermissionScopeKeyParser.parseResource(null)).isEmpty();
        assertThat(PermissionScopeKeyParser.parseResource("")).isEmpty();
        assertThat(PermissionScopeKeyParser.parseResource("invalid")).isEmpty();
    }

    @Test
    void shouldNormalizeResourceAndAction() {
        String result = PermissionScopeKeyParser.normalize("MATERIAL:READ");
        assertThat(result).isEqualTo("material:read");
    }
}
