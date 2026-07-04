package com.leo.erp.system.runtimeconfig.feature;

import dev.openfeature.sdk.Client;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenFeatureFlagServiceTest {

    private final Client client = mock(Client.class);
    private final OpenFeatureFlagService service = new OpenFeatureFlagService(client);

    @Test
    void returnsBooleanFlagValue() {
        when(client.getBooleanValue("feature-a", false)).thenReturn(true);

        assertThat(service.isEnabled("feature-a", false)).isTrue();
    }

    @Test
    void fallsBackWhenProviderFails() {
        when(client.getBooleanValue("feature-a", true)).thenThrow(new IllegalStateException("provider down"));

        assertThat(service.isEnabled("feature-a", true)).isTrue();
    }

    @Test
    void fallsBackForBlankKey() {
        assertThat(service.isEnabled(" ", true)).isTrue();
    }
}
