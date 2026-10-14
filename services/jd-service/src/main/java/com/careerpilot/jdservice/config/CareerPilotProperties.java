package com.careerpilot.jdservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部依赖地址，全部支持环境变量覆盖。
 */
@Data
@Component
@ConfigurationProperties(prefix = "careerpilot")
public class CareerPilotProperties {

    /** AI 任务调度服务地址。 */
    private String aiSchedulerUrl = "http://localhost:8085";

    /** 建任务超时（秒）。这里只是登记任务，正常应该毫秒级返回。 */
    private int createTimeoutSeconds = 10;
}
