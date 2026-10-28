r"""
端到端冒烟测试（走网关，覆盖求职侧 + HR 侧的真实链路）。

覆盖链路：
    注册 → JD 解析 → 简历 ATS 评分 → 匹配打分 → HR 洞察报告
    → 报告列表 → 追问判定 → JD 反向校准

写成 Python 而不是 PowerShell：PowerShell 5.1 读 .ps1 脚本默认按 GBK 解码，
脚本里的中文字面量会变成乱码，导致「免责声明含『辅助建议』」这类断言假失败。

用法
----
    # 先把全栈跑起来
    powershell -File scripts\local\start-all.ps1
    # 再跑验收
    python scripts\e2e_smoke.py

退出码 0 = 全部通过。
"""
import json
import os
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

GW = "http://127.0.0.1:8080"

# 管理端引导账号：HR 接口在网关按角色收口，测试账号需要先被提升为 RECRUITER。
# 账号由 scripts/local/linux/bootstrap-admin.sh 创建，可用环境变量覆盖。
ADMIN_EMAIL = os.environ.get("CP_ADMIN_EMAIL", "admin@demo.local")
ADMIN_PASSWORD = os.environ.get("CP_ADMIN_PASSWORD", "Admin1234")

PASSED = 0
FAILED = 0
TOKEN = None
JD_ID = None
RESUME_ID = None
INSIGHT_ID = None


def call(method, path, payload=None, token=None, raw=None, content_type=None):
    # 注意：token 不能写成默认参数（token=TOKEN）——
    # 默认参数在函数定义时求值，那时 TOKEN 还是 None，
    # 注册之后拿到的令牌根本带不出去，表现为莫名其妙的 401。
    if token is None:
        token = TOKEN
    url = GW + path
    if raw is not None:
        body = raw
        ctype = content_type or "application/octet-stream"
    elif payload is not None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        ctype = "application/json; charset=utf-8"
    else:
        body = None
        ctype = None
    req = urllib.request.Request(url, data=body, method=method)
    if ctype:
        req.add_header("Content-Type", ctype)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw_body = e.read().decode("utf-8", "replace")
        try:
            return json.loads(raw_body)
        except ValueError:
            return {"code": e.code, "message": raw_body[:300], "data": None}


def check(name, cond, detail=""):
    global PASSED, FAILED
    if cond:
        PASSED += 1
        print("     \u00b7 %s %s" % (name, detail))
    else:
        FAILED += 1
        print("     [X] %s %s" % (name, detail))


def step(name):
    print("\n-- %s" % name)


