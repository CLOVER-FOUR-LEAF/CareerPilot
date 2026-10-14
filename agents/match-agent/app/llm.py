"""
可选的 LLM 增强层。

设计约束（本项目硬性要求）：
- OPENAI_API_KEY 为空（仓库默认值）时，``llm_enabled()`` 返回 False，整个进程不会发起任何外部调用。
- 规则/词表路径是主实现，必须独立可用且结果完整。
- LLM 调用失败只记录 warning 并返回 None，绝不让请求失败。
"""
import logging
from typing import Optional

import httpx

from .config import settings

logger = logging.getLogger(__name__)


def llm_enabled() -> bool:
    """是否配置了可用的 LLM"""
    return bool(settings.OPENAI_API_KEY.strip())


async def complete(system_prompt: str, user_prompt: str) -> Optional[str]:
    """
    调用 OpenAI 兼容的 chat/completions 接口。

    返回 None 表示"拿不到 LLM 结果"，调用方应回落到规则引擎的结果。
    任何异常都会被吞掉并记录日志，绝不向上抛出。
    """
    if not llm_enabled():
        return None

    url = f"{settings.OPENAI_API_BASE.rstrip('/')}/chat/completions"
    payload = {
        "model": settings.OPENAI_MODEL,
        "temperature": settings.OPENAI_TEMPERATURE,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }
    headers = {
        "Authorization": f"Bearer {settings.OPENAI_API_KEY}",
        "Content-Type": "application/json",
    }

    try:
        async with httpx.AsyncClient(timeout=settings.LLM_TIMEOUT_SECONDS) as client:
            resp = await client.post(url, json=payload, headers=headers)
            resp.raise_for_status()
            data = resp.json()
        return data["choices"][0]["message"]["content"]
    except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError) as exc:
        # 网络、协议、返回结构异常一律降级，不影响主流程
        logger.warning("LLM call failed, falling back to rules: %s", exc)
        return None
