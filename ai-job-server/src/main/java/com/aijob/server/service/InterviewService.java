package com.aijob.server.service;

import com.aijob.server.entity.Interview;
import com.aijob.server.entity.InterviewMessage;
import com.aijob.server.entity.InterviewReport;
import com.aijob.server.vo.InterviewAnswerVO;
import com.aijob.server.vo.InterviewStartVO;

import java.util.List;

public interface InterviewService {

    Interview create(Long userId, Long resumeId, String position);

    List<Interview> listByUserId(Long userId);

    Interview getById(Long id, Long userId);

    List<InterviewMessage> listMessages(Long id, Long userId);

    InterviewReport getReport(Long id, Long userId);

    InterviewStartVO start(Long id, Long userId);

    InterviewAnswerVO answer(Long id, Long userId, String answer);

    InterviewReport finish(Long id, Long userId);

    void delete(Long id, Long userId);
}
