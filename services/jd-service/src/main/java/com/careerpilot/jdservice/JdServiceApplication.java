package com.careerpilot.jdservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.careerpilot.jdservice.mapper")
public class JdServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(JdServiceApplication.class, args);
    }
}
