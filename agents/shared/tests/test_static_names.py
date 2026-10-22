"""
静态检查：模块里引用了但从未定义的名字。

起因
----
HR 侧开发时踩过一次：常量定义写成 ``_MANAGMENT_TITLE_MARKERS``，
使用处写成 ``_MANAGEMENT_TITLE_MARKERS``，差一个字母。
Python 编译期不报错，运行到那一行才抛 NameError —— 而且是在
「另一个模块的函数里」，排查成本很高。

这类问题靠人眼 review 抓不住（两个名字长得几乎一样），
所以用 AST 做一次静态检查：名字在模块里找不到定义就报出来。

覆盖范围：``agents/`` 下的全部 Python 源码。
"""
import ast
import builtins
import sys
from pathlib import Path

import pytest

_ROOT = Path(__file__).resolve().parents[2]      # agents/
_SKIP_DIRS = {".venv", "venv", "__pycache__", "node_modules", "target", ".git", ".runtime"}

_BUILTINS = set(dir(builtins)) | {"__file__", "__name__", "__doc__", "__package__"}


def _iter_sources():
    for path in sorted(_ROOT.rglob("*.py")):
        if any(part in _SKIP_DIRS for part in path.relative_to(_ROOT).parts):
            continue
        yield path


def _module_bindings(tree: ast.Module) -> set[str]:
    """模块级绑定的名字：赋值、import、def、class。"""
    names: set[str] = set()

    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            names.add(node.name)
        elif isinstance(node, ast.Import):
            for alias in node.names:
                names.add(alias.asname or alias.name.split(".")[0])
        elif isinstance(node, ast.ImportFrom):
            for alias in node.names:
                names.add(alias.asname or alias.name)
        elif isinstance(node, ast.Name) and isinstance(node.ctx, (ast.Store, ast.Del)):
            names.add(node.id)
        elif isinstance(node, ast.arg):
            names.add(node.arg)
        elif isinstance(node, ast.ExceptHandler) and node.name:
            names.add(node.name)
        elif isinstance(node, ast.Global) or isinstance(node, ast.Nonlocal):
            names.update(node.names)

    return names


def _local_bindings(node: ast.AST) -> set[str]:
    """
    函数内部绑定的名字（对嵌套作用域做粗粒度并集，宁可多报也不要漏）。

    局部变量、参数、推导式变量、except 名字都算进来。
    """
    names: set[str] = set()
    for child in ast.walk(node):
        if isinstance(child, ast.Name) and isinstance(child.ctx, (ast.Store, ast.Del)):
            names.add(child.id)
        elif isinstance(child, ast.arg):
            names.add(child.arg)
        elif isinstance(child, ast.ExceptHandler) and child.name:
            names.add(child.name)
        elif isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            names.add(child.name)
        elif isinstance(child, (ast.Global, ast.Nonlocal)):
            names.update(child.names)
    return names


def _undefined_names(tree: ast.Module) -> list[tuple[int, str]]:
    """返回 [(行号, 名字)] —— 引用了但找不到定义的名字。"""
    module_names = _module_bindings(tree)
    problems: list[tuple[int, str]] = []

    for func in ast.walk(tree):
        if not isinstance(func, (ast.FunctionDef, ast.AsyncFunctionDef)):
            continue

        locals_in_func = _local_bindings(func)
        # 嵌套函数里的名字也算外层可见
        for nested in ast.walk(func):
            if isinstance(nested, (ast.FunctionDef, ast.AsyncFunctionDef)) and nested is not func:
                locals_in_func |= _local_bindings(nested)

        for node in ast.walk(func):
            if not isinstance(node, ast.Name) or not isinstance(node.ctx, ast.Load):
                continue
            name = node.id
            if name in locals_in_func or name in module_names or name in _BUILTINS:
                continue
            problems.append((node.lineno, name))

    return problems


def _check_file(path: Path) -> list[str]:
    try:
        source = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return [f"{path.relative_to(_ROOT)}: 不是合法 UTF-8"]

    try:
        tree = ast.parse(source)
    except SyntaxError as exc:
        return [f"{path.relative_to(_ROOT)}:{exc.lineno}: 语法错误 {exc.msg}"]

    return [
        f"{path.relative_to(_ROOT)}:{lineno}: 名字 `{name}` 在本模块中找不到定义"
        for lineno, name in _undefined_names(tree)
    ]


def test_no_undefined_names_in_agents():
    """全 agents 目录不允许出现「引用了但没定义」的名字。"""
    problems: list[str] = []
    for path in _iter_sources():
        problems.extend(_check_file(path))

    assert not problems, "发现未定义的名字引用：\n" + "\n".join(f"  - {p}" for p in problems)


def test_source_files_are_readable():
    """顺带确认扫描到了文件，避免检查因为路径问题空跑。"""
    files = list(_iter_sources())
    assert len(files) >= 20, f"只扫描到 {len(files)} 个文件，路径可能不对"
