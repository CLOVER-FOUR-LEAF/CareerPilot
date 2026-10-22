"""
CareerPilot 共享 LLM 客户端。

为什么要有这一层
----------------
此前 8 个 Agent 各自持有一份 llm.py，同样的逻辑抄了 5 遍：

- 只返回裸字符串，没有结构化输出
- 解析失败没有重试
- 没有超时分级（连接超时和读超时混在一起）
- 没有 token 计量，LLM 花多少钱、烧多少 token 完全不可见
- 配置项各写各的，模型名/温度散落各处

HR 侧能力（归因 / 验证计划 / 判定）重度依赖 LLM，所以先把这层工程化掉，
后面所有功能都站在同一块地基上。

六条硬性要求（对应计划书 §5.3）
------------------------------
1. 结构化输出：请求体带 ``response_format: json_schema``；
   端点不支持时自动退化为「提示 JSON + 解析 + 校验」。
2. 校验 + 重试：解析失败或字段缺失 → 指数退避（1s/2s/4s + 抖动）重试，最多 N 次。
3. 超时：分阶段超时，httpx ``Timeout(connect=5, read=timeout_s)``。
4. 失败降级：任何失败返回 None，由调用方降级到规则实现。**绝不抛异常。**
5. token 计量：解析 ``usage.prompt_tokens`` / ``usage.completion_tokens``，
   通过 ``usage_out`` 回填给调用方，落库到 ``t_agent_invocation``。
6. 模型与参数可配：沿用 ``OPENAI_API_BASE`` / ``OPENAI_MODEL`` /
   ``OPENAI_TEMPERATURE``，新增 ``LLM_PROMPT_VERSION`` 便于 prompt 版本管理。

Prompt 设计原则（写进代码注释，见 ADR-001）
--------------------------------------------
1. 不喂原始简历全文给归因/验证，只喂 gap 涉及的**相关片段**，降低幻觉与成本。
2. 强制引用：``evidence_ref`` 必须是输入中**原样出现的连续片段**（由调用方校验）。
3. 禁止输出禁用词：system prompt 里列出禁用词清单（§8.3）。
4. 温度：归因/判定用 0.1-0.3，生成问题用 0.5-0.7。
"""
from __future__ import annotations

import asyncio
import json
import logging
import random
import re
from dataclasses import dataclass, field
from typing import Any, Optional

import httpx

logger = logging.getLogger(__name__)

# ── 默认参数 ──────────────────────────────────────────────────────

DEFAULT_MAX_RETRIES = 2
CONNECT_TIMEOUT_SECONDS = 5.0
DEFAULT_TIMEOUT_SECONDS = 30.0

# 退避基数（秒）：1s / 2s / 4s，再叠加抖动
_BACKOFF_BASE_SECONDS = 1.0
_BACKOFF_JITTER_SECONDS = 0.3


@dataclass(frozen=True)
class LlmSettings:
    """
    LLM 调用参数。

    由各 Agent 的 ``app/config.py`` 组装传入 —— 共享模块本身不读任何
    Agent 的 settings，这样它对所有 Agent 都是无差别的。
    """

    api_key: str
    api_base: str = "https://api.openai.com/v1"
    model: str = "gpt-4o"
    temperature: float = 0.3
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS
    prompt_version: str = "v1"

    @property
    def enabled(self) -> bool:
        return bool(self.api_key.strip())


