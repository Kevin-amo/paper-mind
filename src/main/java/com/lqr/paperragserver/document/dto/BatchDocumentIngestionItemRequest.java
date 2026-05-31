package com.lqr.paperragserver.document.dto;

/**
 * 批量文档入库的单个文件请求项。
 */
public record BatchDocumentIngestionItemRequest(String fileName, String sourceId, String title) {
}