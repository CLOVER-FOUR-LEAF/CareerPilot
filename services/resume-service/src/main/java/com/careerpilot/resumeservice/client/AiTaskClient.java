package com.careerpilot.resumeservice.client;

import com.careerpilot.resumeservice.config.CareerPilotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

/**
 * 调 ai-task-scheduler 登记简历分析任务。
 *
 * <p>失败不抛：文件已经落库、正文已经入库，用户至少能在页面上看到简历本身，
 * 之后再点「重新分析」重试即可。不能让调度服务的一次抖动把整个上传功能废掉。
 */
@Slf4j
@Component
public class AiTaskClient {

    private final RestClient restClient;
    private final CareerPilotProperties properties;

    public AiTaskClient(RestClient.Builder builder, CareerPilotProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    /**
     * @return taskId；登记失败返回 null
     */
    public String submitResumeAnalyze(String userId, String resumeBizId, String versionId, String text) {
        Map<String, Object> input = new HashMap<>();
        input.put("resume_id", resumeBizId);
        input.put("version_id", versionId);
        input.put("resume_text", text);

        Map<String, Object> body = new HashMap<>();
        body.put("taskType", "resume_analyze");
        body.put("bizId", resumeBizId);
        body.put("input", input);

        try {
            Map<?, ?> resp = restClient.post()
                    .uri(properties.getAiSchedulerUrl() + "/api/v1/ai-tasks")
                    .header("X-User-Id", userId)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (resp == null) {
                return null;
            }
            Object data = resp.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                Object taskId = dataMap.get("taskId");
                return taskId == null ? null : String.valueOf(taskId);
            }
            return null;
        } catch (RestClientException e) {
            log.error("登记简历分析任务失败: resumeId={}, err={}", resumeBizId, e.getMessage());
            return null;
        }
    }
}
