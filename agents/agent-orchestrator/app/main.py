"""
Agent Orchestrator - FastAPI 入口

把 AI 任务路由到各专职 Agent（LangGraph 状态机）。

契约要点（§6.1）：/api/orchestrate 返回的 result 就是下游 Agent 的原始输出，
不额外包一层，回调方可以直接落库。
"""
import json
import logging
import uuid
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from sse_starlette.sse import EventSourceResponse

from .config import settings
from .graph import orchestrator_graph
from .http_client import close_client, init_client
from .state import AgentState, new_state

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(f"{settings.APP_NAME} starting on port {settings.APP_PORT}")
    logger.info(f"supported task types: {', '.join(settings.SUPPORTED_TASK_TYPES)}")
    init_client()          # 全进程共用一个客户端
    yield
    await close_client()
    logger.info("Shutting down")


app = FastAPI(
    title="CareerPilot Agent Orchestrator",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ─── 请求 / 响应模型 ───────────────────────────────────────────

class TaskRequest(BaseModel):
    task_type: str
    input_data: dict = Field(default_factory=dict)
    user_id: str = ""


class TaskResponse(BaseModel):
    task_id: str
    status: str
    progress: int
    result: dict | None = None
    error: str | None = None


# ─── 端点 ─────────────────────────────────────────────────────

@app.get("/health")
async def health():
    return {
        "status": "ok",
        "service": settings.APP_NAME,
        "supported_task_types": list(settings.SUPPORTED_TASK_TYPES),
    }


def _validate_task_type(task_type: str) -> None:
    """在入口就拦掉不支持的任务类型，返回明确的 400"""
    if task_type not in settings.SUPPORTED_TASK_TYPES:
        supported = ", ".join(settings.SUPPORTED_TASK_TYPES)
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported task type: {task_type}（支持：{supported}）",
        )


def _build_state(task_type: str, input_data: dict, user_id: str = "") -> AgentState:
    task_id = f"task_{uuid.uuid4().hex[:16]}"
    return new_state(task_id=task_id, task_type=task_type, user_id=user_id, input_data=input_data)


async def orchestrate_task(req: TaskRequest) -> TaskResponse:
    """
    同步编排入口：跑完整张图后一次性返回最终结果。
    """
    _validate_task_type(req.task_type)
    initial_state = _build_state(req.task_type, req.input_data, req.user_id)

    try:
        final_state = await orchestrator_graph.ainvoke(initial_state)
    except Exception as exc:  # 图本身的异常（理论上节点内已兜住）
        logger.error(f"Orchestration failed for {initial_state['task_id']}: {exc}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    return TaskResponse(
        task_id=initial_state["task_id"],
        status="failed" if final_state.get("error") else "completed",
        progress=final_state.get("progress", 100),
        result=final_state.get("final_output"),
        error=final_state.get("error"),
    )


@app.post("/api/orchestrate", response_model=TaskResponse)
async def orchestrate_endpoint(req: TaskRequest):
    return await orchestrate_task(req)


@app.post("/api/match", response_model=TaskResponse)
async def match_resume_jd(req: TaskRequest):
    """岗位匹配的便捷入口，等价于 task_type = match_score"""
    req.task_type = "match_score"
    return await orchestrate_task(req)


@app.get("/api/stream/{task_type}")
async def stream_task(task_type: str, input_data: str = "{}"):
    """
    SSE 进度推送。

    图只执行一次：astream 本身就是一次完整的执行，
    在流结束后用累加出的最终状态产出 done 事件，
    绝不能再调一次 ainvoke（那会把所有下游 Agent 重复调用一遍）。
    """
    _validate_task_type(task_type)

    try:
        parsed_input = json.loads(input_data) if input_data else {}
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=400, detail=f"input_data 不是合法 JSON：{exc}") from exc
    if not isinstance(parsed_input, dict):
        raise HTTPException(status_code=400, detail="input_data 必须是 JSON 对象")

    initial_state = _build_state(task_type, parsed_input)
    task_id = initial_state["task_id"]

    async def event_generator() -> AsyncIterator[dict[str, str]]:
        # 逐节点累加状态；跑完后它就是最终状态
        merged: dict = dict(initial_state)

        try:
            async for event in orchestrator_graph.astream(
                initial_state, stream_mode="updates"
            ):
                for node_name, node_output in event.items():
                    if not isinstance(node_output, dict):
                        continue
                    merged.update(node_output)
                    yield {
                        "event": "progress",
                        "data": json.dumps(
                            {
                                "task_id": task_id,
                                "node": node_name,
                                "progress": merged.get("progress", 0),
                                "current_agent": merged.get("current_agent", ""),
                            },
                            ensure_ascii=False,
                        ),
                    }

            error = merged.get("error")
            yield {
                "event": "done" if not error else "error",
                "data": json.dumps(
                    {
                        "task_id": task_id,
                        "status": "failed" if error else "completed",
                        "progress": merged.get("progress", 100),
                        "current_agent": merged.get("current_agent", ""),
                        "result": merged.get("final_output"),
                        "error": error,
                    },
                    ensure_ascii=False,
                ),
            }
        except Exception as exc:  # 兜底，保证 SSE 一定收到终止事件
            logger.error(f"Stream failed for {task_id}: {exc}", exc_info=True)
            yield {
                "event": "error",
                "data": json.dumps(
                    {"task_id": task_id, "status": "failed", "error": str(exc)},
                    ensure_ascii=False,
                ),
            }

    return EventSourceResponse(event_generator())
