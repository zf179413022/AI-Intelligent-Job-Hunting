package com.aijob.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.aijob.server.mapper")
public class AiJobServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiJobServerApplication.class, args);
    }

}
