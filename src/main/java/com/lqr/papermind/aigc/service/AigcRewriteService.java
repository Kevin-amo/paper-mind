package com.lqr.papermind.aigc.service;

import com.lqr.papermind.aigc.dto.AigcRewriteRequest;
import com.lqr.papermind.aigc.dto.AigcRewriteResponse;

/**
 * 段落学术润色服务。
 */
public interface AigcRewriteService {

    /**
     * 对段落进行学术润色，降低机械化痕迹。
     *
     * @param request 润色请求
     * @return 润色结果
     */
    AigcRewriteResponse rewrite(AigcRewriteRequest request);
}
