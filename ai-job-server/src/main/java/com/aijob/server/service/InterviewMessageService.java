package com.aijob.server.service;

import com.aijob.server.entity.InterviewMessage;

import java.util.List;

public interface InterviewMessageService {

    InterviewMessage save(Long interviewId, String role, String content);

    List<InterviewMessage> listByInterviewId(Long interviewId);

    void deleteByInterviewId(Long interviewId);
}
