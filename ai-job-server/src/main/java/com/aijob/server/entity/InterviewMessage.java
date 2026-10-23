package com.aijob.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_message")
public class InterviewMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long interviewId;

    /** AI / USER */
    private String role;

    private String content;

    /** 本轮路由：probe / switch / conclude。仅 AI 消息填写，供对账，不送进模型。 */
    private String routeAction;

    /** 知识库工具：NONE / SUCCESS / FAILED */
    private String toolStatus;

    private LocalDateTime createdAt;
}
