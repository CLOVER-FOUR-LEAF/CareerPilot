package com.careerpilot.aitaskscheduler.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;

/**
 * 调 Agent 用的共享 WebClient。
 *
 * <p>单例复用连接池：每次 {@code WebClient.create()} 都会新建一套 Netty 连接，
 * 高并发下会把编排服务打挂。
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                // 读超时留足：Agent 全流程（含语义相似度计算）可能跑很久，
                // 真正的兜底是 AiTaskService 里 block(120s) 的超时，两者要一致地宽松。
                .responseTimeout(java.time.Duration.ofMinutes(5))
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(300)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // Agent 返回的 JD 全文 / 简历全文可能很大，默认 256KB 会直接爆
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
}
