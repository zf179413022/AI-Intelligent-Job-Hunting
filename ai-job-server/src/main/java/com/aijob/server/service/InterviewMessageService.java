package com.aijob.server.service;

import com.aijob.server.entity.InterviewMessage;

import java.util.List;

public interface InterviewMessageService {

    InterviewMessage save(Long interviewId, String role, String content);

    InterviewMessage save(Long interviewId, String role, String content, String routeAction, String toolStatus);

    InterviewMessage findById(Long interviewId, Long messageId);

    List<InterviewMessage> listByInterviewId(Long interviewId);

    void deleteByInterviewId(Long interviewId);
}
