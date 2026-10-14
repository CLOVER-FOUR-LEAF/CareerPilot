package com.careerpilot.jdservice.controller;

import com.careerpilot.jdservice.dto.ApiResponse;
import com.careerpilot.jdservice.entity.Jd;
import com.careerpilot.jdservice.entity.JdParseResult;
import com.careerpilot.jdservice.service.JdService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 供其他微服务读取 JD 的内部接口，**不经网关**。
 *
 * <p>match-service 做匹配打分时需要 JD 全文与硬技能清单，
 * 但 JD 数据归 jd-service 所有，所以由 jd-service 校验归属并吐出数据，
 * 而不是让 match-service 直连本服务的表。
 */
@RestController
@RequestMapping("/internal/jds")
@RequiredArgsConstructor
public class InternalJdController {

    private final JdService jdService;

    /**
     * 按数据库主键取 JD 全文与硬技能。
     *
     * <p>{@code userId} 必传且会被校验归属 —— 没有归属校验的话，
     * 任何服务都能通过猜 ID 把别人的 JD 读走。
     */
    @GetMapping("/{id}/text")
    public ApiResponse<Map<String, Object>> textById(@PathVariable("id") Long id,
                                                     @RequestParam("userId") String userId) {
        Jd jd = jdService.requireOwned(userId, id);
        if (jd.getRawText() == null || jd.getRawText().isBlank()) {
            return ApiResponse.error(1404, "JD 正文为空");
        }

        JdParseResult parse = jdService.findParseResult(jd.getJdId());

        Map<String, Object> body = new HashMap<>();
        body.put("jdBizId", jd.getJdId());
        body.put("title", jd.getPositionName());
        body.put("company", jd.getCompanyName());
        body.put("text", jd.getRawText());
        body.put("parseStatus", jd.getStatus());
        body.put("hardSkills", parse == null ? List.of() : extractSkillNames(parse.getHardSkillsJson()));
        body.put("seniority", parse == null ? null : parse.getSeniority());
        body.put("industry", parse == null ? null : parse.getIndustry());
        body.put("educationRequired", parse == null ? null : parse.getEducationRequired());
        body.put("experienceMinYears", parse == null || parse.getExperienceMinYears() == null
                ? null : parse.getExperienceMinYears().doubleValue());

        return ApiResponse.success(body);
    }

    /**
     * 从 hard_skills_json 里抽出技能名数组。
     *
     * <p>这列是 Agent 写的 JSON，结构随 Agent 版本可能变，
     * 所以解析失败时退化成空列表而不是抛异常 —— 匹配降级比匹配失败可接受。
     */
    private List<String> extractSkillNames(String hardSkillsJson) {
        List<String> names = new ArrayList<>();
        if (hardSkillsJson == null || hardSkillsJson.isBlank()) {
            return names;
        }
        try {
            Object parsed = cn.hutool.json.JSONUtil.parseArray(hardSkillsJson);
            if (!(parsed instanceof cn.hutool.json.JSONArray array)) {
                return names;
            }
            for (Object o : array) {
                if (o instanceof cn.hutool.json.JSONObject skill) {
                    String name = skill.getStr("name");
                    if (name != null && !name.isBlank()) {
                        names.add(name);
                    }
                }
            }
        } catch (Exception e) {
            return names;
        }
        return names;
    }
}
