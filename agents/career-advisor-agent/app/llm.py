"""
可选的 LLM 增强层。

OPENAI_API_KEY 为空（仓库默认值）时完全不发起外部调用；
检索 + 规则拼装是主实现，LLM 调用失败只记录 warning 并返回 None。

实现已收敛到共享层 ``agents/shared/llm_client.py``：
结构化输出、校验重试、分阶段超时、token 计量、失败降级都在那一处实现，
本文件只负责把本 Agent 的配置适配进去。
"""
import logging
import sys
from pathlib import Path
from typing import Optional

from .config import settings

# 共享层位于 agents/shared/，各 Agent 虚拟环境独立，需显式挂载 agents/ 才能 import
_AGENTS_ROOT = Path(__file__).resolve().parents[2]
if str(_AGENTS_ROOT) not in sys.path:
    sys.path.insert(0, str(_AGENTS_ROOT))

from shared.llm_client import complete as _complete  # noqa: E402
from shared.llm_client import complete_json as _complete_json  # noqa: E402
from shared.llm_client import settings_from_agent  # noqa: E402

logger = logging.getLogger(__name__)


def llm_enabled() -> bool:
    return bool(settings.OPENAI_API_KEY.strip())


async def complete(system_prompt: str, user_prompt: str,
                   usage_out: Optional[dict] = None) -> Optional[str]:
    """
    调用 OpenAI 兼容的 chat/completions 接口。
    拿不到结果时返回 None，调用方应回落规则引擎的结果。绝不抛异常。
    """
    if not llm_enabled():
        return None
    return await _complete(
        settings_from_agent(settings),
        system_prompt,
        user_prompt,
        usage_out=usage_out,
    )


async def complete_json(system_prompt: str, user_prompt: str,
                        schema: Optional[dict] = None,
                        *,
                        max_retries: int = 2,
                        usage_out: Optional[dict] = None) -> Optional[dict]:
    """
    调用 OpenAI 兼容端点并拿回结构化 JSON。

    - 结构化输出：端点支持时带 response_format，不支持时自动退化为提示式 JSON
    - 校验 + 重试：字段缺失按指数退避重试
    - 失败降级：任何失败返回 None，由调用方降级到规则实现
    """
    if not llm_enabled():
        return None
    return await _complete_json(
        settings_from_agent(settings),
        system_prompt,
        user_prompt,
        schema,
        max_retries=max_retries,
        usage_out=usage_out,
    )
