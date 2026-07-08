package com.lqr.papermind.document.structured.service.impl;

import com.lqr.papermind.document.event.DocumentIndexedEvent;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.structured.service.PaperStructuredParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文档索引完成后的结构化解析入口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredParseDocumentIndexedListener {

    private final PaperStructuredParseService paperStructuredParseService;
    private final DocumentPersistenceService documentPersistenceService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentIndexed(DocumentIndexedEvent event) {
        try {
            if (documentPersistenceService.findReviewDocument(event.ownerUserId(), event.sourceId()).isEmpty()) {
                return;
            }
            paperStructuredParseService.generate(event.ownerUserId(), event.sourceId());
        } catch (RuntimeException ex) {
            log.warn("论文结构化解析监听器失败 ownerUserId={} sourceId={}", event.ownerUserId(), event.sourceId(), ex);
        }
    }
}
