package com.lqr.papermind.document.structured.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.document.entity.DocumentEntity;
import com.lqr.papermind.document.mapper.DocumentMapper;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.structured.entity.PaperStructuredParseEntity;
import com.lqr.papermind.document.structured.mapper.PaperStructuredParseMapper;
import com.lqr.papermind.document.structured.model.ModelCompletionResult;
import com.lqr.papermind.document.structured.model.StructuredParseResult;
import com.lqr.papermind.document.structured.service.PaperSectionRuleParser;
import com.lqr.papermind.document.structured.service.PaperStructuredMergePolicy;
import com.lqr.papermind.document.structured.service.PaperStructuredModelCompleter;
import com.lqr.papermind.document.structured.service.PaperStructuredParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

/**
 * 论文结构化解析编排服务默认实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperStructuredParseServiceImpl implements PaperStructuredParseService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 4000;

    private final PaperStructuredParseMapper structuredParseMapper;
    private final DocumentMapper documentMapper;
    private final DocumentPersistenceService documentPersistenceService;
    private final PaperSectionRuleParser ruleParser;
    private final PaperStructuredModelCompleter modelCompleter;
    private final PaperStructuredMergePolicy mergePolicy;
    private final ObjectMapper objectMapper;

    /**
     * 根据用户ID和来源ID查询论文结构化解析实体。
     *
     * @param ownerUserId 用户ID
     * @param sourceId    来源ID
     * @return 论文结构化解析实体（如果存在）
     */
    @Override
    public Optional<PaperStructuredParseEntity> find(UUID ownerUserId, String sourceId) {
        return Optional.ofNullable(structuredParseMapper.selectOne(new LambdaQueryWrapper<PaperStructuredParseEntity>()
                .eq(PaperStructuredParseEntity::getOwnerUserId, ownerUserId)
                .eq(PaperStructuredParseEntity::getSourceId, sourceId)));
    }

    /**
     * 生成论文结构化解析结果。
     *
     * @param ownerUserId 用户ID
     * @param sourceId    来源ID
     * @return 论文结构化解析实体
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaperStructuredParseEntity generate(UUID ownerUserId, String sourceId) {
        return run(ownerUserId, sourceId);
    }

    /**
     * 重新生成论文结构化解析结果。
     *
     * @param ownerUserId 用户ID
     * @param sourceId    来源ID
     * @return 论文结构化解析实体
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaperStructuredParseEntity regenerate(UUID ownerUserId, String sourceId) {
        return run(ownerUserId, sourceId);
    }

    /**
     * 执行论文结构化解析的核心逻辑。
     *
     * @param ownerUserId 用户ID
     * @param sourceId    来源ID
     * @return 论文结构化解析实体
     */
    private PaperStructuredParseEntity run(UUID ownerUserId, String sourceId) {
        DocumentPersistenceService.DocumentDetail document = documentPersistenceService.findAnyDocument(ownerUserId, sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
        DocumentEntity entity = requireDocumentEntity(ownerUserId, sourceId);
        String rawText = document.contentText() == null ? "" : document.contentText();
        try {
            StructuredParseResult ruleResult = ruleParser.parse(document);
            ModelCompletionResult modelCompletion = modelCompleter.complete(document, ruleResult);
            StructuredParseResult mergedResult = mergePolicy.merge(ruleResult, modelCompletion.result());
            String status = modelCompletion.errorMessage() == null ? "COMPLETED" : "RULE_PARSED";
            structuredParseMapper.upsertResult(
                    UUID.randomUUID(),
                    ownerUserId,
                    entity.getId(),
                    sourceId,
                    rawText,
                    json(ruleResult.content()),
                    json(modelCompletion.result().content()),
                    json(mergedResult.content()),
                    json(mergedResult.missingFields()),
                    modelCompletion.rawModelOutput(),
                    status,
                    cut(modelCompletion.errorMessage())
            );
            return find(ownerUserId, sourceId).orElseThrow(() -> new IllegalStateException("结构化解析结果保存失败"));
        } catch (RuntimeException ex) {
            structuredParseMapper.upsertFailed(UUID.randomUUID(), ownerUserId, entity.getId(), sourceId, rawText, cut(ex.getMessage()));
            log.warn("论文结构化解析失败 ownerUserId={} sourceId={}", ownerUserId, sourceId, ex);
            return find(ownerUserId, sourceId).orElseThrow(() -> ex);
        }
    }

    /**
     * 获取文档实体，如果不存在则抛出异常。
     *
     * @param ownerUserId 用户ID
     * @param sourceId    来源ID
     * @return 文档实体
     */
    private DocumentEntity requireDocumentEntity(UUID ownerUserId, String sourceId) {
        DocumentEntity entity = documentMapper.selectOne(new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getOwnerUserId, ownerUserId)
                .eq(DocumentEntity::getSourceId, sourceId));
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在");
        }
        return entity;
    }

    /**
     * 将对象转换为JSON字符串。
     *
     * @param value 要转换的对象
     * @return JSON字符串
     */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("结构化解析结果 JSON 序列化失败", ex);
        }
    }

    /**
     * 截断字符串到最大长度。
     *
     * @param value 要截断的字符串
     * @return 截断后的字符串
     */
    private String cut(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= ERROR_MESSAGE_MAX_LENGTH ? value : value.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }
}
