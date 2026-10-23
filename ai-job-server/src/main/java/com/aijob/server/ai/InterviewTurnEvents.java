package com.aijob.server.ai;

/**
 * 一轮面试执行过程中的事件。同步接口不传，流式接口用来写 SSE。
 * 这里只带工具名、状态和路由动作，不带 query、userId、Prompt 或检索正文。
 */
public interface InterviewTurnEvents {

    void onTool(String name, String status);

    void onRoute(String action);

    void onDelta(String content);
}
