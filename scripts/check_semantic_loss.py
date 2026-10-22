"""
语义丢失检查（独立脚本）。

背景
----
项目的 verify_contract.py 只能查出「前端传了 Agent 根本不认识的字段」。
但 D1 这类更隐蔽的问题它查不出来：

    前端传了 jd_id / jd_text
        → 编排层原样透传
        → Agent 的 Pydantic 模型只声明了 bullet / jd_skills / tone
        → Pydantic v2 默认 extra='ignore'，多出来的键被**静默丢弃**
        → Agent 以为拿到了 JD，实际 jd_skills 恒为 []
        → 功能悄悄退化成了兜底文案

也就是说：字段「传了」，但「没人消费」，语义在半路蒸发了。
verify_contract.py 里 ALLOWED_EXTRA_KEYS 把 jd_id / jd_text / jd_skills
都放行了，所以这个缺陷在契约检查里是全绿的。

本脚本要查的正是这一类：**Agent 声明了但前端没传的字段**，
以及**前端传了但 Agent 既没声明、也不属于编排层路由字段的字段**。

用法
----
    python scripts/check_semantic_loss.py

退出码 0 表示通过，1 表示存在语义丢失。
"""
from __future__ import annotations

import ast
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
AGENTS = ROOT / "agents"
WEB_SRC = ROOT / "web" / "src"

# 虚拟环境 / 构建产物里的第三方代码不归我们管。
SKIP_DIRS = {".venv", "venv", "__pycache__", "node_modules", "target", ".git", ".runtime"}


def project_files(root: Path, pattern: str) -> list[Path]:
    return sorted(
        p for p in root.rglob(pattern)
        if not any(part in SKIP_DIRS for part in p.relative_to(root).parts)
    )


# ── 契约表 ────────────────────────────────────────────────────────
# taskType → (Agent 目录名, Agent 侧语义相关的字段)
#
# 「语义相关字段」的定义：Agent 声明了它、并且 Agent 的业务逻辑真的会用它。
# 这些字段前端必须传，否则功能会静默退化（而不是报错）。
# 比如 resume_optimize 的 jd_skills —— 不传则 injected_skills 恒为空。
SEMANTIC_FIELDS: dict[str, tuple[str, set[str]]] = {
    "resume_optimize": ("resume-optimizer-agent", {"jd_skills"}),
    "cover_letter": ("cover-letter-agent", {"jd_skills"}),
    "interview": ("interview-agent", {"jd_skills"}),
}

# taskType → 编排层会消费、但 Agent 不需要的路由性字段。
# 这些键由编排层/网关消费（定位业务对象、串联链路），Agent 不声明它们是合理的，
# 因此不计入语义丢失。
ROUTING_ONLY_KEYS: dict[str, set[str]] = {
    "resume_optimize": {"jd_id", "jd_text", "biz_id"},
    "cover_letter": {"jd_id", "jd_text", "biz_id"},
    "interview": {"jd_id", "jd_text", "resume_id", "resume_text", "phase", "biz_id"},
}

# taskType → 前端页面文件名（用于报错时指明位置）
PAGE_OF_TASK: dict[str, str] = {
    "resume_optimize": "Optimize.vue",
    "cover_letter": "CoverLetter.vue",
    "interview": "Interview.vue",
}

TASK_TYPE_TO_AGENT: dict[str, str] = {
    "jd_parse": "jd-parser-agent",
    "resume_analyze": "resume-analyzer-agent",
    "match_score": "match-agent",
    "resume_optimize": "resume-optimizer-agent",
    "interview": "interview-agent",
    "cover_letter": "cover-letter-agent",
    "career_plan": "career-advisor-agent",
}


def fail(msg: str, errors: list[str]) -> None:
    errors.append(msg)


# ── Agent 侧：读 Pydantic 请求模型声明的字段 ──────────────────────

def collect_request_fields(agent_dir: Path) -> dict[str, set[str]]:
    """{Request 类名: {字段名}} —— 从 AST 读，不执行代码。"""
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


