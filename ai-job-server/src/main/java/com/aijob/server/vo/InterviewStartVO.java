package com.aijob.server.vo;

import com.aijob.server.entity.Interview;
import com.aijob.server.entity.InterviewMessage;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InterviewStartVO {

    private Interview interview;

    private InterviewMessage questionMessage;
}
