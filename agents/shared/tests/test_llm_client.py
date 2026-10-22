"""
共享 LLM 客户端的单元测试。

覆盖计划书 §7 M1 的验收标准：
1. 故意不合法的 schema → 连续重试 N 次后返回 None，**不抛异常**
2. 指向不存在的端口 → 5s 内返回 None，不挂起
3. 成功调用后 token 计量被回填
4. 不配 API key 时不发起任何外部调用
"""
import asyncio
import sys
import time
from pathlib import Path

import httpx
import pytest

# agents/shared/ 在仓库的 agents/ 下，各 Agent 虚拟环境独立，需显式挂载
_AGENTS_ROOT = Path(__file__).resolve().parents[2]
if str(_AGENTS_ROOT) not in sys.path:
    sys.path.insert(0, str(_AGENTS_ROOT))

from shared.llm_client import (  # noqa: E402
    LlmSettings,
    complete_json,
    extract_json_object,
    settings_from_agent,
    validate_against_schema,
)


def _settings(**overrides) -> LlmSettings:
    base = {
        "api_key": "test-key",
        "api_base": "http://127.0.0.1:1/v1",
        "model": "gpt-4o",
        "temperature": 0.2,
        "timeout_seconds": 5.0,
        "prompt_version": "v1",
    }
    base.update(overrides)
    return LlmSettings(**base)


# ── Schema 校验 ──────────────────────────────────────────────────

def test_validate_accepts_valid_object():
    schema = {
        "type": "object",
        "properties": {"gap_ref": {"type": "string"}, "level": {"type": "integer"}},
        "required": ["gap_ref", "level"],
        "additionalProperties": False,
    }
    errors = validate_against_schema({"gap_ref": "gap#3", "level": 3}, schema)
    assert errors == []


def test_validate_rejects_missing_required_field():
    schema = {
        "type": "object",
        "properties": {"gap_ref": {"type": "string"}, "level": {"type": "integer"}},
        "required": ["gap_ref", "level"],
    }
    errors = validate_against_schema({"gap_ref": "gap#3"}, schema)
    assert any("level" in e for e in errors)


def test_validate_rejects_wrong_type():
    schema = {
        "type": "object",
        "properties": {"level": {"type": "integer"}},
        "required": ["level"],
    }
    errors = validate_against_schema({"level": "三"}, schema)
    assert errors and "期望类型 integer" in errors[0]


def test_validate_rejects_extra_property():
    schema = {
        "type": "object",
        "properties": {"gap_ref": {"type": "string"}},
        "additionalProperties": False,
    }
    errors = validate_against_schema({"gap_ref": "g", "oops": 1}, schema)
    assert any("oops" in e for e in errors)


def test_validate_checks_enum_and_ranges():
    schema = {
        "type": "object",
        "properties": {
            "coverage": {"type": "string", "enum": ["demonstrated", "adjacent", "not_evidenced"]},
            "confidence": {"type": "number", "minimum": 0, "maximum": 1},
        },
    }
    assert validate_against_schema(
        {"coverage": "demonstrated", "confidence": 0.8}, schema) == []
    assert validate_against_schema({"coverage": "pass", "confidence": 0.8}, schema)
    assert validate_against_schema({"coverage": "adjacent", "confidence": 1.4}, schema)


def test_validate_checks_array_items():
    schema = {
        "type": "object",
        "properties": {
            "probes": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {"question": {"type": "string"}},
                    "required": ["question"],
                },
            }
        },
    }
    assert validate_against_schema({"probes": [{"question": "q"}]}, schema) == []
    assert validate_against_schema({"probes": [{"trigger": "t"}]}, schema)


# ── JSON 提取 ────────────────────────────────────────────────────

def test_extract_plain_json():
    assert extract_json_object('{"a": 1}') == {"a": 1}


def test_extract_from_code_fence():
    text = '这是结果：\n```json\n{"a": 1}\n```\n希望有帮助'
    assert extract_json_object(text) == {"a": 1}


def test_extract_from_surrounding_prose():
    text = '好的，我的回答是 {"dimension": "delivery"} ，请查收。'
    assert extract_json_object(text) == {"dimension": "delivery"}


def test_extract_returns_none_for_garbage():
    assert extract_json_object("完全不是 JSON") is None
    assert extract_json_object("") is None


# ── complete_json 行为 ───────────────────────────────────────────

def test_disabled_llm_returns_none_without_network(monkeypatch):
    """不配 API key 时不发起任何外部调用（验收 4）。"""
    def _boom(*args, **kwargs):
        raise AssertionError("未配置 key 时不应发起 HTTP 请求")

    monkeypatch.setattr(httpx.AsyncClient, "post", _boom)
    result = asyncio.run(complete_json(_settings(api_key=""), "sys", "user", {"type": "object"}))
    assert result is None


