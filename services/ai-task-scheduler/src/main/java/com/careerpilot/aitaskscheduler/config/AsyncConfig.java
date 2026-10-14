package com.careerpilot.aitaskscheduler.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 任务执行线程池。
 *
 * <p>原先用的是 {@code new Thread(...).start()}：每次建任务都新建一条裸线程，
 * 既不受控也不复用，高并发下会直接打爆线程数。任务本身是 IO 密集
 * （绝大部分时间在等 Agent 的 HTTP 响应），所以线程数可以显著大于 CPU 核数。
 */
@Slf4j
@Configuration
public class AsyncConfig {

    public static final String AI_TASK_EXECUTOR = "aiTaskExecutor";

    @Bean(name = AI_TASK_EXECUTOR)
    public TaskExecutor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(256);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ai-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // 队列满了不丢任务，直接让调用线程自己跑，保证任务一定会被执行
        RejectedExecutionHandler handler = (r, pool) -> {
            log.warn("AI 任务线程池饱和（active={}, queue={}），回退到调用线程执行",
                    pool.getActiveCount(), pool.getQueue().size());
            r.run();
        };
        executor.setRejectedExecutionHandler(handler);

        executor.initialize();
        return executor;
    }

    /**
     * SSE 用的调度线程池，与任务执行池隔离 —— 否则慢任务会把事件推送饿死。
     */
    @Bean(name = "sseScheduler")
    public TaskExecutor sseScheduler() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("ai-sse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
