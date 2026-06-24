package com.lqr.papermind.config;

import com.lqr.papermind.auth.config.SecurityProperties;
import com.lqr.papermind.auth.security.JwtAuthenticationFilter;
import com.lqr.papermind.auth.security.RestAccessDeniedHandler;
import com.lqr.papermind.auth.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        SecurityConfigurationHeadersTest.TestApplication.class,
        SecurityConfigurationHeadersTest.HealthProbeController.class,
        SecurityConfigurationHeadersTest.TestSecurityPropertiesConfiguration.class
})
@AutoConfigureMockMvc
class SecurityConfigurationHeadersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private RestAuthenticationEntryPoint authenticationEntryPoint;

    @MockitoBean
    private RestAccessDeniedHandler accessDeniedHandler;

    @Test
    void permittedResponsesShouldIncludeSecurityHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
            RabbitAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class,
            PgVectorStoreAutoConfiguration.class
    })
    @Import(SecurityConfiguration.class)
    static class TestApplication {
    }

    @RestController
    static class HealthProbeController {

        @GetMapping("/actuator/health")
        String health() {
            return "{\"status\":\"UP\"}";
        }
    }

    static class TestSecurityPropertiesConfiguration {

        @Bean
        SecurityProperties securityProperties() {
            return new SecurityProperties(
                    new SecurityProperties.Jwt(
                            "paper-mind",
                            "test-jwt-secret-with-at-least-32-characters",
                            Duration.ofHours(2)
                    ),
                    null,
                    null,
                    null
            );
        }
    }
}
