package com.careerpilot.resumeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部依赖与存储配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "careerpilot")
public class CareerPilotProperties {

    /** AI 任务调度服务地址。 */
    private String aiSchedulerUrl = "http://localhost:8085";

    /** 简历文件存储根目录（相对路径按进程工作目录解析）。 */
    private String storageRoot = "./data/uploads";

    /** 上传体积上限（字节）。超过直接拒，避免大文件把内存打满。 */
    private long maxFileSize = 10L * 1024 * 1024;
}
