package com.careerpilot.adminservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端「服务健康」页的探测目标。
 *
 * <p>这一版刻意不引入注册中心：探测地址写在配置里，改端口只改一处。
 * 等服务发现真正落地（Nacos）后，这里换成从注册表拉实例即可，
 * 控制器接口不用变。
 */
@Data
@ConfigurationProperties(prefix = "platform")
public class ProbeProperties {

    private List<Probe> probes = new ArrayList<>();

    @Data
    public static class Probe {
        /** 展示名，如 match-agent */
        private String name;
        /** 分组：java / agent / gateway */
        private String group;
        /** 健康检查地址 */
        private String url;
    }
}
