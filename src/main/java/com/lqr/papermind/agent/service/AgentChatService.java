package com.lqr.papermind.agent.service;

import com.lqr.papermind.agent.dto.AgentAskRequest;
import com.lqr.papermind.agent.dto.AgentStreamEvent;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * 智能体问答应用服务。
 */
public interface AgentChatService {

    /**
     * 以流式事件形式执行一次智能体问答，并负责会话创建、消息持久化和最终回答落库。
     *
     * @param ownerUserId 当前用户标识
     * @param request     智能体问答请求
     * @return 智能体流式事件
     */
    Flux<AgentStreamEvent> streamAnswer(UUID ownerUserId, AgentAskRequest request);
}
