"""
禁用词扫描（合规自检，计划书 §8.3 检查 C-1）。

为什么需要脚本
--------------
「输出不能出现录用/淘汰/不适合这类词」这条约束，
写在文档里没用 —— 改代码的人不会去翻文档，
而模型在长上下文里也保不准不越界。必须变成**能自动跑的检查**。

扫什么
------
1. **已生成的报告**：t_hr_insight.report_json 里的实际产出
2. **源码里的输出文案**：HR 模块与前端里的字符串常量
   （这是「未来会生成什么」的静态检查）
3. **Markdown 报告**：scripts/ 下生成的报告文件（如有）

不扫什么
--------
- 禁用词清单本身（compliance.py 里的 FORBIDDEN_TERMS）
- 本脚本
- 测试文件与 ADR 文档（它们**故意**引用这些词来做断言与说明）

用法
----
    python scripts/check_forbidden_terms.py

退出码 0 表示通过，1 表示存在越界输出。
"""
from __future__ import annotations

import ast
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKIP_DIRS = {".venv", "venv", "__pycache__", "node_modules", "target", ".git", ".runtime"}

# 禁用词清单。与 agents/interview-agent/app/hr/compliance.py 保持一致。
# 这里刻意**独立维护**而不是 import —— 脚本要能独立运行，
# 不能因为某个 Agent 的虚拟环境没建好就跑不起来。
FORBIDDEN_TERMS: tuple[str, ...] = (
    "建议录用",
    "建议不录用",
    "建议淘汰",
    "淘汰",
    "不适合",
    "不合格",
    "通过筛选",
    "未通过筛选",
    "录用",
    "拒绝",
)

# 英文词按词边界匹配，避免 "failed" 命中 "fail"
FORBIDDEN_ASCII: tuple[str, ...] = ("pass", "fail")

# 允许出现这些词的路径（它们就是清单的出处或故意引用这些词做断言）
ALLOWED_PATH_PARTS: tuple[str, ...] = (
    "compliance.py",          # 禁用词清单的定义处
    "check_forbidden_terms.py",  # 本脚本
)


def find_forbidden(text: str) -> list[str]:
    """找出文本里的禁用词（中文按子串，英文按词边界）。"""
    if not text:
        return []
    hits = [term for term in FORBIDDEN_TERMS if term in text]
    lowered = text.lower()
    for term in FORBIDDEN_ASCII:
        idx = 0
        while True:
            idx = lowered.find(term, idx)
            if idx == -1:
                break
            before = lowered[idx - 1] if idx > 0 else " "
            after = lowered[idx + len(term)] if idx + len(term) < len(lowered) else " "
            if not before.isalnum() and not after.isalnum():
                hits.append(term)
            idx += 1
    return hits


def _is_excluded(path: Path) -> bool:
    if any(part in SKIP_DIRS for part in path.parts):
        return True
    if any(part in ALLOWED_PATH_PARTS for part in path.parts):
        return True
    # 测试文件与文档故意引用这些词
    name = path.name
    if name.startswith("test_") or path.suffix.lower() == ".md":
        return True
    return False


def _iter_files(roots: list[Path], suffixes: tuple[str, ...]):
    for root in roots:
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in suffixes:
                continue
            if _is_excluded(path.relative_to(ROOT)):
                continue
            yield path


# ── 1. 源码里的字符串常量 ───────────────────────────────────────

def check_source_strings() -> list[str]:
    """
    扫描 Python / Vue / TS 源码里的**输出文案**。

    刻意跳过两类字符串：

    1. **docstring** —— 代码说明，不是输出
    2. **prompt 常量**（名字含 PROMPT）—— 提示词里**必须**出现这些词，
       用途是告诉模型「不要用这些词」。提示词本身不面向用户。

    真正要拦的是**会出现在报告/界面上的文案**。
    """
    problems: list[str] = []

    for path in _iter_files([ROOT / "agents"], (".py",)):
        try:
            tree = ast.parse(path.read_text(encoding="utf-8"))
        except (SyntaxError, UnicodeDecodeError):
            continue
        rel = path.relative_to(ROOT)
        docstring_nodes = _docstring_nodes(tree)
        prompt_nodes = _prompt_constant_nodes(tree)

        for node in ast.walk(tree):
            if not isinstance(node, ast.Constant) or not isinstance(node.value, str):
                continue
            if id(node) in docstring_nodes:
                continue
            if id(node) in prompt_nodes:
                continue
            hits = find_forbidden(node.value)
            if hits:
                problems.append(f"{rel}:{node.lineno}: 输出文案含禁用词 {sorted(set(hits))}")

    # Vue / TS：用引号配对粗略提取
    import re
    string_re = re.compile(r"(['\"])(?:\\.|(?!\1).)*\1", re.DOTALL)
    for path in _iter_files([ROOT / "web" / "src"], (".vue", ".ts")):
        text = path.read_text(encoding="utf-8", errors="replace")
        rel = path.relative_to(ROOT)
        for match in string_re.finditer(text):
            value = match.group(0)[1:-1]
            if not value.strip():
                continue
            hits = find_forbidden(value)
            if hits:
                line = text.count("\n", 0, match.start()) + 1
                problems.append(f"{rel}:{line}: 输出文案含禁用词 {sorted(set(hits))}")

    return problems


