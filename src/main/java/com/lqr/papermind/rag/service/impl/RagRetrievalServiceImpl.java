package com.lqr.papermind.rag.service.impl;

import com.lqr.papermind.ai.service.RerankService;
import com.lqr.papermind.common.logging.LogSanitizer;
import com.lqr.papermind.common.constant.MetadataKeys;
import com.lqr.papermind.common.model.DocumentChunk;
import com.lqr.papermind.common.model.RetrievedChunk;
import com.lqr.papermind.rag.config.RagProperties;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.rag.service.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 基于向量库的检索实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;
    private final DocumentPersistenceService documentPersistenceService;
    private final RerankService rerankService;

    /**
     * 根据问题从向量库召回最相关的文档分块。
     *
     * @param ownerUserId 拥有者用户ID
     * @param question 用户问题
     * @param topK 召回数量
     * @return 按相关度排序的分块列表
     */
    @Override
    public List<RetrievedChunk> retrieve(UUID ownerUserId, String question, int topK) {
        long startNanos = System.nanoTime();
        if (ownerUserId == null || question == null || question.isBlank()) {
            log.warn("RAG检索跳过 ownerUserId={} queryLength={} requestedTopK={} reason=INVALID_ARGUMENT",
                    ownerUserId, question == null ? 0 : question.length(), topK);
            return List.of();
        }
        int resolvedTopK = topK > 0 ? topK : ragProperties.defaultTopK();
        int candidateTopK = rerankCandidateTopK(resolvedTopK);
        double similarityThreshold = ragProperties.similarityThreshold();
        log.info("RAG检索开始 ownerUserId={} queryExcerpt={} requestedTopK={} resolvedTopK={} candidateTopK={} similarityThreshold={}",
                ownerUserId, LogSanitizer.safeExcerpt(question, 160), topK, resolvedTopK, candidateTopK, similarityThreshold);
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(question)
                    .topK(candidateTopK)
                    .filterExpression(userDocumentFilter(ownerUserId));
            if (similarityThreshold > 0) {
                builder.similarityThreshold(similarityThreshold);
            } else {
                builder.similarityThresholdAll();
            }
            // 把用户问题变成向量，然后在向量库中查找最相关的文档 -- 按语义查询
            List<Document> documents = vectorStore.similaritySearch(builder.build());
            List<String> sourceIds = documents.stream()
                    .map(doc -> {
                        Map<String, Object> meta = doc.getMetadata();
                        // 获取文档的源ID
                        return meta == null ? null : String.valueOf(meta.getOrDefault(MetadataKeys.SOURCE_ID, ""));
                    })
                    // 过滤掉无效的源ID
                    .filter(id -> id != null && !id.isBlank())
                    // 去重
                    .distinct()
                    // 转换为列表
                    .toList();
            // 获取源ID对应的文档是否已索引
            Map<String, Boolean> indexedMap = documentPersistenceService.findIndexedDocuments(ownerUserId, sourceIds);
            List<RetrievedChunk> vectorChunks = new ArrayList<>(documents.size());
            FilterStats filterStats = new FilterStats();
            int index = 0;
            for (Document document : documents) {
                Map<String, Object> metadata = document.getMetadata();
                String sourceId = metadata == null ? null : String.valueOf(metadata.getOrDefault(MetadataKeys.SOURCE_ID, ""));
                String metadataOwnerUserId = metadata == null ? null : String.valueOf(metadata.getOrDefault(MetadataKeys.OWNER_USER_ID, ""));
                if (!ownerUserId.toString().equals(metadataOwnerUserId)) {
                    filterStats.ownerMismatchCount++;
                    continue;
                }
                if (isReviewSource(metadata)) {
                    filterStats.reviewSourceCount++;
                    continue;
                }
                if (sourceId == null || sourceId.isBlank()) {
                    filterStats.invalidSourceCount++;
                    continue;
                }
                if (!indexedMap.getOrDefault(sourceId, Boolean.FALSE)) {
                    filterStats.notIndexedCount++;
                    continue;
                }
                String chunkId = metadata == null ? document.getId() : String.valueOf(metadata.getOrDefault(MetadataKeys.CHUNK_ID, document.getId()));
                int currentIndex = index++;
                int chunkIndex = intMetadata(metadata, MetadataKeys.CHUNK_INDEX, currentIndex);
                DocumentChunk chunk = new DocumentChunk(
                        chunkId,
                        sourceId,
                        chunkIndex,
                        document.getText(),
                        metadata
                );
                Double vectorScore = document.getScore();
                vectorChunks.add(new RetrievedChunk(chunk, vectorRankContribution(vectorScore, currentIndex, documents.size())));
            }
            log.info("RAG检索向量完成 ownerUserId={} vectorRawCount={} vectorFilteredCount={} ownerMismatchCount={} reviewSourceCount={} invalidSourceCount={} notIndexedCount={} costMs={}",
                    ownerUserId, documents.size(), vectorChunks.size(), filterStats.ownerMismatchCount, filterStats.reviewSourceCount, filterStats.invalidSourceCount, filterStats.notIndexedCount, elapsedMs(startNanos));

            List<RetrievedChunk> reranked = rerankService.rerank(question, vectorChunks, resolvedTopK);
            List<RetrievedChunk> finalChunks = reranked.stream()
                    .limit(resolvedTopK)
                    .toList();
            log.info("RAG检索完成 ownerUserId={} resolvedTopK={} vectorCount={} rerankBeforeCount={} rerankAfterCount={} finalCount={} costMs={}",
                    ownerUserId, resolvedTopK, vectorChunks.size(), vectorChunks.size(), reranked.size(), finalChunks.size(), elapsedMs(startNanos));
            logFinalChunks(finalChunks);
            return finalChunks;
        } catch (RuntimeException ex) {
            log.error("RAG检索失败 ownerUserId={} queryExcerpt={} requestedTopK={} resolvedTopK={} candidateTopK={} costMs={}",
                    ownerUserId, LogSanitizer.safeExcerpt(question, 160), topK, resolvedTopK, candidateTopK, elapsedMs(startNanos), ex);
            throw ex;
        }
    }

    /**
     * 根据配置的候选倍数计算重排序所需的候选召回数量。
     *
     * @param resolvedTopK 最终需要返回的分块数量
     * @return 重排序前的候选召回数量
     */
    private int rerankCandidateTopK(int resolvedTopK) {
        int multiplier = ragProperties.rerank().candidateMultiplier();
        return Math.max(resolvedTopK * multiplier, resolvedTopK);
    }

    /**
     * 在 DEBUG 级别逐条输出最终召回片段的详细信息。
     *
     * @param chunks 最终召回的片段列表
     */
    private void logFinalChunks(List<RetrievedChunk> chunks) {
        if (!log.isDebugEnabled()) {
            return;
        }
        for (RetrievedChunk retrieved : chunks) {
            DocumentChunk chunk = retrieved.chunk();
            Map<String, Object> metadata = chunk.metadata();
            log.debug("RAG检索最终分块 sourceId={} chunkId={} chunkIndex={} sectionTitle={} sectionType={} rankScore={} excerpt={}",
                    chunk.sourceId(),
                    chunk.chunkId(),
                    chunk.chunkIndex(),
                    stringMetadata(metadata, MetadataKeys.SECTION_TITLE),
                    stringMetadata(metadata, MetadataKeys.SECTION_TYPE),
                    retrieved.rankScore(),
                    LogSanitizer.safeExcerpt(chunk.content(), 160));
        }
    }

    /**
     * 从元数据映射中安全提取指定键的字符串值。
     *
     * @param metadata 元数据映射
     * @param key 目标字段名
     * @return 提取的字符串值，不存在时返回 null
     */
    private String stringMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 计算从指定起点到当前时间的毫秒耗时。
     *
     * @param startNanos 起始纳秒时间戳
     * @return 耗时毫秒数
     */
    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static final class FilterStats {
        private int ownerMismatchCount;
        private int reviewSourceCount;
        private int invalidSourceCount;
        private int notIndexedCount;
    }

    private boolean isReviewSource(Map<String, Object> metadata) {
        if (metadata == null) {
            return false;
        }
        Object sourceType = metadata.get(MetadataKeys.SOURCE_TYPE);
        return MetadataKeys.SOURCE_TYPE_REVIEW.equalsIgnoreCase(sourceType == null ? null : String.valueOf(sourceType));
    }

    private org.springframework.ai.vectorstore.filter.Filter.Expression userDocumentFilter(UUID ownerUserId) {
        FilterExpressionBuilder filter = new FilterExpressionBuilder();
        return filter.and(
                filter.eq(MetadataKeys.OWNER_USER_ID, ownerUserId.toString()),
                filter.ne(MetadataKeys.SOURCE_TYPE, MetadataKeys.SOURCE_TYPE_REVIEW)
        ).build();
    }

    /**
     * 优先使用向量库返回的相似度分数，缺失时按召回排序补评分。
     *
     * @param vectorScore 向量库相似度分数
     * @param index 当前召回位置
     * @param total 召回总数
     * @return 用于排序的向量侧分数
     */
    private double vectorRankContribution(Double vectorScore, int index, int total) {
        if (vectorScore != null && vectorScore > 0) {
            return vectorScore;
        }
        return total <= 0 ? 0.0 : (double) (total - index) / total;
    }

    /**
     * 从元数据中解析整数值，解析失败时回退到默认值。
     *
     * @param metadata 元数据映射
     * @param key 目标字段名
     * @param defaultValue 默认值
     * @return 解析出的整数结果
     */
    private int intMetadata(Map<String, Object> metadata, String key, int defaultValue) {
        if (metadata == null) {
            return defaultValue;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
