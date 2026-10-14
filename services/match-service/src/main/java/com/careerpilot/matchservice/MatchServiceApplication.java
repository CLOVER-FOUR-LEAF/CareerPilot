package com.careerpilot.matchservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.careerpilot.matchservice.mapper")
public class MatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchServiceApplication.class, args);
    }
}
