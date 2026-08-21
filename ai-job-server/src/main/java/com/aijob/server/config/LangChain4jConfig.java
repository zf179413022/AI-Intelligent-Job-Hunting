package com.aijob.server.config;

import com.aijob.server.ai.KnowledgeChatAssistant;
import com.aijob.server.ai.KnowledgeRagAssistant;
import com.aijob.server.ai.KnowledgeStreamingRagAssistant;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaApiVersion;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    static {
        // Chroma / OpenAI 客户端：Spring Boot 环境优先使用 Spring RestClient
        if (System.getProperty("langchain4j.http.clientBuilderFactory") == null) {
            System.setProperty(
                    "langchain4j.http.clientBuilderFactory",
                    "dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilderFactory"
            );
        }
    }

    @Bean
    public KnowledgeChatAssistant knowledgeChatAssistant(ChatModel chatModel) {
        return AiServices.builder(KnowledgeChatAssistant.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    public KnowledgeRagAssistant knowledgeRagAssistant(ChatModel chatModel) {
        return AiServices.builder(KnowledgeRagAssistant.class)
                .chatModel(chatModel)
                .build();
    }

    /**
     * RAG 5.9：流式 AiServices（需配置 langchain4j.open-ai.streaming-chat-model.*）。
     */
    @Bean
    public KnowledgeStreamingRagAssistant knowledgeStreamingRagAssistant(
            StreamingChatModel streamingChatModel) {
        return AiServices.builder(KnowledgeStreamingRagAssistant.class)
                .streamingChatModel(streamingChatModel)
                .build();
    }

    /**
     * 本地 ONNX Embedding（All-MiniLM-L6-v2 quantized，384 维）。
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    /**
     * LangChain4j Chroma 客户端（连接 Docker 持久化 Chroma HTTP 服务）。
     */
    @Bean
    public EmbeddingStore<TextSegment> knowledgeEmbeddingStore(
            @Value("${knowledge.chroma.base-url}") String baseUrl,
            @Value("${knowledge.chroma.collection-name}") String collectionName,
            @Value("${knowledge.chroma.timeout-seconds:60}") long timeoutSeconds) {
        return ChromaEmbeddingStore.builder()
                .apiVersion(ChromaApiVersion.V2)
                .baseUrl(baseUrl)
                .tenantName("default_tenant")
                .databaseName("default_database")
                .collectionName(collectionName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
