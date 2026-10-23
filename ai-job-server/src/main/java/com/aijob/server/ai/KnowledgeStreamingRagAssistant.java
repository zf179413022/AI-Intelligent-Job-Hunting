package com.aijob.server.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * RAG 流式助手（5.9）：返回 TokenStream，由 StreamingChatModel → DeepSeek 驱动。
 */
public interface KnowledgeStreamingRagAssistant {

    @SystemMessage("""
            你是求职知识库问答助手。请严格根据用户消息中的【检索到的资料】回答问题。
            规则：
            1. 优先使用资料中的信息，可归纳整理，但不要编造资料中不存在的事实。
            2. 若资料不足以回答，请明确说明“根据当前知识库资料无法确定”，不要猜测。
            3. 回答使用简洁中文，必要时分点列出。
            4. 不要输出与问题无关的开场白。
            """)
    TokenStream answer(@UserMessage String userMessage);
}