def settings_from_agent(settings) -> LlmSettings:
    """
    把某个 Agent 自己的 ``Settings`` 对象适配成共享参数。

    共享模块不认识任何具体 Agent 的配置类，所以适配放在这里做一次，
    各 Agent 的 ``app/llm.py`` 只需调用它，不必重复字段映射。
    """
    return LlmSettings(
        api_key=settings.OPENAI_API_KEY,
        api_base=settings.OPENAI_API_BASE,
        model=settings.OPENAI_MODEL,
        temperature=settings.OPENAI_TEMPERATURE,
        timeout_seconds=getattr(settings, "LLM_TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS),
        prompt_version=getattr(settings, "LLM_PROMPT_VERSION", "v1"),
    )


# ── JSON Schema 校验 ─────────────────────────────────────────────
#
# 不引入 jsonschema 依赖：8 个 Agent 各有独立虚拟环境，加包意味着
# 8 份 requirements + 8 次安装，收益不抵成本。这里实现一个覆盖
# 本项目实际用到的 JSON Schema 子集的校验器 —— 类型、必填、
# 枚举、数组元素、取值范围、字符串长度、anyOf。


def _type_matches(value: Any, expected: str) -> bool:
    if expected == "object":
        return isinstance(value, dict)
    if expected == "array":
        return isinstance(value, list)
    if expected == "string":
        return isinstance(value, str)
    if expected == "integer":
        # JSON 里 1.0 也会被很多模型写成整数，先按数值判断再取整
        return isinstance(value, int) and not isinstance(value, bool) or (
            isinstance(value, float) and value.is_integer()
        )
    if expected == "number":
        return isinstance(value, (int, float)) and not isinstance(value, bool)
    if expected == "boolean":
        return isinstance(value, bool)
    if expected == "null":
        return value is None
    return True


def validate_against_schema(value: Any, schema: dict, path: str = "$") -> list[str]:
    """
    按 JSON Schema 子集校验，返回错误列表（空列表 = 通过）。

    支持：type / properties / required / additionalProperties /
    items / enum / minimum / maximum / minLength / maxLength /
    minItems / maxItems / anyOf。
    """
    errors: list[str] = []

    expected_type = schema.get("type")
    if isinstance(expected_type, str):
        if not _type_matches(value, expected_type):
            errors.append(f"{path}: 期望类型 {expected_type}，实际 {type(value).__name__}")
            return errors
    elif isinstance(expected_type, list):
        if not any(_type_matches(value, t) for t in expected_type):
            errors.append(f"{path}: 期望类型 {expected_type} 之一，实际 {type(value).__name__}")
            return errors

    if "enum" in schema and value not in schema["enum"]:
        errors.append(f"{path}: 取值 {value!r} 不在允许范围 {schema['enum']}")

    if isinstance(value, dict):
        properties = schema.get("properties") or {}
        for key in schema.get("required") or []:
            if key not in value:
                errors.append(f"{path}.{key}: 缺少必填字段")

        for key, sub_schema in properties.items():
            if key in value:
                errors.extend(validate_against_schema(value[key], sub_schema, f"{path}.{key}"))

        if schema.get("additionalProperties") is False:
            for key in value:
                if key not in properties:
                    errors.append(f"{path}.{key}: 不允许的额外字段")

    if isinstance(value, list):
        item_schema = schema.get("items")
        if isinstance(item_schema, dict):
            for idx, item in enumerate(value):
                errors.extend(validate_against_schema(item, item_schema, f"{path}[{idx}]"))
        if "minItems" in schema and len(value) < schema["minItems"]:
            errors.append(f"{path}: 至少需要 {schema['minItems']} 个元素")
        if "maxItems" in schema and len(value) > schema["maxItems"]:
            errors.append(f"{path}: 最多 {schema['maxItems']} 个元素")

    if isinstance(value, str):
        if "minLength" in schema and len(value) < schema["minLength"]:
            errors.append(f"{path}: 字符串长度不足 {schema['minLength']}")
        if "maxLength" in schema and len(value) > schema["maxLength"]:
            errors.append(f"{path}: 字符串长度超过 {schema['maxLength']}")

    if isinstance(value, (int, float)) and not isinstance(value, bool):
        if "minimum" in schema and value < schema["minimum"]:
            errors.append(f"{path}: 小于最小值 {schema['minimum']}")
        if "maximum" in schema and value > schema["maximum"]:
            errors.append(f"{path}: 大于最大值 {schema['maximum']}")

    if "anyOf" in schema:
        branches = schema["anyOf"]
        if isinstance(branches, list) and branches:
            if not any(not validate_against_schema(value, b, path) for b in branches):
                errors.append(f"{path}: 不满足 anyOf 中的任何一个分支")

    return errors


# ── JSON 提取 ────────────────────────────────────────────────────

_FENCE_RE = re.compile(r"```(?:json)?\s*(.*?)```", re.DOTALL)


def extract_json_object(text: str) -> Optional[dict]:
    """
    从模型输出里提取 JSON 对象。

    模型经常会在 JSON 外面裹 ```json 代码块，或者前后带一句解释。
    这里做三级降级：直接解析 → 去代码块 → 截取第一个平衡的花括号。
    """
    if not text:
        return None

    candidates: list[str] = []
    stripped = text.strip()
    candidates.append(stripped)

    for match in _FENCE_RE.finditer(text):
        candidates.append(match.group(1).strip())

    start = stripped.find("{")
    end = stripped.rfind("}")
    if start != -1 and end > start:
        candidates.append(stripped[start:end + 1])

    for candidate in candidates:
        if not candidate:
            continue
        try:
            parsed = json.loads(candidate)
        except (json.JSONDecodeError, ValueError):
            continue
        if isinstance(parsed, dict):
            return parsed
    return None


# ── 退避 ──────────────────────────────────────────────────────────

def _backoff_delay(attempt: int) -> float:
    """第 attempt 次失败后应等待的秒数：1s / 2s / 4s + 抖动。"""
    base = _BACKOFF_BASE_SECONDS * (2 ** attempt)
    return base + random.uniform(0, _BACKOFF_JITTER_SECONDS)


# ── 调用主体 ──────────────────────────────────────────────────────

@dataclass
class _Attempt:
    """一次尝试的结果，用于决定是否重试。"""

    ok: bool
    payload: Optional[dict] = None
    error: str = ""
    usage: dict = field(default_factory=dict)
    used_structured_output: bool = True
    # 是否值得重试。连接被拒、鉴权失败这类**确定性**失败重试没有意义，
    # 重试只会把 5s 的响应时间拖成 15s；只有解析/校验/5xx 这类偶发问题才重试。
    retryable: bool = True


def _build_payload(
    settings: LlmSettings,
    system_prompt: str,
    user_prompt: str,
    schema: Optional[dict],
    use_structured_output: bool,
) -> dict:
    payload: dict = {
        "model": settings.model,
        "temperature": settings.temperature,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }

    if schema and use_structured_output:
        payload["response_format"] = {
            "type": "json_schema",
            "json_schema": {
                "name": "structured_output",
                "schema": schema,
                "strict": True,
            },
        }
    return payload


def _extract_usage(data: dict) -> dict:
    """从响应里抽 token 用量。不同厂商字段名不完全一致，都兜住。"""
    usage = data.get("usage") or {}
    if not isinstance(usage, dict):
        return {}
    prompt_tokens = usage.get("prompt_tokens", usage.get("input_tokens"))
    completion_tokens = usage.get("completion_tokens", usage.get("output_tokens"))
    result: dict = {}
    if isinstance(prompt_tokens, int):
        result["input_tokens"] = prompt_tokens
    if isinstance(completion_tokens, int):
        result["output_tokens"] = completion_tokens
    return result


async def _post_once(
    client: httpx.AsyncClient,
    settings: LlmSettings,
    system_prompt: str,
    user_prompt: str,
    schema: Optional[dict],
    use_structured_output: bool,
) -> _Attempt:
    """单次请求。不重试、不吞异常细节 —— 异常在这里收敛成 _Attempt。"""
    url = f"{settings.api_base.rstrip('/')}/chat/completions"
    headers = {
        "Authorization": f"Bearer {settings.api_key}",
        "Content-Type": "application/json",
    }

    try:
        resp = await client.post(
            url,
            json=_build_payload(settings, system_prompt, user_prompt, schema, use_structured_output),
            headers=headers,
        )
    except httpx.ConnectError as exc:
        # 连不上：端点没起 / 端口不通 / DNS 解析失败。重试没有意义，
        # 立刻失败让调用方走规则降级，而不是白等 3 轮退避。
        return _Attempt(
            ok=False,
            error=f"无法连接 LLM 端点: {exc}",
            used_structured_output=use_structured_output,
            retryable=False,
        )
    except httpx.HTTPError as exc:
        # 超时 / 读错误：偶发，值得重试
        return _Attempt(ok=False, error=f"请求失败: {exc}", used_structured_output=use_structured_output)

    if resp.status_code >= 400:
        detail = resp.text[:300]
        # 端点不支持 json_schema 时，退化为「提示 JSON + 解析 + 校验」
        unsupported = resp.status_code == 400 and (
            "response_format" in detail or "json_schema" in detail
        )
        if unsupported and use_structured_output:
            return _Attempt(
                ok=False,
                error=f"端点不支持结构化输出，降级为提示式 JSON: {detail}",
                used_structured_output=False,
            )
        # 401/403/404 是配置或端点问题，重试同样没有意义
        deterministic = resp.status_code in (401, 403, 404)
        return _Attempt(
            ok=False,
            error=f"HTTP {resp.status_code}: {detail}",
            used_structured_output=use_structured_output,
            retryable=not deterministic,
        )

    try:
        data = resp.json()
        content = data["choices"][0]["message"]["content"]
    except (ValueError, KeyError, IndexError, TypeError) as exc:
        return _Attempt(ok=False, error=f"响应结构异常: {exc}", used_structured_output=use_structured_output)

    usage = _extract_usage(data)
    parsed = extract_json_object(content or "")
    if parsed is None:
        return _Attempt(
            ok=False,
            error="模型输出不是合法 JSON",
            usage=usage,
            used_structured_output=use_structured_output,
        )

    if schema:
        errors = validate_against_schema(parsed, schema)
        if errors:
            return _Attempt(
                ok=False,
                error="字段校验失败: " + "; ".join(errors[:5]),
                usage=usage,
                used_structured_output=use_structured_output,
            )

    return _Attempt(ok=True, payload=parsed, usage=usage, used_structured_output=use_structured_output)


async def complete_json(
    settings: LlmSettings,
    system_prompt: str,
    user_prompt: str,
    schema: Optional[dict] = None,
    *,
    max_retries: int = DEFAULT_MAX_RETRIES,
    usage_out: Optional[dict] = None,
) -> Optional[dict]:
    """
    调用 OpenAI 兼容端点并拿回结构化 JSON。

    失败返回 ``None``，由调用方降级到规则实现。**绝不抛异常。**

    :param usage_out: 传入一个 dict，成功时会被填入 token 计量
        （``input_tokens`` / ``output_tokens`` / ``model`` / ``prompt_version``）。
    """
    if not settings.enabled:
        logger.info("LLM 未配置 API key，跳过调用（规则路径）")
        return None

    timeout = httpx.Timeout(
        settings.timeout_seconds,
        connect=CONNECT_TIMEOUT_SECONDS,
    )
    use_structured_output = bool(schema)

    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            for attempt in range(max_retries + 1):
                result = await _post_once(
                    client, settings, system_prompt, user_prompt, schema, use_structured_output
                )

                # 端点不支持结构化输出 → 永久降级为提示式 JSON 后重试
                if not result.ok and not result.used_structured_output and use_structured_output:
                    logger.warning("%s", result.error)
                    use_structured_output = False
                    if attempt < max_retries:
                        await asyncio.sleep(_backoff_delay(attempt))
                        continue

                if result.ok:
                    if usage_out is not None:
                        usage_out.update(result.usage)
                        usage_out["model"] = settings.model
                        usage_out["prompt_version"] = settings.prompt_version
                    return result.payload

                logger.warning("LLM 调用失败（第 %d 次）：%s", attempt + 1, result.error)

                # 确定性失败（连不上 / 鉴权失败）不重试：重试只会拖长响应时间
                if not result.retryable:
                    break
                if attempt >= max_retries:
                    break
                await asyncio.sleep(_backoff_delay(attempt))
    except Exception as exc:  # 兜底：共享层绝不让异常冒到调用方
        logger.warning("LLM 调用异常，调用方将降级到规则实现: %s", exc)
        return None

    return None


async def complete(
    settings: LlmSettings,
    system_prompt: str,
    user_prompt: str,
    *,
    usage_out: Optional[dict] = None,
) -> Optional[str]:
    """
    调用 OpenAI 兼容端点，拿回裸字符串。

    失败返回 ``None``。**绝不抛异常。**
    """
    if not settings.enabled:
        logger.info("LLM 未配置 API key，跳过调用（规则路径）")
        return None

    url = f"{settings.api_base.rstrip('/')}/chat/completions"
    headers = {
        "Authorization": f"Bearer {settings.api_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": settings.model,
        "temperature": settings.temperature,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }

    timeout = httpx.Timeout(settings.timeout_seconds, connect=CONNECT_TIMEOUT_SECONDS)

    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.post(url, json=payload, headers=headers)
            resp.raise_for_status()
            data = resp.json()
            content = data["choices"][0]["message"]["content"]
    except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError) as exc:
        logger.warning("LLM 调用失败，回落到规则: %s", exc)
        return None

    if usage_out is not None:
        usage_out.update(_extract_usage(data))
        usage_out["model"] = settings.model
        usage_out["prompt_version"] = settings.prompt_version

    return content
