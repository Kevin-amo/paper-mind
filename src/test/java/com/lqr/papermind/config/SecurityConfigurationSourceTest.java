package com.lqr.papermind.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationSourceTest {

    @Test
    void securityConfigurationShouldUseSpringContentTypeOptionsHeader() throws Exception {
        Path source = Path.of(
                System.getProperty("user.dir"),
                "src/main/java/com/lqr/papermind/config/SecurityConfiguration.java"
        );

        String content = Files.readString(source);

        assertThat(content).doesNotContain(".contentTypeOptions(cto -> cto.disable())");
    }
}
