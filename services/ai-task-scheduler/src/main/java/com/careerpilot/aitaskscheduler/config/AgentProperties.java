package com.careerpilot.aitaskscheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 任务编排相关的外部依赖配置，全部支持环境变量覆盖，
 * 本地开发走 scripts/local/.env，部署到服务器时用同名环境变量覆盖即可。
 */
@Data
@Component
@ConfigurationProperties(prefix = "careerpilot")
public class AgentProperties {

    /** LangGraph 编排服务地址。所有任务统一打到这里，由 orchestrator 再分发到各专用 Agent。 */
    private String orchestratorUrl = "http://localhost:8001";

    /** 单个任务的 Agent 调用超时（秒）。简历分析、匹配打分都可能偏慢。 */
    private int timeoutSeconds = 180;

    /**
     * 任务类型 → 结果回调地址。
     * 只有需要把结果写回业务表的类型才配；其余类型的调用方直接轮询 /ai-tasks/{taskId} 取结果。
     */
    private Map<String, String> callbacks = new HashMap<>();

    /**
     * 任务类型 → Agent 名称，仅用于调用日志与前端进度展示。
     */
    private Map<String, String> agentNames = new HashMap<>(
            Map.of(
                    "jd_parse", "jd-parser-agent",
                    "resume_analyze", "resume-analyzer-agent",
                    "match_score", "match-agent",
                    "resume_optimize", "resume-optimizer-agent",
                    "interview", "interview-agent",
                    "cover_letter", "cover-letter-agent",
                    "career_plan", "career-advisor-agent",
                    // HR 侧复用 interview-agent 的 recruiter 模式
                    // （约束 C1：本期不新增 Python Agent 进程）
                    "hr_insight", "interview-agent",
                    "jd_calibrate", "interview-agent",
                    "hr_verify", "interview-agent"
            ));

    /** 支持的任务类型白名单。 */
    public boolean isSupported(String taskType) {
        return agentNames.containsKey(taskType);
    }
}