def accepted_fields(agent_dir: Path) -> set[str]:
    """该 Agent 所有 Request 模型的字段并集。"""
    accepted: set[str] = set()
    for class_name, fields in collect_request_fields(agent_dir).items():
        if class_name.endswith("Request"):
            accepted |= fields
    return accepted


# ── 前端侧：抓每个 taskType 提交的 input 键 ────────────────────────

def _keys_in_object_literal(body: str) -> set[str]:
    """
    只取「行首的 key:」形式的键。

    不能用宽松正则扫 —— 那会把 `bullet: original,` 里的值 `original`
    也当成键报成「Agent 不认识」。只认行首能避开值与字符串字面量。
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

        for match in re.finditer(r"taskType:\s*'([a-z_]+)'", text):
            task_type = match.group(1)
            if task_type not in TASK_TYPE_TO_AGENT:
                continue

            tail = text[match.end():match.end() + 1200]
            block = re.search(r"input:\s*\{", tail)
            if not block:
                continue

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

            # 展开 ...context 这类透传：回 buildContext() 把键收全
            if re.search(r"\.\.\.\w+", body):
                ctx = re.search(r"async function buildContext[^{]*\{(.*?)\n\}", text, re.S)
                if ctx:
                    ctx_body = ctx.group(1)
                    keys |= set(re.findall(r"^\s*([a-z_][a-z0-9_]*)\s*:", ctx_body, re.M))
                    keys |= set(re.findall(r"^\s*(?:context|ctx)\.([a-z_][a-z0-9_]*)\s*=", ctx_body, re.M))

            usage.setdefault(task_type, set()).update(keys)

    return usage


# ── 检查 ──────────────────────────────────────────────────────────

def check(errors: list[str]) -> None:
    usage = collect_frontend_inputs()

    for task_type, (agent, semantic_fields) in SEMANTIC_FIELDS.items():
        page = PAGE_OF_TASK.get(task_type, "?")
        agent_dir = AGENTS / agent
        if not agent_dir.is_dir():
            fail(f"{task_type}: 缺少 Agent 目录 agents/{agent}", errors)
            continue

        accepted = accepted_fields(agent_dir)
        if not accepted:
            fail(f"{task_type} ({agent}): 没有解析出任何 Request 模型字段", errors)
            continue

        sent = usage.get(task_type, set())
        if not sent:
            fail(f"{task_type} ({page}): 前端没有找到任何 taskType='{task_type}' 的提交点", errors)
            continue

        # ① 语义关键字段：Agent 声明了、前端却没传 → 恒为空，功能静默退化
        missing = semantic_fields - sent
        for field in sorted(missing):
            fail(
                f"{page} 提交 {task_type} 时没有传 `{field}`，"
                f"但 {agent} 依赖它 → 该字段恒为空，语义在链路上丢失",
                errors,
            )

        # ② 前端多传的键：既不是 Agent 声明的字段，也不是编排层路由字段
        #    → Pydantic extra='ignore' 会把它静默丢掉
        routing = ROUTING_ONLY_KEYS.get(task_type, set())
        unknown = {k for k in sent if k not in accepted and k not in routing}
        for field in sorted(unknown):
            fail(
                f"{page} 提交 {task_type} 时传了 `{field}`，"
                f"但 {agent} 既不声明它、它也不是编排层路由字段 → 会被静默丢弃",
                errors,
            )


# ── main ──────────────────────────────────────────────────────────

def main() -> int:
    errors: list[str] = []
    check(errors)

    print("语义丢失检查（前端 → Agent）")
    print(f"  检查 taskType：{', '.join(sorted(SEMANTIC_FIELDS))}")
    print(f"  检查页面    ：{', '.join(PAGE_OF_TASK[t] for t in sorted(PAGE_OF_TASK))}")

    if errors:
        print("\n发现语义丢失：")
        for e in errors:
            print(f"  - {e}")
        return 1

    print("\n未发现语义丢失。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
