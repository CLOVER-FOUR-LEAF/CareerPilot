r"""
造一份可验收的演示数据。

用途：验收时不必从零建 JD / 简历 / 报告 ——
直接用这个脚本建好账号、JD、简历，并**预生成一份 HR 洞察报告**，
打开页面就能看到完整的六分区报告。

用法
----
    python scripts\seed_demo_data.py
    python scripts\seed_demo_data.py --email hr@demo.local --password Abcd1234

默认账号（不存在时才创建，已存在则直接复用）：
    邮箱    hr@demo.local
    密码    Abcd1234
"""
import json
import os
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

GW = os.environ.get("CAREERPILOT_GW", "http://127.0.0.1:8080")
EMAIL = "hr@demo.local"
PASSWORD = "Abcd1234"

# 管理端引导账号（scripts/local/linux/bootstrap-admin.sh 创建）
ADMIN_EMAIL = os.environ.get("CP_ADMIN_EMAIL", "admin@demo.local")
ADMIN_PASSWORD = os.environ.get("CP_ADMIN_PASSWORD", "Admin1234")

JD_TEXT = (
    "高级后端工程师（Kubernetes / CI/CD 方向）\n"
    "负责公司核心交易系统的架构设计与服务拆分，主导 CI/CD 流水线建设与生产环境发布。\n"
    "要求 5 年以上后端开发经验，本科及以上学历，熟悉分布式系统与微服务治理。\n"
    "有高并发、高可用系统设计经验者优先，熟悉 Redis 与 MySQL 索引优化。\n"
    "具备良好的沟通能力与团队协作精神，PMP 认证者优先。"
)

RESUME_TEXT = (
    "张三 · 后端工程师\n"
    "2019-2023 某电商公司 后端工程师\n"
    "负责订单系统后端开发，参与微服务拆分，主导 CI/CD 流水线建设与日常发布。\n"
    "负责 Redis 缓存设计与 MySQL 索引优化，支撑日订单百万级。\n"
    "2023-至今 某支付公司 后端专家\n"
    "负责支付网关服务设计，独立完成限流熔断方案，参与服务治理。\n"
    "跨部门推动多个需求落地，负责技术方案评审与团队协作。\n"
)


def call(method, path, payload=None, token=None, raw=None, content_type=None):
    if token is None:
        token = globals().get("TOKEN")
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
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw_body = e.read().decode("utf-8", "replace")
        try:
            return json.loads(raw_body)
        except ValueError:
            return {"code": e.code, "message": raw_body[:300], "data": None}


def multipart(path, file_field, filename, title):
    boundary = "----CareerPilotDemo" + str(int(time.time() * 1000))
    with open(path, "rb") as fh:
        content = fh.read()
    parts = [
        ("--%s\r\n" % boundary).encode(),
        ('Content-Disposition: form-data; name="%s"; filename="%s"\r\n'
         % (file_field, filename)).encode("utf-8"),
        b"Content-Type: text/plain\r\n\r\n",
        content, b"\r\n",
        ("--%s\r\n" % boundary).encode(),
        ('Content-Disposition: form-data; name="title"\r\n\r\n').encode("utf-8"),
        title.encode("utf-8"), b"\r\n",
        ("--%s--\r\n" % boundary).encode(),
    ]
    return b"".join(parts), "multipart/form-data; boundary=%s" % boundary


def wait_for(fn, timeout=120, interval=2):
    deadline = time.time() + timeout
    while time.time() < deadline:
        r = fn()
        if r:
            return r
        time.sleep(interval)
    return None


