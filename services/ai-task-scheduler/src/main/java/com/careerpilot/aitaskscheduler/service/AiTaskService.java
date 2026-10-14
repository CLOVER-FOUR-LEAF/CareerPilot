package com.careerpilot.aitaskscheduler.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerpilot.aitaskscheduler.config.AgentProperties;
import com.careerpilot.aitaskscheduler.config.AsyncConfig;
import com.careerpilot.aitaskscheduler.dto.CallbackPayload;
import com.careerpilot.aitaskscheduler.dto.CreateTaskRequest;
import com.careerpilot.aitaskscheduler.dto.TaskVO;
import com.careerpilot.aitaskscheduler.entity.AgentInvocation;
import com.careerpilot.aitaskscheduler.entity.AiTask;
import com.careerpilot.aitaskscheduler.mapper.AgentInvocationMapper;
import com.careerpilot.aitaskscheduler.mapper.AiTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 任务调度。
 *
 * <p>职责边界很窄，只做三件事：
 * <ol>
 *   <li>把任务落库并异步执行（线程池，不是裸线程）</li>
 *   <li>调用 LangGraph 编排服务，把 Agent 结果落回 {@code t_ai_task.output_payload}</li>
 *   <li>把结果回调给发起方（jd/resume/match 服务），由它们写各自的业务表</li>
 * </ol>
 * 业务规则一律不放在这里 —— 调度服务不知道 JD 是什么。
 */
@Slf4j
@Service
public class AiTaskService {

    /** SSE 轮询间隔。500ms 是"看起来像流式"和"不把 DB 打穿"之间的平衡点。 */
    private static final long POLL_INTERVAL_MS = 500L;

    /** 单条 SSE 连接最长保持时间，防止任务卡死时连接永不释放。 */
    private static final long MAX_STREAM_MS = 15 * 60 * 1000L;

    private final AiTaskMapper aiTaskMapper;
    private final AgentInvocationMapper agentInvocationMapper;
    private final WebClient webClient;
    private final AgentProperties properties;
    private final TaskExecutor aiTaskExecutor;
    private final TaskExecutor sseScheduler;

    /** 同一任务内 Agent 调用序号。 */
    private final AtomicInteger invocationSeq = new AtomicInteger();

    public AiTaskService(AiTaskMapper aiTaskMapper,
                         AgentInvocationMapper agentInvocationMapper,
                         WebClient webClient,
                         AgentProperties properties,
                         @Qualifier(AsyncConfig.AI_TASK_EXECUTOR) TaskExecutor aiTaskExecutor,
                         @Qualifier("sseScheduler") TaskExecutor sseScheduler) {
        this.aiTaskMapper = aiTaskMapper;
        this.agentInvocationMapper = agentInvocationMapper;
        this.webClient = webClient;
        this.properties = properties;
        this.aiTaskExecutor = aiTaskExecutor;
        this.sseScheduler = sseScheduler;
    }

    // ── 创建 ────────────────────────────────────────────────────

