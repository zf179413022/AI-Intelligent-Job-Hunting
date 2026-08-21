package com.aijob.server.ai;

/**
 * LangChain4j AI Services 接口：由 ChatModel（DeepSeek）驱动。
 */
public interface KnowledgeChatAssistant {

    String chat(String userMessage);
}
