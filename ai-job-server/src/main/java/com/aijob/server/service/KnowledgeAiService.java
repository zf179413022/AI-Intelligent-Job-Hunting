package com.aijob.server.service;

public interface KnowledgeAiService {

    /**
     * 同步文本生成：Spring Boot → LangChain4j ChatModel → DeepSeek
     */
    String chat(String prompt);
}