    public AiTask createTask(CreateTaskRequest req, String userId) {
        String taskType = req.getTaskType();
        if (!properties.isSupported(taskType)) {
            throw new UnsupportedTaskTypeException(taskType);
        }

        String inputJson = JSONUtil.toJsonStr(req.getInput() == null ? Map.of() : req.getInput());

        AiTask task = new AiTask();
        task.setTaskId("task_" + IdUtil.fastSimpleUUID().substring(0, 16));
        task.setUserId(userId);
        task.setTaskType(taskType);
        task.setBizId(req.getBizId());
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setCurrentAgent(properties.getAgentNames().get(taskType));
        task.setInputPayload(inputJson);
        task.setTraceId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        task.setRetryCount(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        aiTaskMapper.insert(task);

        // 只提交任务 ID，线程体里重新查库，避免共享可变实体
        String taskId = task.getTaskId();
        aiTaskExecutor.execute(() -> executeSafely(taskId));

        return task;
    }

    // ── 执行 ────────────────────────────────────────────────────

    private void executeSafely(String taskId) {
        try {
            execute(taskId);
        } catch (Exception e) {
            // 兜底：execute 内部已尽量收敛异常，这里再兜一层，避免线程静默死掉后任务永远 PENDING
            log.error("AI 任务执行异常且未能正确落状态: taskId={}", taskId, e);
            markFailed(taskId, "任务执行内部异常: " + e.getMessage());
        }
    }

    private void execute(String taskId) {
        AiTask task = getTask(taskId);
        if (task == null) {
            log.warn("任务不存在，跳过执行: {}", taskId);
            return;
        }

        long startMs = System.currentTimeMillis();
        AgentInvocation invocation = beginInvocation(task);

        updateStatus(task, "RUNNING", 10, LocalDateTime.now());

        try {
            String outputJson = callOrchestrator(task);
            int latency = (int) (System.currentTimeMillis() - startMs);

            finishInvocation(invocation, "SUCCESS", outputJson, latency, null);

            AiTask done = new AiTask();
            done.setTaskId(taskId);
            done.setStatus("SUCCESS");
            done.setProgress(100);
            done.setOutputPayload(outputJson);
            done.setDurationMs(latency);
            done.setCompletedAt(LocalDateTime.now());
            done.setUpdatedAt(LocalDateTime.now());
            aiTaskMapper.updateById(done);

            notifyCallback(task, true, outputJson, null, latency);

        } catch (Exception e) {
            int latency = (int) (System.currentTimeMillis() - startMs);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("AI 任务失败: taskId={}, type={}, err={}", taskId, task.getTaskType(), message);

            finishInvocation(invocation, "FAILED", null, latency, message);
            markFailed(taskId, message);

            notifyCallback(task, false, null, message, latency);
        }
    }

    /**
     * 调 LangGraph 编排服务。
     *
     * <p>返回的是 Agent 的<b>原始输出</b>字符串，不额外包一层 —— 契约要求
     * 回调方能直接把它落进自己的 JSON 列。
     */
    private String callOrchestrator(AiTask task) {
        Map<String, Object> body = Map.of(
                "task_type", task.getTaskType(),
                "input_data", parseInput(task.getInputPayload()),
                "user_id", task.getUserId() == null ? "" : task.getUserId()
        );

        String response = webClient.post()
                .uri(properties.getOrchestratorUrl() + "/api/orchestrate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                // Agent 返回 4xx/5xx 时不要抛默认异常，要把响应体带回来做诊断
                .onStatus(status -> status.isError(), clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("{}")
                                .map(bodyText -> new IllegalStateException(
                                        "编排服务返回 " + clientResponse.statusCode().value() + ": " + bodyText)))
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(properties.getTimeoutSeconds()));

        if (response == null || response.isBlank()) {
            throw new IllegalStateException("编排服务无响应");
        }

        var parsed = JSONUtil.parseObj(response);
        String status = parsed.getStr("status", "");
        if ("failed".equalsIgnoreCase(status)) {
            throw new IllegalStateException("编排执行失败: " + parsed.getStr("error", "未知错误"));
        }
        if (!"completed".equalsIgnoreCase(status)) {
            throw new IllegalStateException("编排返回异常状态: " + status);
        }

        Object result = parsed.get("result");
        return result == null ? "{}" : JSONUtil.toJsonStr(result);
    }

    // ── Agent 调用日志 ──────────────────────────────────────────

    private AgentInvocation beginInvocation(AiTask task) {
        AgentInvocation inv = new AgentInvocation();
        inv.setInvocationId("inv_" + IdUtil.fastSimpleUUID().substring(0, 16));
        inv.setTaskId(task.getTaskId());
        inv.setAgentName(task.getCurrentAgent());
        inv.setInvocationNo(invocationSeq.incrementAndGet());
        inv.setStatus("RUNNING");
        inv.setInputPayload(task.getInputPayload());
        inv.setRetryAttempt(task.getRetryCount() == null ? 0 : task.getRetryCount());
        inv.setStartedAt(LocalDateTime.now());
        inv.setCreatedAt(LocalDateTime.now());
        try {
            agentInvocationMapper.insert(inv);
        } catch (Exception e) {
            // 调用日志是审计信息，写不进去不应该让业务任务失败
            log.warn("Agent 调用日志写入失败: taskId={}", task.getTaskId(), e);
        }
        return inv;
    }

    private void finishInvocation(AgentInvocation inv, String status, String output,
                                  int latencyMs, String errorMessage) {
        if (inv == null || inv.getId() == null) {
            return;
        }
        try {
            AgentInvocation patch = new AgentInvocation();
            patch.setId(inv.getId());
            patch.setStatus(status);
            patch.setOutputPayload(output);
            patch.setDurationMs(latencyMs);
            patch.setErrorMessage(errorMessage);
            patch.setCompletedAt(LocalDateTime.now());
            agentInvocationMapper.updateById(patch);
        } catch (Exception e) {
            log.warn("Agent 调用日志更新失败: invocationId={}", inv.getInvocationId(), e);
        }
    }

    // ── 回调 ────────────────────────────────────────────────────

    private void notifyCallback(AiTask task, boolean success, String output, String error, int durationMs) {
        String callbackUrl = properties.getCallbacks().get(task.getTaskType());
        if (callbackUrl == null || callbackUrl.isBlank()) {
            // 没配回调的任务类型（简历优化/面试/求职信/职业规划），
            // 结果留在 t_ai_task.output_payload，由前端按 taskId 自己取
            return;
        }

        CallbackPayload payload = new CallbackPayload(
                task.getTaskId(),
                task.getBizId(),
                success,
                success ? parseInput(output) : null,
                error,
                durationMs
        );

        try {
            webClient.post()
                    .uri(callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(15));
            log.info("回调完成: taskId={}, url={}", task.getTaskId(), callbackUrl);
        } catch (Exception e) {
            // 回调失败不回滚任务状态：任务本身已经成功，业务侧可重新拉取 output 补偿
            log.error("回调失败: taskId={}, url={}, err={}", task.getTaskId(), callbackUrl, e.getMessage());
        }
    }

    // ── 查询 ────────────────────────────────────────────────────

    public AiTask getTask(String taskId) {
        return aiTaskMapper.selectOne(
                new LambdaQueryWrapper<AiTask>().eq(AiTask::getTaskId, taskId));
    }

    public AiTask getOwnedTask(String taskId, String userId) {
        AiTask task = getTask(taskId);
        if (task == null) {
            return null;
        }
        if (userId != null && !userId.equals(task.getUserId())) {
            return null;
        }
        return task;
    }

    public List<AiTask> listTasks(String userId, int limit) {
        LambdaQueryWrapper<AiTask> wrapper = new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getUserId, userId)
                .orderByDesc(AiTask::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        return aiTaskMapper.selectList(wrapper);
    }

    /**
     * SSE 进度流：按固定间隔轮询任务表，状态变化就推一条，
     * 直到进入终态才关闭连接。
     *
     * <p>之前这里只吐一条快照就 {@code sink.complete()}，前端拿到的永远是一帧，
     * 拿不到真正的执行进度。
     */
    public Flux<ServerSentEvent<String>> streamTask(String taskId) {
        if (getTask(taskId) == null) {
            return Flux.error(new TaskNotFoundException(taskId));
        }

        long deadline = System.currentTimeMillis() + MAX_STREAM_MS;

        Flux<AiTask> polled = Flux.interval(
                        Duration.ZERO, Duration.ofMillis(POLL_INTERVAL_MS),
                        // 用独立的 SSE 线程池，避免慢任务把事件推送饿死
                        Schedulers.fromExecutor(sseScheduler))
                .map(tick -> getTask(taskId))
                .filter(Objects::nonNull);

        /*
         * 拆成「非终止快照流」+「最终终止事件」两段，而不是直接 takeUntil：
         * 那样在任务已经终结、或轮询超时时，连接会一条终止事件都不发就关掉，
         * 前端只能靠超时兜底。这里无论如何都保证补发一条 done / error。
         */
        return polled
                .filter(task -> !task.isTerminal() && System.currentTimeMillis() <= deadline)
                .map(this::toSseEvent)
                .concatWith(Mono.defer(() -> {
                    AiTask last = getTask(taskId);
                    if (last == null || !last.isTerminal()) {
                        return Mono.empty();
                    }
                    return Mono.just(toSseEvent(last));
                }));
    }

    private ServerSentEvent<String> toSseEvent(AiTask task) {
        if (!task.isTerminal()) {
            TaskVO.Progress p = new TaskVO.Progress(
                    task.getTaskId(), task.getStatus(),
                    task.getProgress() == null ? 0 : task.getProgress(),
                    task.getCurrentAgent());
            return ServerSentEvent.builder(JSONUtil.toJsonStr(p))
                    .event("snapshot")
                    .build();
        }

        if ("SUCCESS".equals(task.getStatus())) {
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("taskId", task.getTaskId());
            data.put("status", task.getStatus());
            data.put("progress", 100);
            data.put("output", parseInput(task.getOutputPayload()));
            return ServerSentEvent.builder(JSONUtil.toJsonStr(data))
                    .event("done")
                    .build();
        }

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("status", task.getStatus());
        data.put("errorMessage", task.getErrorMessage());
        return ServerSentEvent.builder(JSONUtil.toJsonStr(data))
                .event("error")
                .build();
    }

    // ── 内部工具 ────────────────────────────────────────────────

    private void updateStatus(AiTask task, String status, int progress, LocalDateTime startedAt) {
        AiTask patch = new AiTask();
        patch.setTaskId(task.getTaskId());
        patch.setStatus(status);
        patch.setProgress(progress);
        patch.setCurrentAgent(task.getCurrentAgent());
        if (startedAt != null) {
            patch.setStartedAt(startedAt);
        }
        patch.setUpdatedAt(LocalDateTime.now());
        aiTaskMapper.updateById(patch);
    }

    private void markFailed(String taskId, String message) {
        AiTask patch = new AiTask();
        patch.setTaskId(taskId);
        patch.setStatus("FAILED");
        patch.setErrorMessage(truncate(message, 2000));
        patch.setCompletedAt(LocalDateTime.now());
        patch.setUpdatedAt(LocalDateTime.now());
        aiTaskMapper.updateById(patch);
    }

    /** 空串 / 非法 JSON 都要能安全返回空 Map，否则回调写库会炸。 */
    private static Map<String, Object> parseInput(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSONUtil.parseObj(json).toBean(Map.class);
        } catch (Exception e) {
            return Map.of("_raw", json);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ── 异常 ────────────────────────────────────────────────────

    public static class TaskNotFoundException extends RuntimeException {
        public TaskNotFoundException(String taskId) {
            super("任务不存在: " + taskId);
        }
    }

    public static class UnsupportedTaskTypeException extends RuntimeException {
        public UnsupportedTaskTypeException(String taskType) {
            super("不支持的任务类型: " + taskType);
        }
    }
}
