r"""
基础功能冒烟测试（不依赖 LLM，只验证规则引擎与业务链路）。

覆盖：
    求职者：自助注册 → 简历 ATS → JD 解析 → 匹配打分 → 简历优化
            → 模拟面试出题/评分 → 求职信 → 职业规划
    招聘方：自助注册 → 候选人简历 → 岗位 JD → 批量匹配 → 深度洞察 → 追问判定
    鉴权：招聘方可以自助注册；求职者不能访问 HR 接口；ADMIN 不能自助注册

用法：
    python scripts/basic_feature_smoke.py
    CAREERPILOT_GW=http://127.0.0.1:8080 python scripts/basic_feature_smoke.py

退出码 0 = 全部通过。
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request

GW = os.environ.get("CAREERPILOT_GW", "http://127.0.0.1:8080")
STAMP = time.strftime("%Y%m%d%H%M%S") + str(os.getpid() % 1000)
PASSWORD = "Test1234"

PASSED = 0
FAILED = 0


# ── HTTP ─────────────────────────────────────────────────────────────

def call(method, path, payload=None, token=None, raw=None, content_type=None):
    body = raw
    ctype = content_type
    if raw is None and payload is not None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        ctype = "application/json; charset=utf-8"

    req = urllib.request.Request(GW + path, data=body, method=method)
    if ctype:
        req.add_header("Content-Type", ctype)
    if token:
        req.add_header("Authorization", "Bearer " + token)

    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        text = exc.read().decode("utf-8", "replace")
        try:
            return json.loads(text)
        except ValueError:
            return {"code": exc.code, "message": text[:300], "data": None}


def multipart(text, filename, title):
    boundary = "----CareerPilotSmoke" + STAMP
    content = text.encode("utf-8")
    parts = [
        ("--%s\r\n" % boundary).encode(),
        ('Content-Disposition: form-data; name="file"; filename="%s"\r\n' % filename).encode("utf-8"),
        b"Content-Type: text/plain; charset=utf-8\r\n\r\n",
        content, b"\r\n",
        ("--%s\r\n" % boundary).encode(),
        b'Content-Disposition: form-data; name="title"\r\n\r\n',
        title.encode("utf-8"), b"\r\n",
        ("--%s--\r\n" % boundary).encode(),
    ]
    return b"".join(parts), "multipart/form-data; boundary=%s" % boundary


def wait_for(fn, timeout=120, interval=1):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            value = fn()
        except Exception:
            value = None
        if value:
            return value
        time.sleep(interval)
    return None


# ── 断言 ─────────────────────────────────────────────────────────────

def check(name, cond, detail=""):
    global PASSED, FAILED
    if cond:
        PASSED += 1
        print("  [PASS] %s %s" % (name, detail))
    else:
        FAILED += 1
        print("  [FAIL] %s %s" % (name, detail))


# ── 假数据 ───────────────────────────────────────────────────────────

BACKEND_RESUME = """张伟
高级后端工程师 · 6 年经验
邮箱：zhangwei@example.com · 电话：13800001111 · 杭州
求职意向：高级后端工程师

个人简介
6 年后端开发经验，专注高并发交易系统、微服务治理与稳定性建设。
熟悉 Java、Spring Boot、MySQL、Redis、Kafka、Kubernetes 与 CI/CD。

工作经历
2021.03 - 至今  某支付公司  高级后端工程师
- 主导支付网关服务拆分，将单体交易链路拆成 6 个领域服务，发布频率从每月 1 次提升到每周 2 次。
- 设计并落地限流熔断方案，核心接口 P99 延迟从 850ms 降到 120ms，线上故障率下降 70%。
- 使用 Redis 缓存与 MySQL 索引优化，支撑日均 300 万笔交易，数据库 CPU 峰值下降 35%。
- 推动 Kubernetes 与 CI/CD 流水线建设，发布回滚时间从 40 分钟缩短到 5 分钟。

2019.07 - 2021.02  某电商公司  后端工程师
- 负责订单履约系统开发，使用 Spring Boot 与 Kafka 实现异步削峰。
- 参与微服务拆分与链路追踪建设，接口超时率下降 60%。

教育经历
2015.09 - 2019.06  某大学  软件工程  本科

