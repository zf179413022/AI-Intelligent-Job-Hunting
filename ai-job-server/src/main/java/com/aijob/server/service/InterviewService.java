package com.aijob.server.service;

import com.aijob.server.entity.Interview;
import com.aijob.server.entity.InterviewMessage;
import com.aijob.server.entity.InterviewReport;
import com.aijob.server.vo.InterviewAnswerVO;
import com.aijob.server.vo.InterviewStartVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface InterviewService {

    Interview create(Long userId, Long resumeId, String position);

    List<Interview> listByUserId(Long userId);

    Interview getById(Long id, Long userId);

    List<InterviewMessage> listMessages(Long id, Long userId);

    InterviewReport getReport(Long id, Long userId);

    InterviewStartVO start(Long id, Long userId);

    InterviewAnswerVO answer(Long id, Long userId, String answer);

    /**
     * SSE 流式回答：delta 推送文本，done 时一次性落库 + 刷新 Redis。
     */
    void answerStream(Long id, Long userId, String answer, SseEmitter emitter);

    InterviewReport finish(Long id, Long userId);

    void delete(Long id, Long userId);
}
