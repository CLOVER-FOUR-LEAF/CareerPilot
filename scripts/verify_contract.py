"""
CareerPilot 契约一致性检查。

项目里已经因为「前端发的字段名 ≠ Agent 接收的字段名」吃过亏
（original/bullet、company/company_name、漏传 total_rounds），
所以把这件事变成可重复执行的检查，而不是靠人肉比对。

检查三件事：
1. agents/ 下所有 .py 语法正确、无乱码字符；
2. 每个 Agent 的 Pydantic 请求模型字段，能在 docs/API-CONTRACT.md 的
   「入参」代码块里找到对应说明；
3. web/ 里每个 AI 页面向 /ai-tasks 提交的 input 键，是该 Agent 真实接受的字段
   （按 taskType 分组比对，多出来的键只在契约显式声明时才放行）。

用法：python scripts/verify_contract.py
退出码 0 表示通过。
"""
from __future__ import annotations

import ast
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
AGENTS = ROOT / "agents"
WEB_SRC = ROOT / "web" / "src"
CONTRACT = ROOT / "docs" / "API-CONTRACT.md"

# 只检查我们自己写的源码。虚拟环境 / 构建产物里的第三方代码不归我们管，
# 而且里面常带 U+FFFD 之类的字符，会把检查结果冲成一片假阳性。
SKIP_DIRS = {".venv", "venv", "__pycache__", "node_modules", "target", ".git", ".runtime"}


def project_files(root: Path, pattern: str) -> list[Path]:
    """遍历项目源码，跳过虚拟环境与构建目录。"""
    return sorted(
        p for p in root.rglob(pattern)
        if not any(part in SKIP_DIRS for part in p.relative_to(root).parts)
    )

# 前端可以多传、但 Agent 端不消费也不校验的键（契约里已声明为辅助信息）
ALLOWED_EXTRA_KEYS = {
    "jd_id", "jd_text", "resume_id", "resume_text",
    "phase", "round_no", "total_rounds", "previous_answer",
    "question", "answer", "tone", "job_title", "company_name",
    "bullet", "jd_skills", "resume_highlights",
    "current_title", "target_industry", "target_position",
    "years_of_experience", "skills",
}

TASK_TYPE_TO_AGENT = {
    "jd_parse": "jd-parser-agent",
    "resume_analyze": "resume-analyzer-agent",
    "match_score": "match-agent",
    "resume_optimize": "resume-optimizer-agent",
    "interview": "interview-agent",
    "cover_letter": "cover-letter-agent",
    "career_plan": "career-advisor-agent",
}

# taskType → (agent 内 endpoint 路径, 对应的 Request 类名)
ENDPOINT_TO_REQUEST = {
    "/api/parse": "ParseRequest",
    "/api/analyze": "AnalyzeRequest",
    "/api/match": "MatchRequest",
    "/api/optimize": "OptimizeRequest",
    "/api/question": "QuestionRequest",
    "/api/evaluate": "EvaluateRequest",
    "/api/generate": "GenerateRequest",
    "/api/plan": "PlanRequest",
}


def fail(msg: str, errors: list[str]) -> None:
    errors.append(msg)


# ── 1. 语法与编码 ───────────────────────────────────────────────

def check_python(errors: list[str]) -> int:
    files = project_files(AGENTS, "*.py")
    for path in files:
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            fail(f"{path.relative_to(ROOT)}: 不是合法 UTF-8", errors)
            continue
        try:
            ast.parse(text)
        except SyntaxError as exc:
            fail(f"{path.relative_to(ROOT)}:{exc.lineno}: 语法错误 {exc.msg}", errors)
        if "\ufffd" in text:
            fail(f"{path.relative_to(ROOT)}: 含乱码替换字符 U+FFFD", errors)
    return len(files)


# ── 2. Agent 请求模型 ───────────────────────────────────────────

def collect_request_fields(agent_dir: Path) -> dict[str, set[str]]:
    """{Request 类名: {字段名}} —— 直接从 AST 读，不执行代码。"""
    found: dict[str, set[str]] = {}
    for py in project_files(agent_dir, "*.py"):
        try:
            tree = ast.parse(py.read_text(encoding="utf-8"))
        except (SyntaxError, UnicodeDecodeError):
            continue
        for node in ast.walk(tree):
            if not isinstance(node, ast.ClassDef):
                continue
            for stmt in node.body:
                if isinstance(stmt, ast.AnnAssign) and isinstance(stmt.target, ast.Name):
                    found.setdefault(node.name, set()).add(stmt.target.id)
    return found


def check_agents(errors: list[str]) -> None:
    for task_type, agent in TASK_TYPE_TO_AGENT.items():
        agent_dir = AGENTS / agent
        if not agent_dir.is_dir():
            fail(f"{task_type}: 缺少 Agent 目录 agents/{agent}", errors)
            continue

        fields_by_class = collect_request_fields(agent_dir)
        if not fields_by_class:
            fail(f"{agent}: 没有找到任何 Pydantic 请求模型", errors)