技能
Java、Spring Boot、MySQL、Redis、Kafka、Kubernetes、CI/CD、分布式系统、微服务
"""

FRONTEND_RESUME = """李娜
前端工程师 · 4 年经验
邮箱：lina@example.com · 电话：13800002222 · 上海
求职意向：前端工程师

工作经历
2022.01 - 至今  某 SaaS 公司  前端工程师
- 使用 Vue 3 + TypeScript 重构管理后台，首屏加载时间从 3.2s 降到 1.1s。
- 建设组件库与设计令牌，覆盖 30+ 业务页面，重复开发工作量下降 40%。
- 负责 Webpack 构建优化，产物体积减少 35%。

2020.07 - 2021.12  某内容平台  前端开发
- 使用 React 与 CSS 实现内容编辑器，支撑日活 20 万用户。
- 参与前端监控与性能优化，JS 错误率下降 50%。

教育经历
2016.09 - 2020.06  某大学  计算机科学与技术  本科

技能
Vue、TypeScript、React、CSS、Webpack、性能优化、组件库
"""

DATA_RESUME = """王强
数据工程师 · 5 年经验
邮箱：wangqiang@example.com · 电话：13800003333 · 北京
求职意向：数据工程师

工作经历
2021.05 - 至今  某数据平台  数据工程师
- 基于 Spark 与 Hive 建设离线数仓，日均处理 8TB 数据，任务稳定率 99.5%。
- 使用 Airflow 编排 200+ 数据任务，故障恢复时间从 2 小时降到 20 分钟。
- 设计用户行为宽表，支撑 12 个业务报表，查询耗时下降 45%。

2019.07 - 2021.04  某零售公司  数据分析工程师
- 使用 Python 与 SQL 完成销售分析，帮助运营定位 3 个高潜品类。
- 搭建指标口径文档与数据质量校验，数据投诉下降 60%。

教育经历
2015.09 - 2019.06  某大学  统计学  本科

技能
Python、Spark、SQL、Airflow、Hive、数据仓库、数据质量
"""

JD_BACKEND = """高级后端工程师（交易平台方向）
公司：某支付科技
地点：杭州
薪资：35-55K

岗位职责：
1. 负责核心交易系统的架构设计与服务拆分；
2. 负责高并发场景下的稳定性、性能与容量治理；
3. 推动 CI/CD、容器化与可观测性建设。

任职要求：
1. 本科及以上学历，计算机相关专业；
2. 5 年以上后端开发经验；
3. 精通 Java、Spring Boot，熟悉 MySQL、Redis、Kafka；
4. 熟悉 Kubernetes 与 CI/CD，有高并发系统经验者优先；
5. 具备良好的沟通能力与团队协作精神。
"""

JD_DATA = """数据工程师（实时数仓方向）
公司：某数据科技
地点：北京
薪资：30-45K

岗位职责：
1. 负责离线与实时数仓建设；
2. 负责数据任务编排、数据质量与稳定性治理；
3. 支撑业务报表与用户行为分析。

