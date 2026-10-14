"""
可选的 LLM 增强层。

OPENAI_API_KEY 为空（仓库默认值）时完全不发起外部调用；
检索 + 规则拼装是主实现，LLM 调用失败只记录 warning 并返回 None。
"""
import logging
from typing import Optional

import httpx

from .config import settings

logger = logging.getLogger(__name__)


def llm_enabled() -> bool:
    return bool(settings.OPENAI_API_KEY.strip())


async def complete(system_prompt: str, user_prompt: str) -> Optional[str]:
    """
    调用 OpenAI 兼容的 chat/completions 接口。
    拿不到结果时返回 None，调用方应回落规则引擎的结果。绝不抛异常。
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
        logger.warning("LLM call failed, falling back to rules: %s", exc)
        return None
