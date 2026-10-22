"""校验 Markdown 文档的内部锚点链接是否都能解析（GitHub slug 规则）。

用法：
    python scripts/check_doc_anchors.py [文档路径...]
不传参数时默认检查 docs/API-FRONTEND.md。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DOCS = [ROOT / "docs" / "API-FRONTEND.md"]

docs = [Path(a) if Path(a).is_absolute() else ROOT / a for a in sys.argv[1:]] or DEFAULT_DOCS


def slugify(text: str) -> str:
    """GitHub slug：转小写 → 去掉标点（保留 CJK/字母/数字/连字符/空格/下划线）→ 空格转连字符"""
    s = text.strip().lower()
    s = re.sub(r"[^\w\s\-]", "", s, flags=re.UNICODE)  # 去标点（含 emoji、全角括号、→、：）
    s = s.replace(" ", "-")
    return s


def count_cols(line: str) -> int:
    """表格列数：单元格里的转义竖线 \\| 是合法 Markdown，不算列分隔符"""
    return re.sub(r"\\\|", "", line).count("|")


failed = False

for doc in docs:
    if not doc.exists():
        print(f"[X] 文件不存在: {doc}")
        failed = True
        continue

    text = doc.read_text(encoding="utf-8")
    rel = doc.relative_to(ROOT) if ROOT in doc.parents else doc

    headings = re.findall(r"^#{1,6}\s+(.+?)\s*$", text, flags=re.MULTILINE)
    slugs = {}
    for h in headings:
        slugs.setdefault(slugify(h), h)

    links = re.findall(r"\]\(#([^)]+)\)", text)
    bad = [l for l in links if l not in slugs]

    print(f"── {rel}")
    print(f"   标题数: {len(headings)}  唯一 slug: {len(slugs)}  内部链接: {len(links)}")

    if bad:
        failed = True
        print(f"   [X] 断链 {len(bad)} 个:")
        for b in sorted(set(bad)):
            print(f"       #{b}")
    else:
        print("   [OK] 全部内部锚点可解析")

    # 表格列数一致性
    lines = text.split("\n")
    in_fence = False
    issues = 0
    i = 0
    while i < len(lines):
        line = lines[i]
        if line.strip().startswith("```"):
            in_fence = not in_fence
            i += 1
            continue
        if not in_fence and line.strip().startswith("|") and i + 1 < len(lines) \
                and re.match(r"^\|[\s:|-]+\|$", lines[i + 1].strip()):
            cols = count_cols(line)
            j = i + 2
            while j < len(lines) and lines[j].strip().startswith("|"):
                if count_cols(lines[j]) != cols:
                    print(f"   [!] 表格列数不一致 @行 {j + 1}: {lines[j][:70]}")
                    issues += 1
                j += 1
            i = j
            continue
        i += 1
    print(f"   表格列数问题: {issues} 处")
    if issues:
        failed = True

sys.exit(1 if failed else 0)
