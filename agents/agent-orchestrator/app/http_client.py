"""
下游 Agent 的 HTTP 调用封装。

要点：
- 整个进程共用一个 httpx.AsyncClient（在 lifespan 里创建/关闭），不再每请求新建。
- 统一把下游异常包装成 AgentError，图节点只需捕获一种异常。
"""
import logging
from typing import Any, Optional

import httpx

from .config import settings

logger = logging.getLogger(__name__)

_client: Optional[httpx.AsyncClient] = None


class AgentError(Exception):
    """下游 Agent 调用失败"""


def init_client() -> httpx.AsyncClient:
    """创建（或复用）全局共享客户端。由 lifespan 调用一次。"""
    global _client
    if _client is None or _client.is_closed:
        timeout = httpx.Timeout(
            settings.AGENT_TIMEOUT_SECONDS,
            connect=settings.AGENT_CONNECT_TIMEOUT_SECONDS,
        )
        limits = httpx.Limits(
            max_connections=settings.AGENT_MAX_CONNECTIONS,
            max_keepalive_connections=max(1, settings.AGENT_MAX_CONNECTIONS // 2),
        )
        _client = httpx.AsyncClient(timeout=timeout, limits=limits)
        logger.info("Shared httpx.AsyncClient initialized")
    return _client


async def close_client() -> None:
    """关闭共享客户端。由 lifespan 调用一次。"""
    global _client
    if _client is not None and not _client.is_closed:
        await _client.aclose()
        logger.info("Shared httpx.AsyncClient closed")
    _client = None


def get_client() -> httpx.AsyncClient:
    """取共享客户端。未初始化时按需创建，保证单测或直接调用节点时也能工作。"""
    return init_client()


async def post_json(url: str, payload: dict[str, Any]) -> dict[str, Any]:
    """
    POST JSON 并返回解析后的响应体。

    任何失败（连接、超时、非 2xx、响应不是 JSON 对象）都包装成 AgentError，
    便于图节点统一处理成 state["error"] 而不是抛栈。
    """
    client = get_client()
    try:
        resp = await client.post(url, json=payload)
    except httpx.TimeoutException as exc:
        raise AgentError(f"调用 {url} 超时（{settings.AGENT_TIMEOUT_SECONDS}s）") from exc
    except httpx.HTTPError as exc:
        raise AgentError(f"调用 {url} 失败：{exc}") from exc

    if resp.status_code >= 400:
        detail = resp.text[:300]
        raise AgentError(f"{url} 返回 {resp.status_code}：{detail}")

    try:
        data = resp.json()
    except ValueError as exc:
        raise AgentError(f"{url} 返回的不是合法 JSON") from exc

    if not isinstance(data, dict):
        raise AgentError(f"{url} 返回的 JSON 顶层不是对象")
    return data
