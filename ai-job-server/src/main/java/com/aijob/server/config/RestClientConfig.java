package com.aijob.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class RestClientConfig {

    /**
     * SSE 发送留在平台线程。JDK 21 上 ResponseBodyEmitter 里的 synchronized 可能钉住虚拟线程。
     * 面试流式的模型读取放到虚拟线程，事件仍由这个池子里的同一次任务按顺序写回 SseEmitter。
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService sseExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("interview-sse-" + t.threadId());
            t.setDaemon(true);
            return t;
        });
    }
}
