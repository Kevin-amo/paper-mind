package com.lqr.papermind.document;

import com.lqr.papermind.document.config.DocumentIngestionProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档入库配置默认值测试。
 */
class DocumentIngestionPropertiesTest {

    @Test
    void defaultsShouldKeepOriginalUploadForFormatCheck() {
        DocumentIngestionProperties properties = new DocumentIngestionProperties(
                null,
                null,
                0,
                null,
                null
        );

        assertThat(properties.keepUploadFile()).isTrue();
        assertThat(properties.cleanup().enabled()).isFalse();
    }

    @Test
    void localProfileShouldKeepOriginalUploadForFormatCheck() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application-local.yaml"));

        assertThat(yaml).contains("keep-upload-file: true");
        assertThat(yaml).contains("enabled: false");
    }

    @Test
    void dockerComposeDefaultsShouldKeepOriginalUploadForFormatCheck() throws Exception {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose).contains("DOCUMENT_INGESTION_KEEP_UPLOAD_FILE: ${DOCUMENT_INGESTION_KEEP_UPLOAD_FILE:-true}");
        assertThat(compose).contains("DOCUMENT_INGESTION_CLEANUP_ENABLED: ${DOCUMENT_INGESTION_CLEANUP_ENABLED:-false}");
    }
}
