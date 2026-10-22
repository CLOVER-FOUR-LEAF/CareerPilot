package com.careerpilot.aitaskscheduler.controller;

import com.careerpilot.aitaskscheduler.dto.ApiResponse;
import com.careerpilot.aitaskscheduler.dto.CreateTaskRequest;
import com.careerpilot.aitaskscheduler.dto.TaskVO;
import com.careerpilot.aitaskscheduler.service.AiTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai-tasks")
@RequiredArgsConstructor
public class AiTaskController {

    private final AiTaskService aiTaskService;

    /** 创建 AI 任务。立即返回，Agent 在后台线程池里跑。 */
    @PostMapping
    public ApiResponse<TaskVO> createTask(@Valid @RequestBody CreateTaskRequest request,
                                          @RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(TaskVO.from(aiTaskService.createTask(request, userId)));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskVO> getTask(@PathVariable("taskId") String taskId,
                                       @RequestHeader("X-User-Id") String userId) {
        var task = aiTaskService.getOwnedTask(taskId, userId);
        if (task == null) {
            return ApiResponse.error(1404, "任务不存在");
        }
        return ApiResponse.success(TaskVO.from(task));
    }

    /** 我的任务列表，供 Dashboard / 任务中心展示。 */
    @GetMapping("/list")
    public ApiResponse<List<TaskVO>> listTasks(@RequestHeader("X-User-Id") String userId,
                                               @RequestParam(value = "limit", defaultValue = "20") int limit) {
        List<TaskVO> list = aiTaskService.listTasks(userId, limit).stream()
                .map(TaskVO::from)
                .toList();
        return ApiResponse.success(list);
    }

    /**
     * SSE 进度流。浏览器用 EventSource 无法带 Authorization 头，
     * 前端改用 fetch + ReadableStream 手工解析 SSE 帧。
     */
    @GetMapping(value = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamTask(@PathVariable("taskId") String taskId,
                                                    @RequestHeader("X-User-Id") String userId) {
        // 先做归属校验，别让别人的任务状态被读出来
        if (aiTaskService.getOwnedTask(taskId, userId) == null) {
            return Flux.error(new AiTaskService.TaskNotFoundException(taskId));
        }
        return aiTaskService.streamTask(taskId);
    }
}
