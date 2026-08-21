package com.aijob.server.service.impl;

import com.aijob.server.entity.InterviewMessage;
import com.aijob.server.mapper.InterviewMessageMapper;
import com.aijob.server.service.InterviewMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewMessageServiceImpl implements InterviewMessageService {

    private final InterviewMessageMapper interviewMessageMapper;

    @Override
    public InterviewMessage save(Long interviewId, String role, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setInterviewId(interviewId);
        message.setRole(role);
        message.setContent(content);
        interviewMessageMapper.insert(message);
        return message;
    }

    @Override
    public List<InterviewMessage> listByInterviewId(Long interviewId) {
        return interviewMessageMapper.selectList(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getInterviewId, interviewId)
                        .orderByAsc(InterviewMessage::getCreatedAt)
                        .orderByAsc(InterviewMessage::getId)
        );
    }

    @Override
    public void deleteByInterviewId(Long interviewId) {
        interviewMessageMapper.delete(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getInterviewId, interviewId)
        );
    }
}
