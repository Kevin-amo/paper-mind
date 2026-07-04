package com.lqr.papermind.rag;

import com.lqr.papermind.common.model.RetrievedChunk;
import com.lqr.papermind.rag.config.RagProperties;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.rag.service.RerankService;
import com.lqr.papermind.rag.service.impl.RagRetrievalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorFilterExpressionConverter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagRetrievalServiceImplTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final DocumentPersistenceService documentPersistenceService = mock(DocumentPersistenceService.class);
    private final RerankService rerankService = mock(RerankService.class);
    private final RagProperties ragProperties = new RagProperties(800, 120, 5, 0);
    private final UUID ownerUserId = UUID.randomUUID();
    private RagRetrievalServiceImpl service;

    @BeforeEach
    void setUp() {
        when(rerankService.rerank(anyString(), anyList(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        service = new RagRetrievalServiceImpl(vectorStore, ragProperties, documentPersistenceService, rerankService);
    }

    @Test
    void retrieveShouldReturnEmptyWhenVectorRecallMisses() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        List<RetrievedChunk> results = service.retrieve(ownerUserId, "这篇文章的学生姓名是谁", 3);

        assertThat(results).isEmpty();
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void retrieveShouldReturnVectorResultsAfterRerank() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("hit A", Map.of(
                        "ownerUserId", ownerUserId.toString(),
                        "chunkId", "chunk-a",
                        "sourceId", "source-1",
                        "chunkIndex", 0,
                        "title", "Paper A"
                )),
                new Document("hit B", Map.of(
                        "ownerUserId", ownerUserId.toString(),
                        "chunkId", "chunk-b",
                        "sourceId", "source-1",
                        "chunkIndex", 1,
                        "title", "Paper B"
                ))
        ));
        when(documentPersistenceService.findIndexedDocuments(eq(ownerUserId), any()))
                .thenReturn(Map.of("source-1", Boolean.TRUE));

        List<RetrievedChunk> results = service.retrieve(ownerUserId, "排序测试", 3);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).chunk().chunkId()).isEqualTo("chunk-a");
        assertThat(results.get(1).chunk().chunkId()).isEqualTo("chunk-b");
    }

    @Test
    void retrieveShouldFilterUserDocumentsBeforeVectorTopK() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenAnswer(invocation -> {
                    SearchRequest request = invocation.getArgument(0);
                    if (!request.hasFilterExpression()) {
                        return reviewHeavyResultsWithSingleUserHit();
                    }
                    return userResults(5);
                });
        when(documentPersistenceService.findIndexedDocuments(eq(ownerUserId), any()))
                .thenReturn(Map.of("source-user", Boolean.TRUE));

        List<RetrievedChunk> results = service.retrieve(ownerUserId, "核心研究问题 主要贡献", 5);

        assertThat(results).hasSize(5);
        assertThat(results).allMatch(result -> result.chunk().sourceId().equals("source-user"));
    }

    @Test
    void retrieveShouldUsePgVectorSupportedFilterExpression() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenAnswer(invocation -> {
                    SearchRequest request = invocation.getArgument(0);
                    assertThat(request.hasFilterExpression()).isTrue();
                    assertThatCode(() -> new PgVectorFilterExpressionConverter()
                            .convertExpression(request.getFilterExpression()))
                            .doesNotThrowAnyException();
                    return List.of();
                });

        List<RetrievedChunk> results = service.retrieve(ownerUserId, "Graph RAG", 3);

        assertThat(results).isEmpty();
    }

    @Test
    void retrieveShouldUseRerankResultBeforeFinalTopK() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("hit A", Map.of(
                        "ownerUserId", ownerUserId.toString(),
                        "chunkId", "chunk-a",
                        "sourceId", "source-1",
                        "chunkIndex", 0,
                        "title", "Paper A"
                )),
                new Document("hit B", Map.of(
                        "ownerUserId", ownerUserId.toString(),
                        "chunkId", "chunk-b",
                        "sourceId", "source-1",
                        "chunkIndex", 1,
                        "title", "Paper B"
                ))
        ));
        when(documentPersistenceService.findIndexedDocuments(eq(ownerUserId), any()))
                .thenReturn(Map.of("source-1", Boolean.TRUE));
        when(rerankService.rerank(eq("精排测试"), anyList(), eq(2)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<RetrievedChunk> candidates = invocation.getArgument(1);
                    return List.of(candidates.get(1), candidates.get(0));
                });

        List<RetrievedChunk> results = service.retrieve(ownerUserId, "精排测试", 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).chunk().chunkId()).isEqualTo("chunk-b");
        assertThat(results.get(1).chunk().chunkId()).isEqualTo("chunk-a");
        verify(rerankService).rerank(eq("精排测试"), anyList(), eq(2));
    }

    private List<Document> reviewHeavyResultsWithSingleUserHit() {
        return List.of(
                reviewDocument("review-1", 0),
                reviewDocument("review-2", 1),
                reviewDocument("review-3", 2),
                reviewDocument("review-4", 3),
                userDocument("user-0", 0)
        );
    }

    private List<Document> userResults(int count) {
        java.util.ArrayList<Document> documents = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            documents.add(userDocument("user-" + index, index));
        }
        return documents;
    }

    private Document userDocument(String chunkId, int chunkIndex) {
        return new Document("user hit " + chunkIndex, Map.of(
                "ownerUserId", ownerUserId.toString(),
                "chunkId", chunkId,
                "sourceId", "source-user",
                "sourceType", "USER",
                "chunkIndex", chunkIndex,
                "title", "User Paper"
        ));
    }

    private Document reviewDocument(String chunkId, int chunkIndex) {
        return new Document("review hit " + chunkIndex, Map.of(
                "ownerUserId", ownerUserId.toString(),
                "chunkId", chunkId,
                "sourceId", "source-review",
                "sourceType", "REVIEW",
                "chunkIndex", chunkIndex,
                "title", "Review Paper"
        ));
    }
}
