package com.lqr.papermind.auth.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityPropertiesTest {

    private static final String TEST_SECRET = "test-jwt-secret-with-at-least-32-characters";

    @Test
    void jwtShouldRejectMissingSecret() {
        assertThatThrownBy(() -> new SecurityProperties.Jwt(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.security.jwt.secret must be configured");
    }

    @Test
    void jwtShouldRejectBlankSecret() {
        assertThatThrownBy(() -> new SecurityProperties.Jwt(null, "   ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.security.jwt.secret must be configured");
    }

    @Test
    void jwtShouldKeepSafeDefaultsWhenSecretIsConfigured() {
        SecurityProperties.Jwt jwt = new SecurityProperties.Jwt(null, TEST_SECRET, null);

        assertThat(jwt.issuer()).isEqualTo("paper-mind");
        assertThat(jwt.secret()).isEqualTo(TEST_SECRET);
        assertThat(jwt.accessTokenTtl()).isEqualTo(Duration.ofHours(2));
    }
}