def ensure_recruiter(email, password):
    """
    把测试账号提升为 RECRUITER，并重新登录换取带 role claim 的令牌。

    HR 接口在网关按 JWT 里的 role 收口：注册出来的是 USER，
    不提升的话 /api/v1/hr/** 一律 403。这也顺带验证了「管理员改角色」这条链路。
    """
    r = call("POST", "/api/v1/auth/login",
             {"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD}, token="")
    admin_token = (r.get("data") or {}).get("accessToken")
    if not admin_token:
        print("     [X] 管理员账号不可用：%s（先跑 scripts/local/linux/bootstrap-admin.sh）"
              % ADMIN_EMAIL)
        return None

    listing = call("GET",
                   "/api/v1/admin/users?page=1&size=100&keyword=" + urllib.parse.quote(email),
                   token=admin_token)
    records = ((listing.get("data") or {}).get("records") or [])
    target = next((x for x in records if x.get("email") == email), None)
    if not target:
        print("     [X] 管理端查不到测试账号：%s" % email)
        return None

    if target.get("role") != "RECRUITER":
        updated = call("PUT", "/api/v1/admin/users/%s/role" % target["id"],
                       {"role": "RECRUITER"}, token=admin_token)
        if updated.get("code") != 0:
            print("     [X] 提升 RECRUITER 失败：%s" % updated.get("message"))
            return None

    # 角色写在 accessToken 里，必须重新登录才会生效
    relogin = call("POST", "/api/v1/auth/login",
                   {"email": email, "password": password}, token="")
    return (relogin.get("data") or {}).get("accessToken")


def wait_for(fn, timeout=90, interval=2):
    deadline = time.time() + timeout
    while time.time() < deadline:
        r = fn()
        if r:
            return r
        time.sleep(interval)
    return None


def multipart(field_file_path, file_field, title):
    boundary = "----CareerPilotE2E" + str(int(time.time() * 1000))
    with open(field_file_path, "rb") as fh:
        content = fh.read()
    parts = []
    parts.append(("--%s\r\n" % boundary).encode())
    parts.append(('Content-Disposition: form-data; name="%s"; filename="%s"\r\n'
                  % (file_field, title)).encode("utf-8"))
    parts.append(b"Content-Type: text/plain\r\n\r\n")
    parts.append(content)
    parts.append(b"\r\n")
    parts.append(("--%s\r\n" % boundary).encode())
    parts.append(('Content-Disposition: form-data; name="title"\r\n\r\n').encode("utf-8"))
    parts.append(title.encode("utf-8"))
    parts.append(b"\r\n")
    parts.append(("--%s--\r\n" % boundary).encode())
    return b"".join(parts), "multipart/form-data; boundary=%s" % boundary


# ── 1. 注册 ───────────────────────────────────────────────────────
step("注册并拿到令牌")
E2E_EMAIL = "e2e%d@test.com" % int(time.time() % 100000)
E2E_PASSWORD = "Abcd1234"
r = call("POST", "/api/v1/auth/register",
         {"email": E2E_EMAIL,
          "password": E2E_PASSWORD, "nickname": "E2E", "inviteCode": None})
check("注册返回 code=0", r.get("code") == 0, "(实际 %s)" % r.get("code"))
TOKEN = (r.get("data") or {}).get("accessToken")
check("拿到 accessToken", bool(TOKEN))

# ── 2. JD ────────────────────────────────────────────────────────
step("新建 JD 并等解析完成")
JD_TEXT = (
    "高级后端工程师（Kubernetes / CI/CD 方向）\n"
    "负责核心交易系统的架构设计与服务拆分，主导 CI/CD 流水线建设与生产环境发布。\n"
    "要求 5 年以上后端开发经验，本科及以上学历，熟悉分布式系统与微服务治理。\n"
    "有高并发、高可用系统设计经验者优先，熟悉 Redis 与 MySQL 索引优化。\n"
    "具备良好的沟通能力与团队协作精神。"
)
r = call("POST", "/api/v1/jd/upload",
         {"jobTitle": "高级后端工程师", "companyName": "某电商",
          "rawText": JD_TEXT, "sourceType": "MANUAL"})
check("JD 创建成功", r.get("code") == 0, "(code=%s)" % r.get("code"))
JD_ID = (r.get("data") or {}).get("jdId")
check("拿到 jdId", bool(JD_ID), "(%s)" % JD_ID)


def jd_parsed():
    p = call("GET", "/api/v1/jd/%s/parse/result" % JD_ID)
    d = p.get("data") or {}
    skills = d.get("skills") or []
    return (skills, d) if skills else None


got = wait_for(jd_parsed, timeout=90)
check("JD 解析完成且有技能", bool(got),
      "技能: %s" % ("、".join(s["name"] for s in got[0]) if got else "无"))

# ── 3. 简历 ──────────────────────────────────────────────────────
step("上传简历并等 ATS 评分")
RESUME_TEXT = (
    "2019-2023 某电商公司 后端工程师\n"
    "负责订单系统后端开发，参与微服务拆分，主导 CI/CD 流水线建设与日常发布。\n"
    "负责 Redis 缓存设计与 MySQL 索引优化，支撑日订单百万级。\n"
    "2023-至今 某支付公司 后端专家\n"
    "负责支付网关服务设计，独立完成限流熔断方案，参与服务治理。\n"
    "跨部门推动多个需求落地，负责技术方案评审与团队协作。\n"
)
tmp = os.path.join(tempfile.gettempdir(), "e2e_resume.txt")
with open(tmp, "w", encoding="utf-8") as fh:
    fh.write(RESUME_TEXT)
body, ctype = multipart(tmp, "file", "E2E-后端-简历.txt")
r = call("POST", "/api/v1/resume/upload", raw=body, content_type=ctype)
check("简历上传成功", r.get("code") == 0, "(code=%s msg=%s)" % (r.get("code"), r.get("message")))
RESUME_ID = (r.get("data") or {}).get("resumeId")
check("拿到 resumeId", bool(RESUME_ID), "(%s)" % RESUME_ID)


def ats_ready():
    s = call("GET", "/api/v1/resume/%s/ats-score" % RESUME_ID)
    d = s.get("data") or {}
    return d if d.get("overall_score") else None


ats = wait_for(ats_ready, timeout=90)
check("ATS 评分完成", bool(ats), "(总分 %s)" % (ats.get("overall_score") if ats else "无"))

# ── 4. 匹配 ──────────────────────────────────────────────────────
step("匹配打分（顺带验证 M0 修复：jd_skills 真的到了 Agent）")
r = call("POST", "/api/v1/match/evaluate", {"resumeId": RESUME_ID, "jdId": JD_ID})
check("匹配任务已创建", r.get("code") == 0)
record_id = (r.get("data") or {}).get("recordId")


def match_done():
    d = call("GET", "/api/v1/match/%s" % record_id)
    data = d.get("data") or {}
    return data if data.get("status") == "SUCCESS" else None


m = wait_for(match_done, timeout=90)
check("匹配完成", bool(m))
if m:
    print("       final=%s matched=%s gap 数=%d"
          % (m.get("finalScore"), "、".join(m.get("matchedSkills") or []),
             len(m.get("gapList") or [])))
    check("识别出 gap", len(m.get("gapList") or []) > 0)

# ── 5. HR 洞察 ──────────────────────────────────────────────────
step("提升为招聘方（验证角色鉴权链路）")
recruiter_token = ensure_recruiter(E2E_EMAIL, E2E_PASSWORD)
check("拿到 RECRUITER 令牌", bool(recruiter_token))
if recruiter_token:
    TOKEN = recruiter_token

step("HR 洞察报告（归因 + 验证计划）")
r = call("POST", "/api/v1/hr/insight",
         {"resumeId": RESUME_ID, "jdId": JD_ID, "maxQuestions": 12})
check("洞察任务已创建", r.get("code") == 0, "(code=%s msg=%s)" % (r.get("code"), r.get("message")))
INSIGHT_ID = (r.get("data") or {}).get("insightId")
check("拿到 insightId", bool(INSIGHT_ID), "(%s)" % INSIGHT_ID)

report = {}


def insight_ready():
    d = call("GET", "/api/v1/hr/insight/%s" % INSIGHT_ID)
    data = d.get("data") or {}
    return data if data.get("report") else None


ins = wait_for(insight_ready, timeout=120)
check("报告生成完成", bool(ins))
if ins:
    report = ins["report"]
    print("       gap=%s 维度=%s 问题=%s"
          % (report.get("gap_count"), report.get("dimension_count"),
             report.get("question_count")))
    check("有归因结论", (report.get("gap_count") or 0) > 0)
    check("有验证问题", (report.get("question_count") or 0) > 0)
    check("免责声明由后端拼接", "辅助建议" in (report.get("disclaimer") or ""),
          repr((report.get("disclaimer") or "")[:40]))
    check("审计说明存在", bool(report.get("audit_note")))
    for q in report.get("verification_plan") or []:
        check("%s 有 intent" % q["id"], bool(q.get("intent")))
        check("%s 有追问" % q["id"], len(q.get("probes") or []) >= 1)
        check("%s 有三档判定信号" % q["id"],
              {s["verdict"] for s in q.get("signals") or []}
              == {"demonstrated", "adjacent", "not_evidenced"})
        check("%s 未泄露缺口" % q["id"],
              not any(w in q.get("question", "") for w in ("简历", "JD", "岗位要求")))
        check("%s 不是二元问法" % q["id"],
              not any(w in q.get("question", "") for w in ("会不会", "熟悉", "了解", "掌握")))

# ── 6. 报告列表 ─────────────────────────────────────────────────
step("我的报告列表")
r = call("GET", "/api/v1/hr/insight/list?page=1&size=20")
check("列表接口正常", r.get("code") == 0)
check("列表里有数据", len((r.get("data") or {}).get("items") or []) >= 1)

# ── 7. 追问判定 ─────────────────────────────────────────────────
step("提交面试官录入的回答 → 覆盖判定")
vq = (report.get("verification_plan") or [{}])[0].get("id")
check("取到验证问题", bool(vq), "(%s)" % vq)
r = call("POST", "/api/v1/hr/verify/answer",
         {"insightId": INSIGHT_ID, "vqId": vq,
          "answer": "我们负责自建集群的搭建，扩容和节点排障都做过，发布走灰度流水线，版本升级也有预案",
          "roundNo": 1})
check("判定任务已创建", r.get("code") == 0, "(code=%s msg=%s)" % (r.get("code"), r.get("message")))
record_id = (r.get("data") or {}).get("recordId")
check("拿到 recordId", bool(record_id))


def verdict_ready():
    recs = call("GET", "/api/v1/hr/verify/records/%s" % INSIGHT_ID)
    items = recs.get("data") or []
    # 记录行是提交时同步建的，判定结果由回调异步写入 ——
    # 必须等 coverage 有值才算完成，否则会读到「还没判定」的半成品。
    if items and items[0].get("coverage"):
        return items[0]
    return None


v = wait_for(verdict_ready, timeout=90)
check("判定结果已落库", bool(v))
if v:
    print("       coverage=%s confidence=%s" % (v.get("coverage"), v.get("confidence")))
    check("判定值合法",
          v.get("coverage") in ("demonstrated", "adjacent", "not_evidenced"))
    check("有原文引用", bool(v.get("evidenceRef")))
    check("判定理由不含禁用词",
          not any(w in (v.get("reason") or "")
                  for w in ("录用", "淘汰", "不适合", "不合格")))

# ── 8. JD 校准 ─────────────────────────────────────────────────
step("JD 反向校准")
# 这份 JD 刻意凑齐「学历 + 年限 + 资质」三道硬门槛，
# 用来验证过度筛选信号能被识别出来。
CALIBRATE_JD = (
    "高级后端工程师（Kubernetes / CI/CD 方向）\n"
    "负责核心交易系统的架构设计与服务拆分，主导 CI/CD 流水线建设与生产环境发布。\n"
    "要求 5 年以上后端开发经验，本科及以上学历，熟悉分布式系统与微服务治理。\n"
    "有高并发、高可用系统设计经验者优先，熟悉 Redis 与 MySQL 索引优化。\n"
    "具备良好的沟通能力与团队协作精神，PMP 认证者优先。"
)
r = call("POST", "/api/v1/ai-tasks",
         {"taskType": "jd_calibrate", "bizId": None,
          "input": {"jd_text": CALIBRATE_JD, "job_title": "高级后端工程师"}})
check("校准任务已创建", r.get("code") == 0, "(code=%s msg=%s)" % (r.get("code"), r.get("message")))
task_id = (r.get("data") or {}).get("taskId")
check("拿到 taskId", bool(task_id))


def calibrate_done():
    t = call("GET", "/api/v1/ai-tasks/%s" % task_id)
    data = t.get("data") or {}
    if data.get("status") == "SUCCESS":
        return data.get("output")
    if data.get("status") == "FAILED":
        return {"__failed__": data.get("errorMessage")}
    return None


cal = wait_for(calibrate_done, timeout=90)
check("JD 校准完成", bool(cal) and "__failed__" not in (cal or {}),
      "(error=%s)" % (cal or {}).get("__failed__"))
if cal and "__failed__" not in cal:
    print("       硬门槛=%s 过度筛选=%s 模糊表述=%s"
          % (len(cal.get("hard_gates") or []),
             len(cal.get("over_filtering_signals") or []),
             len(cal.get("vague_phrases") or [])))
    check("识别出模糊表述(>=2)", len(cal.get("vague_phrases") or []) >= 2)
    check("识别出过度筛选信号(>=1)", len(cal.get("over_filtering_signals") or []) >= 1)
    check("有免责声明", "辅助建议" in (cal.get("disclaimer") or ""))

print("\n" + "=" * 60)
print("结果：%d 通过 / %d 失败" % (PASSED, FAILED))
sys.exit(1 if FAILED else 0)