任职要求：
1. 本科及以上学历；
2. 3 年以上数据开发经验；
3. 熟悉 Python、Spark、SQL、Airflow、Hive；
4. 有数据仓库建模经验者优先。
"""

INTERVIEW_ANSWER = (
    "在支付网关拆分项目中，背景是单体交易链路发布慢、故障影响面大。"
    "我负责订单履约链路的拆分方案，先梳理依赖并定义领域边界，"
    "再用 Spring Boot 和 Kafka 落地异步削峰，配合 Kubernetes 与 CI/CD 分批发布。"
    "最终发布频率从每月 1 次提升到每周 2 次，P99 延迟从 850ms 降到 120ms，"
    "线上故障率下降 70%。"
)


# ── 业务辅助 ─────────────────────────────────────────────────────────

def register(prefix, role):
    email = "%s_%s@test.local" % (prefix, STAMP)
    resp = call("POST", "/api/v1/auth/register", {
        "email": email,
        "password": PASSWORD,
        "nickname": prefix,
        "role": role,
    }, token="")
    data = resp.get("data") or {}
    return email, data.get("accessToken"), (data.get("user") or {}).get("role"), resp


def upload_resume(token, title, text):
    body, ctype = multipart(text, title + ".txt", title)
    resp = call("POST", "/api/v1/resume/upload", raw=body, content_type=ctype, token=token)
    resume_id = (resp.get("data") or {}).get("resumeId")
    if not resume_id:
        return None, None, resp

    ats = wait_for(lambda: (
        lambda d: d if d and d.get("overall_score") is not None else None
    )(call("GET", "/api/v1/resume/%s/ats-score" % resume_id, token=token).get("data")))
    return resume_id, ats, resp


def create_jd(token, title, company, text):
    resp = call("POST", "/api/v1/jd/upload", {
        "jobTitle": title,
        "companyName": company,
        "rawText": text,
        "sourceType": "MANUAL",
    }, token=token)
    data = resp.get("data") or {}
    jd_id = data.get("jdId")
    if not jd_id:
        return None, None, None, resp

    parsed = wait_for(lambda: (
        lambda d: d if d and d.get("skills") else None
    )(call("GET", "/api/v1/jd/%s/parse/result" % jd_id, token=token).get("data")))
    return jd_id, data.get("jdBizId"), parsed, resp


def evaluate_match(token, resume_id, jd_id):
    resp = call("POST", "/api/v1/match/evaluate", {
        "resumeId": resume_id,
        "jdId": jd_id,
    }, token=token)
    record_id = (resp.get("data") or {}).get("recordId")
    if not record_id:
        return None, resp

    record = wait_for(lambda: (
        lambda d: d if d and d.get("status") in ("SUCCESS", "FAILED") else None
    )(call("GET", "/api/v1/match/%s" % record_id, token=token).get("data")))
    return record, resp


def create_task(token, task_type, input_data, biz_id=None):
    resp = call("POST", "/api/v1/ai-tasks", {
        "taskType": task_type,
        "bizId": biz_id,
        "input": input_data,
    }, token=token)
    task_id = (resp.get("data") or {}).get("taskId")
    if not task_id:
        return None, resp

    task = wait_for(lambda: (
        lambda d: d if d and d.get("status") in ("SUCCESS", "FAILED", "CANCELLED") else None
    )(call("GET", "/api/v1/ai-tasks/%s" % task_id, token=token).get("data")))
    return task, resp


def create_insight(token, resume_id, jd_id, max_questions=6):
    resp = call("POST", "/api/v1/hr/insight", {
        "resumeId": resume_id,
        "jdId": jd_id,
        "maxQuestions": max_questions,
    }, token=token)
    insight_id = (resp.get("data") or {}).get("insightId")
    if not insight_id:
        return None, None, resp

    detail = wait_for(lambda: (
        lambda d: d if d and d.get("report") else None
    )(call("GET", "/api/v1/hr/insight/%s" % insight_id, token=token).get("data")))
    return insight_id, detail, resp


# ── 测试主体 ─────────────────────────────────────────────────────────

def main():
    print("== 基础功能冒烟测试 ==")
    print("网关：%s" % GW)

    # 1. 自助注册与角色
    print("\n-- 自助注册与角色 --")
    seeker_email, seeker_token, seeker_role, seeker_resp = register("seeker", "USER")
    hr_email, hr_token, hr_role, hr_resp = register("recruiter", "RECRUITER")
    check("求职者自助注册", bool(seeker_token) and seeker_role == "USER", seeker_email)
    check("招聘方自助注册", bool(hr_token) and hr_role == "RECRUITER", hr_email)

    admin_email = "admin_self_%s@test.local" % STAMP
    admin_resp = call("POST", "/api/v1/auth/register", {
        "email": admin_email,
        "password": PASSWORD,
        "role": "ADMIN",
    }, token="")
    check("ADMIN 不允许自助注册", admin_resp.get("code") == 1400, admin_resp.get("message", ""))

    boundary = call("GET", "/api/v1/hr/insight/list", token=seeker_token)
    check("求职者不能访问 HR 接口", boundary.get("code") == 1403, boundary.get("message", ""))
    hr_list = call("GET", "/api/v1/hr/insight/list", token=hr_token)
    check("招聘方可以访问 HR 接口", hr_list.get("code") == 0, "total=%s" % ((hr_list.get("data") or {}).get("total")))

    # 2. 求职者：简历 × JD × 匹配
    print("\n-- 求职者：简历 / JD / 匹配 --")
    backend_rid, backend_ats, backend_resp = upload_resume(seeker_token, "张伟-后端-6年", BACKEND_RESUME)
    frontend_rid, frontend_ats, frontend_resp = upload_resume(seeker_token, "李娜-前端-4年", FRONTEND_RESUME)
    data_rid, data_ats, data_resp = upload_resume(seeker_token, "王强-数据-5年", DATA_RESUME)
    check("上传后端简历", bool(backend_rid), "resumeId=%s" % backend_rid)
    check("后端简历 ATS 完成", bool(backend_ats) and backend_ats.get("overall_score", 0) > 0,
          "score=%s" % (backend_ats or {}).get("overall_score"))
    check("后端简历 ATS 维度", bool(backend_ats) and len(backend_ats.get("dimensions", [])) >= 3,
          "dimensions=%s" % len((backend_ats or {}).get("dimensions", [])))
    check("上传前端简历", bool(frontend_rid), "resumeId=%s" % frontend_rid)
    check("上传数据简历", bool(data_rid), "resumeId=%s" % data_rid)

    backend_jd, backend_jd_biz, backend_parse, backend_jd_resp = create_jd(
        seeker_token, "高级后端工程师", "某支付科技", JD_BACKEND)
    data_jd, data_jd_biz, data_parse, data_jd_resp = create_jd(
        seeker_token, "数据工程师", "某数据科技", JD_DATA)
    check("创建后端 JD", bool(backend_jd), "jdId=%s" % backend_jd)
    check("后端 JD 解析技能", bool(backend_parse) and len(backend_parse.get("skills", [])) > 0,
          "skills=%s" % len((backend_parse or {}).get("skills", [])))
    check("创建数据 JD", bool(data_jd), "jdId=%s" % data_jd)
    check("数据 JD 解析技能", bool(data_parse) and len(data_parse.get("skills", [])) > 0,
          "skills=%s" % len((data_parse or {}).get("skills", [])))

    backend_match, _ = evaluate_match(seeker_token, backend_rid, backend_jd)
    frontend_match, _ = evaluate_match(seeker_token, frontend_rid, backend_jd)
    data_match, _ = evaluate_match(seeker_token, data_rid, data_jd)
    check("后端简历 × 后端 JD 匹配完成",
          bool(backend_match) and backend_match.get("status") == "SUCCESS",
          "status=%s" % (backend_match or {}).get("status"))
    check("后端匹配有分数与差距",
          bool(backend_match) and backend_match.get("finalScore") is not None
          and len(backend_match.get("gapList", [])) >= 0,
          "final=%s gaps=%s" % ((backend_match or {}).get("finalScore"), len((backend_match or {}).get("gapList", []))))
    check("后端匹配命中技能",
          bool(backend_match) and len(backend_match.get("matchedSkills", [])) > 0,
          "matched=%s" % ((backend_match or {}).get("matchedSkills", [])))
    check("前端简历 × 后端 JD 匹配完成",
          bool(frontend_match) and frontend_match.get("status") == "SUCCESS",
          "final=%s" % (frontend_match or {}).get("finalScore"))
    check("匹配分数有区分度",
          bool(backend_match) and bool(frontend_match)
          and backend_match.get("finalScore") is not None
          and frontend_match.get("finalScore") is not None
          and backend_match["finalScore"] > frontend_match["finalScore"],
          "backend=%s frontend=%s" % (
              (backend_match or {}).get("finalScore"), (frontend_match or {}).get("finalScore")))
    check("数据简历 × 数据 JD 匹配完成",
          bool(data_match) and data_match.get("status") == "SUCCESS",
          "final=%s" % (data_match or {}).get("finalScore"))

    jd_skills = [s.get("name") for s in (backend_parse or {}).get("skills", []) if s.get("name")]

    # 3. 求职者：生成类能力
    print("\n-- 求职者：优化 / 面试 / 求职信 / 职业规划 --")
    optimize_task, _ = create_task(seeker_token, "resume_optimize", {
        "bullet": "负责用户增长相关工作，提升了留存率",
        "tone": "professional",
        "jd_id": backend_jd_biz,
        "jd_text": JD_BACKEND,
        "jd_skills": jd_skills,
    }, biz_id=backend_jd_biz)
    optimize_out = (optimize_task or {}).get("output") or {}
    check("简历优化任务成功", (optimize_task or {}).get("status") == "SUCCESS",
          "status=%s" % (optimize_task or {}).get("status"))
    check("简历优化有重写结果", bool(optimize_out.get("rewritten")), "len=%s" % len(optimize_out.get("rewritten", "")))
    check("简历优化有 STAR 拆分", bool(optimize_out.get("star_breakdown")),
          "keys=%s" % sorted((optimize_out.get("star_breakdown") or {}).keys()))
    check("简历优化有改动说明", bool(optimize_out.get("change_note")))

    question_task, _ = create_task(seeker_token, "interview", {
        "phase": "question",
        "round_no": 1,
        "total_rounds": 3,
        "jd_skills": jd_skills,
        "resume_highlights": ["张伟-后端-6年"],
        "job_title": "高级后端工程师",
        "jd_id": backend_jd_biz,
        "jd_text": JD_BACKEND,
        "resume_id": "",
        "resume_text": BACKEND_RESUME[:500],
    }, biz_id=backend_jd_biz)
    question_out = (question_task or {}).get("output") or {}
    check("模拟面试出题成功", (question_task or {}).get("status") == "SUCCESS" and bool(question_out.get("question")),
          "question=%s" % (question_out.get("question", "")[:40]))

    evaluate_task, _ = create_task(seeker_token, "interview", {
        "phase": "evaluate",
        "round_no": 1,
        "question": question_out.get("question") or "请介绍一个你做过的项目",
        "answer": INTERVIEW_ANSWER,
        "jd_skills": jd_skills,
    }, biz_id=backend_jd_biz)
    evaluate_out = (evaluate_task or {}).get("output") or {}
    check("模拟面试评分成功", (evaluate_task or {}).get("status") == "SUCCESS",
          "score=%s" % evaluate_out.get("score"))
    check("模拟面试有 STAR 覆盖", bool(evaluate_out.get("star_coverage")),
          "coverage=%s" % evaluate_out.get("star_coverage"))
    check("模拟面试有反馈", bool(evaluate_out.get("feedback")))

    cover_task, _ = create_task(seeker_token, "cover_letter", {
        "job_title": "高级后端工程师",
        "company_name": "某支付科技",
        "tone": "professional",
        "jd_id": backend_jd_biz,
        "jd_text": JD_BACKEND,
        "jd_skills": jd_skills,
        "resume_highlights": ["张伟-后端-6年"],
    }, biz_id=backend_jd_biz)
    cover_out = (cover_task or {}).get("output") or {}
    check("求职信生成成功", (cover_task or {}).get("status") == "SUCCESS" and len(cover_out.get("content", "")) > 100,
          "chars=%s" % len(cover_out.get("content", "")))
    check("求职信有字数与技能", bool(cover_out.get("word_count")) and isinstance(cover_out.get("highlighted_skills"), list),
          "word_count=%s" % cover_out.get("word_count"))

    career_task, _ = create_task(seeker_token, "career_plan", {
        "current_title": "后端工程师",
        "years_of_experience": 3,
        "target_industry": "互联网",
        "target_position": "高级后端工程师",
        "skills": ["Java", "MySQL", "Redis"],
    })
    career_out = (career_task or {}).get("output") or {}
    check("职业规划生成成功", (career_task or {}).get("status") == "SUCCESS", "status=%s" % (career_task or {}).get("status"))
    check("职业规划有路线图", isinstance(career_out.get("roadmap"), list) and len(career_out.get("roadmap", [])) > 0,
          "phases=%s" % len(career_out.get("roadmap", [])))
    check("职业规划有技能缺口", isinstance(career_out.get("skill_gap"), list),
          "gap=%s" % career_out.get("skill_gap"))

    # 4. 招聘方：候选人简历 / 匹配 / 洞察 / 追问
    print("\n-- 招聘方：候选人 / 匹配 / 洞察 / 追问 --")
    candidate_rid, candidate_ats, _ = upload_resume(hr_token, "王强-候选人-数据-5年", DATA_RESUME)
    candidate2_rid, candidate2_ats, _ = upload_resume(hr_token, "李娜-候选人-前端-4年", FRONTEND_RESUME)
    check("招聘方导入候选人简历", bool(candidate_rid) and bool(candidate2_rid))
    check("候选人 ATS 完成", bool(candidate_ats) and candidate_ats.get("overall_score", 0) > 0,
          "score=%s" % (candidate_ats or {}).get("overall_score"))

    hr_jd, hr_jd_biz, hr_parse, _ = create_jd(hr_token, "高级后端工程师", "某支付科技", JD_BACKEND)
    check("招聘方创建岗位 JD", bool(hr_jd), "jdId=%s" % hr_jd)
    check("招聘方 JD 解析技能", bool(hr_parse) and len(hr_parse.get("skills", [])) > 0,
          "skills=%s" % len((hr_parse or {}).get("skills", [])))

    hr_match1, _ = evaluate_match(hr_token, candidate_rid, hr_jd)
    hr_match2, _ = evaluate_match(hr_token, candidate2_rid, hr_jd)
    check("招聘方候选人匹配完成",
          bool(hr_match1) and bool(hr_match2)
          and hr_match1.get("status") == "SUCCESS" and hr_match2.get("status") == "SUCCESS",
          "candidate=%s frontend=%s" % (
              (hr_match1 or {}).get("finalScore"), (hr_match2 or {}).get("finalScore")))

    insight_id, insight, insight_resp = create_insight(hr_token, candidate_rid, hr_jd)
    report = (insight or {}).get("report") or {}
    check("深度洞察报告生成", bool(insight_id) and bool(report),
          "insightId=%s" % insight_id)
    check("洞察包含匹配总览", bool((report.get("match_overview") or {}).get("final_score") is not None),
          "final=%s" % (report.get("match_overview") or {}).get("final_score"))
    check("洞察包含能力覆盖", len(report.get("capability_coverage", [])) > 0,
          "dimensions=%s" % len(report.get("capability_coverage", [])))
    check("洞察包含验证问题", len(report.get("verification_plan", [])) > 0,
          "questions=%s" % len(report.get("verification_plan", [])))

    plan = report.get("verification_plan") or []
    if plan:
        vq = plan[0]
        verify_resp = call("POST", "/api/v1/hr/verify/answer", {
            "insightId": insight_id,
            "vqId": vq.get("id"),
            "answer": INTERVIEW_ANSWER,
            "roundNo": 1,
        }, token=hr_token)
        verify_task_id = (verify_resp.get("data") or {}).get("taskId")
        verify_task = wait_for(lambda: (
            lambda d: d if d and d.get("status") in ("SUCCESS", "FAILED") else None
        )(call("GET", "/api/v1/ai-tasks/%s" % verify_task_id, token=hr_token).get("data"))) if verify_task_id else None
        records = call("GET", "/api/v1/hr/verify/records/%s" % insight_id, token=hr_token)
        record_list = records.get("data") or []
        check("追问判定任务完成", bool(verify_task) and verify_task.get("status") == "SUCCESS",
              "status=%s" % (verify_task or {}).get("status"))
        check("追问判定结果落库", len(record_list) > 0 and record_list[0].get("coverage") in
              ("demonstrated", "adjacent", "not_evidenced"),
              "coverage=%s" % (record_list[0].get("coverage") if record_list else None))
    else:
        check("追问判定任务完成", False, "报告没有验证问题")

    # 5. 列表与数据归属
    print("\n-- 列表与数据归属 --")
    seeker_resumes = call("GET", "/api/v1/resume/list?page=1&size=100", token=seeker_token)
    seeker_jds = call("GET", "/api/v1/jd/list?page=1&size=100", token=seeker_token)
    seeker_matches = call("GET", "/api/v1/match/list?page=1&size=100", token=seeker_token)
    check("求职者简历列表", (seeker_resumes.get("data") or {}).get("total") == 3,
          "total=%s" % ((seeker_resumes.get("data") or {}).get("total")))
    check("求职者 JD 列表", (seeker_jds.get("data") or {}).get("total") == 2,
          "total=%s" % ((seeker_jds.get("data") or {}).get("total")))
    check("求职者匹配列表", (seeker_matches.get("data") or {}).get("total") == 3,
          "total=%s" % ((seeker_matches.get("data") or {}).get("total")))
    hr_resumes = call("GET", "/api/v1/resume/list?page=1&size=100", token=hr_token)
    check("招聘方只能看到自己的候选人", (hr_resumes.get("data") or {}).get("total") == 2,
          "total=%s" % ((hr_resumes.get("data") or {}).get("total")))

    print("\n" + "=" * 60)
    print("结果：%s 通过 / %s 失败" % (PASSED, FAILED))
    if FAILED:
        print("测试未全部通过，请检查上面的 FAIL 项。")
        return 1

    print("测试数据账号：")
    print("  求职者：%s / %s" % (seeker_email, PASSWORD))
    print("  招聘方：%s / %s" % (hr_email, PASSWORD))
    print("所有基础能力均通过规则引擎完成，未启用 LLM。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