def ensure_recruiter(email, password):
    """
    演示账号必须是 RECRUITER，否则 /api/v1/hr/** 会被网关按角色拒绝。

    通过管理端接口提升角色，再重新登录 —— 角色写在 accessToken 里，
    不重新登录的话旧令牌仍然是 USER。
    """
    r = call("POST", "/api/v1/auth/login",
             {"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD}, token="")
    admin_token = (r.get("data") or {}).get("accessToken")
    if not admin_token:
        print("管理员账号不可用：%s（先跑 scripts/local/linux/bootstrap-admin.sh）" % ADMIN_EMAIL)
        return None

    listing = call("GET",
                   "/api/v1/admin/users?page=1&size=100&keyword=" + urllib.parse.quote(email),
                   token=admin_token)
    records = ((listing.get("data") or {}).get("records") or [])
    target = next((x for x in records if x.get("email") == email), None)
    if not target:
        print("管理端查不到账号：%s" % email)
        return None

    if target.get("role") != "RECRUITER":
        updated = call("PUT", "/api/v1/admin/users/%s/role" % target["id"],
                       {"role": "RECRUITER"}, token=admin_token)
        if updated.get("code") != 0:
            print("提升 RECRUITER 失败：%s" % updated.get("message"))
            return None
        print("已将 %s 提升为 RECRUITER" % email)

    relogin = call("POST", "/api/v1/auth/login",
                   {"email": email, "password": password}, token="")
    return (relogin.get("data") or {}).get("accessToken")


def main():
    global TOKEN

    # 1. 账号：先试登录，失败就注册
    r = call("POST", "/api/v1/auth/login", {"email": EMAIL, "password": PASSWORD})
    if r.get("code") == 0:
        print("账号已存在，直接登录：%s" % EMAIL)
    else:
        r = call("POST", "/api/v1/auth/register",
                 {"email": EMAIL, "password": PASSWORD, "nickname": "HR 演示", "inviteCode": None})
        if r.get("code") != 0:
            print("创建账号失败：%s" % json.dumps(r, ensure_ascii=False)[:200])
            return 1
        print("已创建账号：%s / %s" % (EMAIL, PASSWORD))
    TOKEN = (r.get("data") or {}).get("accessToken")
    if not TOKEN:
        print("拿不到令牌")
        return 1

    # 1.5 角色：演示账号要能访问 /api/v1/hr/**
    recruiter_token = ensure_recruiter(EMAIL, PASSWORD)
    if not recruiter_token:
        print("无法把演示账号提升为 RECRUITER，HR 报告将无法生成")
        return 1
    TOKEN = recruiter_token

    # 2. JD
    r = call("POST", "/api/v1/jd/upload",
             {"jobTitle": "高级后端工程师", "companyName": "某电商",
              "rawText": JD_TEXT, "sourceType": "MANUAL"})
    jd_id = (r.get("data") or {}).get("jdId")
    if not jd_id:
        print("创建 JD 失败：%s" % json.dumps(r, ensure_ascii=False)[:200])
        return 1
    print("JD 已创建：jdId=%s" % jd_id)

    # 3. 简历
    tmp = os.path.join(tempfile.gettempdir(), "demo_resume.txt")
    with open(tmp, "w", encoding="utf-8") as fh:
        fh.write(RESUME_TEXT)
    body, ctype = multipart(tmp, "file", "张三-后端-简历.txt", "张三-后端-简历.txt")
    r = call("POST", "/api/v1/resume/upload", raw=body, content_type=ctype)
    resume_id = (r.get("data") or {}).get("resumeId")
    if not resume_id:
        print("上传简历失败：%s" % json.dumps(r, ensure_ascii=False)[:200])
        return 1
    print("简历已上传：resumeId=%s" % resume_id)

    # 4. 等 JD 解析 + 简历 ATS
    wait_for(lambda: (call("GET", "/api/v1/jd/%s/parse/result" % jd_id).get("data") or {}).get("skills"),
              timeout=90)
    wait_for(lambda: (call("GET", "/api/v1/resume/%s/ats-score" % resume_id).get("data") or {}).get("overall_score"),
              timeout=90)
    print("JD 解析与 ATS 评分已完成")

    # 5. 预生成 HR 洞察报告
    r = call("POST", "/api/v1/hr/insight",
             {"resumeId": resume_id, "jdId": jd_id, "maxQuestions": 12})
    insight_id = (r.get("data") or {}).get("insightId")
    if not insight_id:
        print("生成 HR 报告失败：%s" % json.dumps(r, ensure_ascii=False)[:200])
        return 1
    print("HR 报告任务已创建：insightId=%s" % insight_id)

    def ready():
        d = call("GET", "/api/v1/hr/insight/%s" % insight_id).get("data") or {}
        return d if d.get("report") else None

    ins = wait_for(ready, timeout=150)
    if not ins:
        print("HR 报告未在超时前生成")
        return 1
    rep = ins["report"]
    print("HR 报告已生成：gap=%s 维度=%s 问题=%s"
          % (rep.get("gap_count"), rep.get("dimension_count"), rep.get("question_count")))

    print("")
    print("=" * 60)
    print("演示数据就绪。登录信息：")
    print("    邮箱：%s" % EMAIL)
    print("    密码：%s" % PASSWORD)
    print("    入口：http://localhost:3000/hr/insight")
    print("    管理端：http://localhost:3100（admin@demo.local / Admin1234）")
    print("=" * 60)
    return 0


if __name__ == "__main__":
    sys.exit(main())
