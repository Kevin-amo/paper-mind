package com.lqr.papermind.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaEmbeddingConfigurationTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();

    @Test
    void applicationYamlShouldUseOllamaEmbeddingDefaults() throws IOException {
        String yaml = read("src/main/resources/application.yaml");

        assertThat(yaml).contains("  ai:");
        assertThat(yaml).contains("    model:");
        assertThat(yaml).contains("      chat: ${SPRING_AI_MODEL_CHAT:dashscope}");
        assertThat(yaml).contains("      embedding: ${SPRING_AI_MODEL_EMBEDDING:ollama}");
        assertThat(yaml).contains("base-url: ${OLLAMA_BASE_URL:http://localhost:11434}");
        assertThat(yaml).contains("model: ${OLLAMA_EMBEDDING_MODEL:qwen3-embedding:8b}");
        assertThat(yaml).contains("dimensions: ${PGVECTOR_DIMENSIONS:4096}");
        assertThat(yaml).contains("index-type: ${PGVECTOR_INDEX_TYPE:none}");
    }

    @Test
    void deploymentDefaultsShouldExposeOllamaEmbeddingSettings() throws IOException {
        String envExample = read(".env.example");
        String compose = read("docker-compose.yml");

        assertThat(envExample).contains("OLLAMA_BASE_URL=http://localhost:11434");
        assertThat(envExample).contains("SPRING_AI_MODEL_CHAT=dashscope");
        assertThat(envExample).contains("OLLAMA_EMBEDDING_MODEL=qwen3-embedding:8b");
        assertThat(envExample).contains("PGVECTOR_DIMENSIONS=4096");
        assertThat(envExample).contains("PGVECTOR_INDEX_TYPE=none");

        assertThat(compose).contains("SPRING_AI_MODEL_CHAT: ${SPRING_AI_MODEL_CHAT:-dashscope}");
        assertThat(compose).contains("OLLAMA_BASE_URL: ${OLLAMA_BASE_URL:-http://localhost:11434}");
        assertThat(compose).contains("OLLAMA_EMBEDDING_MODEL: ${OLLAMA_EMBEDDING_MODEL:-qwen3-embedding:8b}");
        assertThat(compose).contains("PGVECTOR_DIMENSIONS: ${PGVECTOR_DIMENSIONS:-4096}");
        assertThat(compose).contains("PGVECTOR_INDEX_TYPE: ${PGVECTOR_INDEX_TYPE:-none}");
    }

    @Test
    void sqlAndReadmeShouldDocumentOllamaVectorDimension() throws IOException {
        String sql = read("src/main/resources/sql/paper-mind.sql");
        String readme = read("README.md");

        assertThat(sql).contains("embedding public.vector(4096)");
        assertThat(sql).contains("Ollama qwen3-embedding:8b");
        assertThat(sql).doesNotContain("USING hnsw");
        assertThat(sql).doesNotContain("vector_store_embedding_idx");

        assertThat(readme).contains("qwen3-embedding:8b");
        assertThat(readme).contains("http://localhost:11434");
        assertThat(readme).contains("4096");
        assertThat(readme).contains("PGVECTOR_INDEX_TYPE=none");
        assertThat(readme).contains("DROP INDEX IF EXISTS public.vector_store_embedding_idx;");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }
}
