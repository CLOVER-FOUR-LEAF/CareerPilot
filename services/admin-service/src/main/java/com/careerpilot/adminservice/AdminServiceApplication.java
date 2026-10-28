package com.careerpilot.adminservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 平台管理端。
 *
 * <p>只服务 ADMIN 角色，负责运营概览、用户与角色、平台配置、服务健康。
 * 业务能力仍然留在各自的服务里 —— 管理端不复制一份业务逻辑，
 * 否则两边会慢慢长歪。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}
