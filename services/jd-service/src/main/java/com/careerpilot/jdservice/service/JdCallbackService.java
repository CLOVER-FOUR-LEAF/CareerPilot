package com.careerpilot.jdservice.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerpilot.jdservice.entity.Jd;
import com.careerpilot.jdservice.entity.JdParseResult;
import com.careerpilot.jdservice.entity.JdSkill;
import com.careerpilot.jdservice.mapper.JdMapper;
import com.careerpilot.jdservice.mapper.JdParseResultMapper;
import com.careerpilot.jdservice.mapper.JdSkillMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.careerpilot.jdservice.service.JdService.toDecimal;

/**
 * 接收 ai-task-scheduler 的 JD 解析回调，把 Agent 的 JdChunk 落库。
 *
 * <p>重新解析走 upsert：{@code t_jd_parse_result} 上有 jd_id 唯一键，
 * 直接 insert 第二次会撞主键。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JdCallbackService {

    private final JdService jdService;
    private final JdMapper jdMapper;
    private final JdParseResultMapper parseResultMapper;
    private final JdSkillMapper jdSkillMapper;

    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(String jdBizId, boolean success, Map<String, Object> output,
                               String errorMessage) {
        Jd jd = jdService.findByBizId(jdBizId);
        if (jd == null) {
            log.warn("回调对应的 JD 不存在: jdId={}", jdBizId);
            return;
        }

        if (!success || output == null || output.isEmpty()) {
            markStatus(jd, "FAILED");
            return;
        }

        JSONObject chunk = JSONUtil.parseObj(output);

        upsertParseResult(jd.getJdId(), chunk);
        replaceSkills(jd.getJdId(), chunk);
        markStatus(jd, "SUCCESS");
    }

    private void markStatus(Jd jd, String status) {
        Jd patch = new Jd();
        patch.setId(jd.getId());
        patch.setStatus(status);
        patch.setUpdatedAt(LocalDateTime.now());
        jdMapper.updateById(patch);
    }

    private void upsertParseResult(String jdBizId, JSONObject chunk) {
        JdParseResult existing = jdService.findParseResult(jdBizId);

        JdParseResult entity = existing != null ? existing : new JdParseResult();
        if (existing == null) {
            entity.setJdId(jdBizId);
            entity.setCreatedAt(LocalDateTime.now());
        }

        entity.setRawResultJson(JSONUtil.toJsonStr(chunk));
        entity.setHardSkillsJson(pickSkillsJson(chunk, "technical", "tool", "language", "certification"));
        entity.setSoftSkillsJson(pickSkillsJson(chunk, "soft_skill"));
        entity.setBizMetricsJson(pickSkillsJson(chunk, "biz_metric"));
        entity.setResponsibilitiesJson(JSONUtil.toJsonStr(chunk.getJSONArray("responsibilities")));
        entity.setRequirementsJson(JSONUtil.toJsonStr(chunk.getJSONArray("requirements")));
        entity.setSeniority(deriveSeniority(chunk));
        entity.setIndustry(deriveIndustry(chunk));
        entity.setLocation(chunk.getStr("location"));
        entity.setSalaryMin(toDecimal(chunk.get("salary_min")));
        entity.setSalaryMax(toDecimal(chunk.get("salary_max")));
        entity.setExperienceMinYears(toDecimal(chunk.get("experience_min_years")));
        entity.setEducationRequired(chunk.getStr("education_required"));

        BigDecimal score = toDecimal(chunk.get("parse_score"));
        entity.setParseScore(score != null && score.compareTo(BigDecimal.ONE) > 0
                ? score.divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP)
                : score);
        entity.setParseModel(chunk.getStr("parse_method"));
        entity.setUpdatedAt(LocalDateTime.now());

        if (existing == null) {
            parseResultMapper.insert(entity);
        } else {
            parseResultMapper.updateById(entity);
        }
    }

    private String pickSkillsJson(JSONObject chunk, String... categories) {
        List<String> wanted = List.of(categories);
        Object skills = chunk.get("skills");
        if (!(skills instanceof JSONArray array)) {
            return "[]";
        }
        JSONArray result = new JSONArray();
        for (Object o : array) {
            if (o instanceof JSONObject skill) {
                String category = skill.getStr("category", "other");
                if (wanted.contains(category)) {
                    result.add(skill);
                }
            }
        }
        return result.toString();
    }

    /** 技能清单整体重写：JD 文本变了，旧技能就没意义了。 */
    private void replaceSkills(String jdBizId, JSONObject chunk) {
        jdSkillMapper.delete(new LambdaQueryWrapper<JdSkill>().eq(JdSkill::getJdId, jdBizId));

        Object skills = chunk.get("skills");
        if (!(skills instanceof JSONArray array)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (Object o : array) {
            if (!(o instanceof JSONObject skill)) {
                continue;
            }
            String name = skill.getStr("name");
            if (name == null || name.isBlank()) {
                continue;
            }
            JdSkill entity = new JdSkill();
            entity.setJdId(jdBizId);
            entity.setSkillName(name);
            entity.setSkillType(mapSkillType(skill.getStr("category", "other")));
            entity.setWeight(skill.getBigDecimal("weight") != null
                    ? skill.getBigDecimal("weight")
                    : importanceToWeight(skill.getStr("importance")));
            entity.setCreatedAt(now);
            jdSkillMapper.insert(entity);
        }
    }

    private static String mapSkillType(String category) {
        return switch (category) {
            case "soft_skill" -> "soft";
            case "biz_metric" -> "biz_metric";
            default -> "hard";
        };
    }

    private static BigDecimal importanceToWeight(String importance) {
        return switch (importance == null ? "" : importance) {
            case "required" -> new BigDecimal("0.90");
            case "preferred" -> new BigDecimal("0.60");
            default -> new BigDecimal("0.40");
        };
    }

    /**
     * 派生职级：JD 没直接写 seniority 时，从经验年限反推。
     * 这一步是 match-service 打分"硬性条件"判定所需要的输入。
     */
    private static String deriveSeniority(JSONObject chunk) {
        String explicit = chunk.getStr("seniority");
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        BigDecimal years = toDecimal(chunk.get("experience_min_years"));
        if (years == null) {
            return null;
        }
        double y = years.doubleValue();
        if (y < 1) {
            return "intern";
        }
        if (y < 3) {
            return "junior";
        }
        if (y < 5) {
            return "mid";
        }
        if (y < 8) {
            return "senior";
        }
        return "lead";
    }

    /** 行业分类：优先用 Agent 给的，否则从公司名/文本里做一次关键词命中。 */
    private static String deriveIndustry(JSONObject chunk) {
        String explicit = chunk.getStr("industry");
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        String text = chunk.getStr("raw_text", "");
        if (text == null) {
            text = "";
        }
        String[][] rules = {
                {"互联网", "后端", "前端", "算法", "微服务", "分布式", "高并发", "Java", "React"},
                {"金融", "银行", "证券", "基金", "支付", "风控", "清算", "保险"},
                {"制造", "工厂", "产线", "MES", "PLC", "供应链", "质量管理"},
                {"外企", "English", "English is", "global", "Global", "跨境", "出海"}
        };
        for (String[] rule : rules) {
            for (String keyword : rule) {
                if (text.contains(keyword)) {
                    return rule[0];
                }
            }
        }
        return null;
    }
}
