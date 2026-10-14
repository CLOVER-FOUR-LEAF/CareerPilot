package com.careerpilot.resumeservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.careerpilot.resumeservice.mapper")
public class ResumeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResumeServiceApplication.class, args);
    }
}
