package com.careerpilot.matchservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上下游依赖地址。
 */
@Data
@Component
@ConfigurationProperties(prefix = "careerpilot")
public class CareerPilotProperties {

    /** AI 任务调度服务地址。 */
    private String aiSchedulerUrl = "http://localhost:8085";

    /** resume-service 内部接口地址。 */
    private String resumeServiceUrl = "http://localhost:8082";

    /** jd-service 内部接口地址。 */
    private String jdServiceUrl = "http://localhost:8083";
}