def _docstring_nodes(tree: ast.Module) -> set[int]:
    """收集所有 docstring 节点的 id（模块/类/函数的第一个字符串常量）。"""
    ids: set[int] = set()
    for node in ast.walk(tree):
        if isinstance(node, (ast.Module, ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
            body = getattr(node, "body", None)
            if body and isinstance(body[0], ast.Expr) and isinstance(body[0].value, ast.Constant):
                if isinstance(body[0].value.value, str):
                    ids.add(id(body[0].value))
    return ids


def _prompt_constant_nodes(tree: ast.Module) -> set[int]:
    """
    收集被赋给「名字里含 PROMPT 的变量」的字符串常量的 id。

    prompt 里必须出现禁用词（用途是禁止模型使用它们），
    所以要排除掉，否则检查会一直误报。
    """
    ids: set[int] = set()
    for node in ast.walk(tree):
        targets: list[ast.expr] = []
        value: ast.expr | None = None
        if isinstance(node, ast.Assign):
            targets = list(node.targets)
            value = node.value
        elif isinstance(node, ast.AnnAssign):
            targets = [node.target]
            value = node.value
        else:
            continue

        if not isinstance(value, ast.Constant) or not isinstance(value.value, str):
            continue
        for target in targets:
            if isinstance(target, ast.Name) and "PROMPT" in target.id.upper():
                ids.add(id(value))
    return ids


# ── 2. 已生成的报告（数据库）─────────────────────────────────────

def check_database() -> list[str]:
    """
    扫 t_hr_insight.report_json 里的实际产出。

    数据库不可用时跳过（本地环境可能没起 MySQL）。
    """
    problems: list[str] = []

    try:
        import pymysql  # type: ignore
    except ImportError:
        return problems

    host = os.environ.get("MYSQL_HOST", "127.0.0.1")
    port = int(os.environ.get("MYSQL_PORT", "3307"))
    user = os.environ.get("MYSQL_USER", "careerpilot")
    password = os.environ.get("MYSQL_PASSWORD", "cp123456")

    try:
        conn = pymysql.connect(host=host, port=port, user=user, password=password,
                               database="careerpilot", charset="utf8mb4")
    except Exception:
        return problems

    try:
        with conn.cursor() as cur:
            cur.execute("SELECT insight_id, report_json FROM t_hr_insight "
                        "WHERE report_json IS NOT NULL")
            for insight_id, report_json in cur.fetchall():
                hits = find_forbidden(str(report_json))
                if hits:
                    problems.append(f"t_hr_insight[{insight_id}]: 报告含禁用词 {sorted(set(hits))}")
    except Exception:
        pass
    finally:
        conn.close()

    return problems


# ── 3. 生成的 Markdown 报告 ──────────────────────────────────────

def check_markdown_outputs() -> list[str]:
    """扫 scripts/ 下生成的 Markdown 报告（如果存在）。"""
    problems: list[str] = []
    out_dir = ROOT / "scripts" / "eval_output"
    if not out_dir.exists():
        return problems
    for path in out_dir.glob("*.md"):
        hits = find_forbidden(path.read_text(encoding="utf-8", errors="replace"))
        if hits:
            problems.append(f"{path.relative_to(ROOT)}: 含禁用词 {sorted(set(hits))}")
    return problems


# ── main ────────────────────────────────────────────────────────

def main() -> int:
    problems: list[str] = []
    problems.extend(check_source_strings())
    problems.extend(check_database())
    problems.extend(check_markdown_outputs())

    print("禁用词扫描（合规自检 C-1）")
    print(f"  禁用词：{', '.join(FORBIDDEN_TERMS)}")
    print("  扫描范围：HR 模块与前端源码的输出文案、已生成的报告、Markdown 报告")

    if problems:
        print("\n发现越界输出：")
        for p in problems:
            print(f"  - {p}")
        return 1

    print("\n未发现越界输出。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
