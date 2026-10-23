package com.aijob.server.service.impl;

import com.aijob.server.vo.KnowledgeSourceVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeRagServiceImplTest {

    @Test
    void dropsSourcesWhenTheAnswerCannotBeDetermined() {
        List<KnowledgeSourceVO> retrieved = List.of(
                new KnowledgeSourceVO(1L, "HashMap", 9L, "数组加链表", 0.42)
        );

        List<KnowledgeSourceVO> cited = KnowledgeRagServiceImpl.sourcesToCite(
                "根据当前知识库资料无法确定。",
                retrieved
        );

        assertTrue(cited.isEmpty());
    }

    @Test
    void keepsSourcesWhenTheAnswerUsesTheMaterial() {
        List<KnowledgeSourceVO> retrieved = List.of(
                new KnowledgeSourceVO(1L, "HashMap", 9L, "数组加链表", 0.81)
        );

        List<KnowledgeSourceVO> cited = KnowledgeRagServiceImpl.sourcesToCite(
                "HashMap 的主干是数组加链表。",
                retrieved
        );

        assertEquals(retrieved, cited);
    }
}
