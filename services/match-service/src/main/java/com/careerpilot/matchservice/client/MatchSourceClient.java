package com.careerpilot.matchservice.client;

import cn.hutool.json.JSONUtil;
import com.careerpilot.matchservice.config.CareerPilotProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 向 resume-service / jd-service 取匹配所需的原始素材。
 *
 * <p>刻意走对方服务的内部接口，而不是让 match-service 直连它们的表：
 * 归属校验必须发生在数据所有者手里，否则任何人猜到 ID 就能读走别人的简历和 JD。
 */
@Slf4j
@Component
public class MatchSourceClient {

    private final RestClient restClient;
    private final CareerPilotProperties properties;

    /**
     * 注入 {@code RestClient.Builder} 而不是 {@code RestClient}：
     * Spring Boot 只自动装配 Builder，裸的 RestClient 没有默认 Bean，
     * 直接注入会导致启动期 UnsatisfiedDependencyException。
     * 写法与 jd-service / resume-service 的 AiTaskClient 保持一致。
     */
    public MatchSourceClient(RestClient.Builder builder, CareerPilotProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    /** 简历素材。 */
    @Data
    public static class ResumeSource {
        private String resumeBizId;
        private String title;
        private String text;
        private Integer atsScore;
    }

    /** JD 素材。 */
    @Data
    public static class JdSource {
        private String jdBizId;
        private String title;
        private String company;
        private String text;
        private String parseStatus;
        private List<String> hardSkills = new ArrayList<>();
        private String seniority;
        private String industry;
        private String educationRequired;
        private Double experienceMinYears;
    }

    public ResumeSource fetchResume(Long resumeId, String userId) {
        Map<?, ?> body = get(
                properties.getResumeServiceUrl() + "/internal/resumes/" + resumeId + "/text",
                userId, "简历");

        ResumeSource source = new ResumeSource();
        source.setResumeBizId(str(body, "resumeBizId"));
        source.setTitle(str(body, "title"));
        source.setText(str(body, "text"));
        Object score = body.get("atsScore");
        if (score instanceof Number n) {
            source.setAtsScore(n.intValue());
        }
        return source;
    }

    public JdSource fetchJd(Long jdId, String userId) {
        Map<?, ?> body = get(
                properties.getJdServiceUrl() + "/internal/jds/" + jdId + "/text",
                userId, "JD");

        JdSource source = new JdSource();
        source.setJdBizId(str(body, "jdBizId"));
        source.setTitle(str(body, "title"));
        source.setCompany(str(body, "company"));
        source.setText(str(body, "text"));
        source.setParseStatus(str(body, "parseStatus"));
        source.setSeniority(str(body, "seniority"));
        source.setIndustry(str(body, "industry"));
        source.setEducationRequired(str(body, "educationRequired"));
        if (body.get("experienceMinYears") instanceof Number n) {
            source.setExperienceMinYears(n.doubleValue());
        }
        Object skills = body.get("hardSkills");
        if (skills instanceof List<?> list) {
            source.setHardSkills(list.stream()
                    .map(String::valueOf)
                    .filter(s -> !s.isBlank())
                    .toList());
        }
        return source;
    }

    /**
     * @return 包络里的 data
     * @throws SourceNotFoundException 素材不存在或不属于该用户
     * @throws SourceUnavailableException 依赖服务不可用
     */
    private Map<?, ?> get(String url, String userId, String label) {
        try {
            Map<?, ?> response = restClient.get()
                    .uri(url + "?userId=" + userId)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new SourceNotFoundException(label + "不存在");
            }
            Object code = response.get("code");
            if (code instanceof Number n && n.intValue() != 0) {
                throw new SourceNotFoundException(label + "不存在或无权访问");
            }
            Object data = response.get("data");
            if (!(data instanceof Map<?, ?> map)) {
                throw new SourceNotFoundException(label + "数据为空");
            }
            return map;
        } catch (SourceNotFoundException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("取{}失败: url={}, err={}", label, url, e.getMessage());
            throw new SourceUnavailableException(label + "服务暂时不可用，请稍后重试");
        }
    }

    /** 登记匹配打分任务。失败返回 null（调用方据此把记录留在 PENDING）。 */
    public String submitMatchScore(String userId, String recordBizId, Map<String, Object> input) {
        Map<String, Object> body = Map.of(
                "taskType", "match_score",
                "bizId", recordBizId,
                "input", input);

        try {
            Map<?, ?> response = restClient.post()
                    .uri(properties.getAiSchedulerUrl() + "/api/v1/ai-tasks")
                    .header("X-User-Id", userId)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !(response.get("data") instanceof Map<?, ?> data)) {
                return null;
            }
            Object taskId = data.get("taskId");
            return taskId == null ? null : String.valueOf(taskId);
        } catch (RestClientException e) {
            log.error("登记匹配任务失败: recordId={}, err={}", recordBizId, e.getMessage());
            return null;
        }
    }

    /** 直接构造 JSON 时用得上，保留一个统一的序列化入口。 */
    static String toJson(Object value) {
        return JSONUtil.toJsonStr(value);
    }

    private static String str(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static class SourceNotFoundException extends RuntimeException {
        public SourceNotFoundException(String message) {
            super(message);
        }
    }

    public static class SourceUnavailableException extends RuntimeException {
        public SourceUnavailableException(String message) {
            super(message);
        }
    }
}
