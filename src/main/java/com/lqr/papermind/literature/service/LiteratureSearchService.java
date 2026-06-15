package com.lqr.papermind.literature.service;

import com.lqr.papermind.literature.model.LiteratureSearchRequest;
import com.lqr.papermind.literature.model.LiteratureSearchResponse;

/**
 * 文献搜索业务入口。
 */
public interface LiteratureSearchService {

    /**
     * 执行文献搜索，负责参数规范化、缓存命中、缓存重建保护和外部源兜底。
     *
     * @param request 文献搜索请求
     * @return 文献搜索响应
     */
    LiteratureSearchResponse search(LiteratureSearchRequest request);
}
