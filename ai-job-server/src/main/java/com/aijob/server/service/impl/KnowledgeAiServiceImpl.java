package com.aijob.server.service.impl;

import com.aijob.server.ai.KnowledgeChatAssistant;
import com.aijob.server.service.KnowledgeAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeAiServiceImpl implements KnowledgeAiService {

    private final KnowledgeChatAssistant knowledgeChatAssistant;
    private final String apiKey;

    public KnowledgeAiServiceImpl(
            KnowledgeChatAssistant knowledgeChatAssistant,
            @Value("${langchain4j.open-ai.chat-model.api-key:}") String apiKey) {
        this.knowledgeChatAssistant = knowledgeChatAssistant;
        this.apiKey = apiKey;
    }

    @Override
    public String chat(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw new RuntimeException("prompt 不能为空");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new RuntimeException("未配置 DEEPSEEK_API_KEY（langchain4j.open-ai.chat-model.api-key），无法调用 DeepSeek");
        }

        try {
            String answer = knowledgeChatAssistant.chat(prompt.trim());
            if (!StringUtils.hasText(answer)) {
                throw new RuntimeException("DeepSeek 未返回有效内容");
            }
            return answer.trim();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("LangChain4j 调用 DeepSeek 失败：" + e.getMessage(), e);
        }
    }
}
