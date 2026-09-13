package com.aijob.server.vo;

import com.aijob.server.entity.Interview;
import com.aijob.server.entity.InterviewMessage;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InterviewAnswerVO {

    private Interview interview;

    private String evaluation;

    private InterviewMessage nextQuestionMessage;

    private Boolean shouldContinue;
}
