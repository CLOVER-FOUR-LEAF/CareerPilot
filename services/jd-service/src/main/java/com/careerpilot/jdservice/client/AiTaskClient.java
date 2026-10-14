package com.careerpilot.jdservice.client;

import com.careerpilot.jdservice.config.CareerPilotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

/**
 * 调 ai-task-scheduler 登记解析任务。
 *
 * <p>刻意做成"失败不抛"：AI 调度服务挂了不应该让用户的上传请求失败 ——
 * JD 文本已经落库了，用户至少还能手动查看原文，之后点"重新解析"再试。
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
     * 登记一个 JD 解析任务。
     *
     * @return taskId；登记失败返回 null（调用方据此把 status 留在 PENDING）
     */
    public String submitJdParse(String userId, String jdBizId, String rawText) {
        Map<String, Object> input = new HashMap<>();
        input.put("jd_id", jdBizId);
        input.put("jd_text", rawText);

        Map<String, Object> body = new HashMap<>();
        body.put("taskType", "jd_parse");
        body.put("bizId", jdBizId);
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
            log.error("登记 JD 解析任务失败: jdId={}, err={}", jdBizId, e.getMessage());
            return null;
        }
    }
}
