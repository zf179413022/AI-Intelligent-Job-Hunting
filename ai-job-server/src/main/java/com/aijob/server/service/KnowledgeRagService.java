package com.aijob.server.service;

import com.aijob.server.dto.KnowledgeAskRequest;
import com.aijob.server.vo.KnowledgeAskVO;
import com.aijob.server.vo.KnowledgeRetrieveVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface KnowledgeRagService {

    /** 5.6.1：仅 Top-K 检索 */
    KnowledgeRetrieveVO retrieve(KnowledgeAskRequest request, Long userId);

    /** 5.6：检索 + Context + DeepSeek + Sources（同步） */
    KnowledgeAskVO ask(KnowledgeAskRequest request, Long userId);

    /**
     * 5.9：SSE 流式 RAG。
     * event: meta | delta | done | error
     */
    void askStream(KnowledgeAskRequest request, Long userId, SseEmitter emitter);
}
