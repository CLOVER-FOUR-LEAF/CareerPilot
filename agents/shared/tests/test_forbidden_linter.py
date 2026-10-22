"""
禁用词 linter 自身的单元测试。

一个「永远通过」的检查等于没有检查 —— 所以要验证它**能抓到**违规。
"""
import ast
import importlib.util
import sys
from pathlib import Path

_ROOT = Path(__file__).resolve().parents[3]      # 仓库根
_AGENTS = Path(__file__).resolve().parents[2]    # agents/
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

_SCRIPT = _ROOT / "scripts" / "check_forbidden_terms.py"
_spec = importlib.util.spec_from_file_location("check_forbidden_terms", _SCRIPT)
assert _spec and _spec.loader
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)

find_forbidden = _mod.find_forbidden
_prompt_constant_nodes = _mod._prompt_constant_nodes
_docstring_nodes = _mod._docstring_nodes


# ── 基本检测 ────────────────────────────────────────────────────

def test_detects_chinese_forbidden_terms():
    assert find_forbidden("建议录用该候选人")
    assert find_forbidden("这个岗位不适合他")
    assert find_forbidden("结论：不合格")
    assert find_forbidden("已淘汰")


def test_detects_english_terms_with_word_boundary():
    assert find_forbidden("result: pass")
    assert find_forbidden("verdict fail")


def test_english_terms_use_word_boundary():
    """failed / password 不该命中 fail —— 否则全是误报。"""
    assert find_forbidden("the request failed") == []
    assert find_forbidden("password reset") == []
    assert find_forbidden("bypass") == []


def test_clean_text_passes():
    text = "该维度有相邻经验，但本次未获取到证据。"
    assert find_forbidden(text) == []


def test_empty_text_passes():
    assert find_forbidden("") == []


# ── 排除规则 ────────────────────────────────────────────────────

def test_docstrings_are_excluded():
    """docstring 里出现禁用词是正常的（代码说明），不该报。"""
    tree = ast.parse('''
def f():
    """这里提到录用一类的说法是不允许的。"""
    return "建议录用"
''')
    docstrings = _docstring_nodes(tree)
    prompts = _prompt_constant_nodes(tree)

    flagged = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Constant) or not isinstance(node.value, str):
            continue
        if id(node) in docstrings or id(node) in prompts:
            continue
        if find_forbidden(node.value):
            flagged.append(node.value)

    assert flagged == ["建议录用"], "只有真正的输出文案该被拦下"


def test_prompt_constants_are_excluded():
    """prompt 里必须出现禁用词（用来禁止模型使用），不该被拦。"""
    tree = ast.parse('''
SYSTEM_PROMPT = "禁止使用录用、淘汰一类的结论。"
REPORT_TEXT = "建议录用该候选人"
''')
    prompts = _prompt_constant_nodes(tree)
    docstrings = _docstring_nodes(tree)

    flagged = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Constant) or not isinstance(node.value, str):
            continue
        if id(node) in docstrings or id(node) in prompts:
            continue
        if find_forbidden(node.value):
            flagged.append(node.value)

    assert flagged == ["建议录用该候选人"], "prompt 里的禁用词是故意写的，不该被拦"


def test_prompt_detection_requires_prompt_in_name():
    tree = ast.parse('''
MESSAGE = "建议录用"
''')
    assert _prompt_constant_nodes(tree) == set()