def test_invalid_schema_returns_none_after_retries(monkeypatch):
    """故意不合法的 schema → 重试 N 次后返回 None，不抛异常（验收 1）。"""
    calls = {"n": 0}

    async def _fake_post(self, url, **kwargs):
        calls["n"] += 1
        # 每次都返回缺少必填字段的 JSON → 校验必然失败
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": '{"dimension": "delivery"}'}}],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 5}},
            request=httpx.Request("POST", url),
        )

    monkeypatch.setattr(httpx.AsyncClient, "post", _fake_post)
    # 把退避时间压到 0，避免测试等待 1s+2s
    monkeypatch.setattr("shared.llm_client._backoff_delay", lambda attempt: 0.0)

    schema = {
        "type": "object",
        "properties": {"gap_ref": {"type": "string"}, "dimension": {"type": "string"}},
        "required": ["gap_ref", "dimension"],
    }
    result = asyncio.run(complete_json(_settings(), "sys", "user", schema, max_retries=2))

    assert result is None          # 降级返回 None
    assert calls["n"] == 3         # 首次 + 2 次重试


def test_unreachable_port_returns_none_quickly():
    """指向不存在的端口 → 5s 内返回 None，不挂起（验收 2）。"""
    started = time.monotonic()
    result = asyncio.run(complete_json(_settings(api_base="http://127.0.0.1:9/v1"), "sys", "user"))
    elapsed = time.monotonic() - started
    assert result is None
    assert elapsed < 5.0


def test_successful_call_returns_json_and_meters_tokens(monkeypatch):
    """成功调用后 token 计量被回填（验收 3）。"""
    async def _fake_post(self, url, **kwargs):
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": '{"dimension": "delivery"}'}}],
                  "usage": {"prompt_tokens": 123, "completion_tokens": 45}},
            request=httpx.Request("POST", url),
        )

    monkeypatch.setattr(httpx.AsyncClient, "post", _fake_post)

    usage: dict = {}
    schema = {
        "type": "object",
        "properties": {"dimension": {"type": "string"}},
        "required": ["dimension"],
    }
    result = asyncio.run(
        complete_json(_settings(model="gpt-4o-mini"), "sys", "user", schema, usage_out=usage)
    )

    assert result == {"dimension": "delivery"}
    assert usage["input_tokens"] == 123
    assert usage["output_tokens"] == 45
    assert usage["model"] == "gpt-4o-mini"
    assert usage["prompt_version"] == "v1"


def test_structured_output_degrades_when_endpoint_rejects(monkeypatch):
    """端点不支持 response_format → 自动退化为提示式 JSON 仍能拿到结果。"""
    seen: list = []

    async def _fake_post(self, url, **kwargs):
        payload = kwargs.get("json") or {}
        seen.append(payload)
        if "response_format" in payload:
            return httpx.Response(
                400,
                text='{"error": {"message": "response_format is not supported"}}',
                request=httpx.Request("POST", url),
            )
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": '{"dimension": "scale"}'}}]},
            request=httpx.Request("POST", url),
        )

    monkeypatch.setattr(httpx.AsyncClient, "post", _fake_post)
    monkeypatch.setattr("shared.llm_client._backoff_delay", lambda attempt: 0.0)

    schema = {
        "type": "object",
        "properties": {"dimension": {"type": "string"}},
        "required": ["dimension"],
    }
    result = asyncio.run(complete_json(_settings(), "sys", "user", schema, max_retries=2))

    assert result == {"dimension": "scale"}
    # 第一次带 response_format 被拒，之后退化为不带
    assert "response_format" in seen[0]
    assert "response_format" not in seen[-1]


def test_server_error_returns_none_not_exception(monkeypatch):
    """服务端 500 → 返回 None，绝不抛异常。"""
    async def _fake_post(self, url, **kwargs):
        return httpx.Response(500, text="boom", request=httpx.Request("POST", url))

    monkeypatch.setattr(httpx.AsyncClient, "post", _fake_post)
    monkeypatch.setattr("shared.llm_client._backoff_delay", lambda attempt: 0.0)

    result = asyncio.run(complete_json(_settings(), "sys", "user", None, max_retries=1))
    assert result is None


# ── Agent 配置适配 ───────────────────────────────────────────────

def test_settings_adapter_reads_agent_settings():
    class FakeSettings:
        OPENAI_API_KEY = "k"
        OPENAI_API_BASE = "http://base"
        OPENAI_MODEL = "m"
        OPENAI_TEMPERATURE = 0.4
        LLM_TIMEOUT_SECONDS = 12.0
        LLM_PROMPT_VERSION = "v7"

    adapted = settings_from_agent(FakeSettings())
    assert adapted.api_key == "k"
    assert adapted.api_base == "http://base"
    assert adapted.model == "m"
    assert adapted.temperature == 0.4
    assert adapted.timeout_seconds == 12.0
    assert adapted.prompt_version == "v7"


def test_settings_adapter_tolerates_missing_optional_fields():
    class MinimalSettings:
        OPENAI_API_KEY = ""
        OPENAI_API_BASE = "http://base"
        OPENAI_MODEL = "m"
        OPENAI_TEMPERATURE = 0.3

    adapted = settings_from_agent(MinimalSettings())
    assert adapted.timeout_seconds == 30.0
    assert adapted.prompt_version == "v1"