# ── 3. 前端提交键 vs Agent 接受键 ───────────────────────────────

def _keys_in_object_literal(body: str) -> set[str]:
    """
    只取「行首的 key:」形式的键。

    不能用宽松的正则去扫 —— 那会把 `bullet: original,` 里的值 `original`
    也当成键报成「Agent 不认识」。只认行首能避开值、字符串字面量
    和 `context.jd_text = ...` 这类赋值语句。
    """
    keys: set[str] = set()
    for line in body.splitlines():
        match = re.match(r"\s*([a-z_][a-z0-9_]*)\s*:", line)
        if match:
            keys.add(match.group(1))
    return keys


def collect_frontend_inputs() -> dict[str, set[str]]:
    """{taskType: {input 里出现的键}}。按 `taskType: 'x'` 就近找 input 对象。"""
    usage: dict[str, set[str]] = {}

    for vue in project_files(WEB_SRC, "*.vue"):
        text = vue.read_text(encoding="utf-8")

        # 抓 taskType: 'xxx' 的位置，再往后找最近的 input: { ... } 块
        for match in re.finditer(r"taskType:\s*'([a-z_]+)'", text):
            task_type = match.group(1)
            if task_type not in TASK_TYPE_TO_AGENT:
                continue

            tail = text[match.end():match.end() + 800]
            block = re.search(r"input:\s*\{", tail)
            if not block:
                continue

            # 从 '{' 开始做括号配平，拿到完整对象字面量
            start = tail.index("{", block.start())
            depth = 0
            end = start
            for idx in range(start, len(tail)):
                if tail[idx] == "{":
                    depth += 1
                elif tail[idx] == "}":
                    depth -= 1
                    if depth == 0:
                        end = idx
                        break
            body = tail[start + 1:end]

            keys = _keys_in_object_literal(body)

            # 展开 ...context 这类透传：去 buildContext() 里把键收全
            if re.search(r"\.\.\.\w+", body):
                ctx = re.search(r"async function buildContext[^{]*\{(.*?)\n\}", text, re.S)
                if ctx:
                    ctx_body = ctx.group(1)
                    # buildContext 里既有 `key: value` 也有 `obj.key = value` 两种写法
                    keys |= set(re.findall(r"^\s*([a-z_][a-z0-9_]*)\s*:", ctx_body, re.M))
                    keys |= set(re.findall(r"^\s*(?:context|ctx)\.([a-z_][a-z0-9_]*)\s*=", ctx_body, re.M))

            usage.setdefault(task_type, set()).update(keys)

    return usage


def check_frontend(errors: list[str]) -> None:
    usage = collect_frontend_inputs()
    for task_type, keys in sorted(usage.items()):
        agent = TASK_TYPE_TO_AGENT[task_type]
        fields_by_class = collect_request_fields(AGENTS / agent)
        if not fields_by_class:
            continue

        # 该 Agent 所有 Request 模型的字段并集（interview 有 question/evaluate 两个模型）
        accepted: set[str] = set()
        for class_name, model_fields in fields_by_class.items():
            if class_name.endswith("Request"):
                accepted |= model_fields

        unknown = {k for k in keys if k not in accepted and k not in ALLOWED_EXTRA_KEYS}
        if unknown:
            fail(
                f"{task_type} ({agent}): 前端提交了 Agent 不认识的字段 {sorted(unknown)}\n"
                f"    Agent 接受: {sorted(accepted)}",
                errors,
            )


# ── 4. 契约文档在场 ─────────────────────────────────────────────

def check_contract(errors: list[str]) -> None:
    if not CONTRACT.is_file():
        fail("缺少 docs/API-CONTRACT.md", errors)
        return
    text = CONTRACT.read_text(encoding="utf-8")
    for keyword in ("bullet", "company_name", "total_rounds", "final_score"):
        if keyword not in text:
            fail(f"docs/API-CONTRACT.md 未记录关键字段 `{keyword}`", errors)


# ── main ────────────────────────────────────────────────────────

def main() -> int:
    errors: list[str] = []

    count = check_python(errors)
    print(f"[1/4] Python 语法与编码检查：{count} 个文件")

    check_agents(errors)
    print("[2/4] Agent 请求模型检查：8 个 Agent")

    usage = collect_frontend_inputs()
    check_frontend(errors)
    print(f"[4/4] 前端 AI 提交键检查：覆盖 {len(usage)} 个 taskType")

    check_contract(errors)
    print("[3/4] 契约文档检查")

    if errors:
        print("\n发现问题：")
        for e in errors:
            print(f"  - {e}")
        return 1

    print("\n全部通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
