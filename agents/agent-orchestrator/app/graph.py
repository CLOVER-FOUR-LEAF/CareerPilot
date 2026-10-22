"""
基于 LangGraph 的任务编排图。

保留了 LangGraph StateGraph 作为架构展示（契约与项目要求），修正了以下问题：

1. 支持全部 7 种 task_type，而非原先的 3 种。
2. match_score 委派给 match-agent（8004）真实打分，不再做朴素关键词重叠。
3. interview 支持 question / evaluate 两个阶段（由 input_data.phase 决定）。
4. 下游地址全部走环境变量，HTTP 客户端全进程共享。
5. 每个节点都写 progress（只增不减）与 current_agent；下游失败时写 error
   并统一收敛到 finalize，而不是把异常抛到最外层。
6. /api/orchestrate 的 result 是下游 Agent 的**原始输出**，不再多包一层。
"""
import logging
from typing import Any

from langgraph.graph import END, StateGraph

from .config import settings
from .http_client import AgentError, post_json
from .state import AGENT_NAMES, RESULT_KEYS, AgentState, result_for

logger = logging.getLogger(__name__)


# ─── 进度工具 ──────────────────────────────────────────────────

def _advance(state: AgentState, progress: int, agent: str) -> AgentState:
    """推进进度并记录当前 Agent；进度只增不减，避免 SSE 出现回退"""
    state["progress"] = max(state.get("progress", 0), min(progress, 100))
    state["current_agent"] = agent
    return state


def _fail(state: AgentState, agent: str, exc: Exception) -> AgentState:
    """统一记录下游失败，写入 error 以便流转到 finalize"""
    state["error"] = str(exc)
    state["current_agent"] = agent
    logger.error("[%s] task=%s 调用下游失败：%s", agent, state.get("task_id"), exc)
    return state


# ─── 路由节点 ──────────────────────────────────────────────────

async def route_task(state: AgentState) -> AgentState:
    """按 task_type 决定调用哪个 Agent，并校验是否为受支持的类型"""
    task_type = state.get("task_type", "")
    agent = AGENT_NAMES.get(task_type)

    if agent is None:
        supported = ", ".join(settings.SUPPORTED_TASK_TYPES)
        state["error"] = f"Unsupported task type: {task_type}（支持：{supported}）"
        state["current_agent"] = "unknown"
        logger.warning("Unsupported task type: %s", task_type)
        return state

    _advance(state, 10, agent)
    return state


# ─── 各 Agent 调用节点 ────────────────────────────────────────

async def call_jd_parser(state: AgentState) -> AgentState:
    """调用 jd-parser-agent 解析 JD"""
    agent = "jd-parser-agent"
    _advance(state, 30, agent)
    try:
        payload = dict(state.get("input_data") or {})
        # 编排层只需要解析结果本身，jd_id 回填给调用方即可
        payload.setdefault("jd_id", state.get("input_data", {}).get("jd_id", ""))
        raw = await post_json(f"{settings.JD_PARSER_URL}/api/parse", payload)
        # jd-parser 返回 {"jd_id","result","parse_score"}，按契约 §6.1 取出原结构 JdChunk
        state["jd_parsed"] = raw.get("result", raw)
        _advance(state, 90, agent)
    except AgentError as exc:
        _fail(state, agent, exc)
    return state


async def call_resume_analyzer(state: AgentState) -> AgentState:
    """调用 resume-analyzer-agent 做 ATS 分析"""
    agent = "resume-analyzer-agent"
    _advance(state, 30, agent)
    try:
        payload = dict(state.get("input_data") or {})
        payload.setdefault("resume_id", state.get("input_data", {}).get("resume_id", ""))
        raw = await post_json(f"{settings.RESUME_ANALYZER_URL}/api/analyze", payload)
        # resume-analyzer 返回 {"resume_id","result"}，取出原结构 AnalysisResult
        state["resume_analyzed"] = raw.get("result", raw)
        _advance(state, 90, agent)
    except AgentError as exc:
        _fail(state, agent, exc)
    return state


async def call_match_agent(state: AgentState) -> AgentState:
    """
    调用 match-agent 做三维打分（契约 §7.3）。

    jd_skills 若未显式给出，交给 match-agent 自行从 JD 全文抽取，
    编排层不再重复调用 jd-parser，避免下游被调用两次。
    """
    agent = "match-agent"
    _advance(state, 30, agent)
    try:
        payload = dict(state.get("input_data") or {})
        raw = await post_json(f"{settings.MATCH_AGENT_URL}/api/match", payload)
        # match-agent 直接返回 MatchResult，本身就是原结构
        state["match_result"] = raw
        _advance(state, 90, agent)
    except AgentError as exc:
        _fail(state, agent, exc)
    return state


async def call_resume_optimizer(state: AgentState) -> AgentState:
    """调用 resume-optimizer-agent 改写单条经历"""
    agent = "resume-optimizer-agent"
    _advance(state, 30, agent)
    try:
        payload = dict(state.get("input_data") or {})
        raw = await post_json(f"{settings.RESUME_OPTIMIZER_URL}/api/optimize", payload)
        state["optimize_result"] = raw
        _advance(state, 90, agent)
    except AgentError as exc:
        _fail(state, agent, exc)
    return state


async def call_interview_agent(state: AgentState) -> AgentState:
    """
    调用 interview-agent，支持两阶段。

    phase=question（默认）-> POST /api/question
    phase=evaluate        -> POST /api/evaluate
    """
    agent = "interview-agent"
    _advance(state, 30, agent)

    phase = (state.get("phase") or state.get("input_data", {}).get("phase") or "question")
    phase = str(phase).strip().lower()
    if phase not in ("question", "evaluate"):
        _fail(state, agent, ValueError(
            f"interview 的 phase 只支持 question / evaluate，收到：{phase}"
        ))
        return state

    state["phase"] = phase
    try:
        payload = dict(state.get("input_data") or {})
        # phase 是编排层的路由信息，不需要透传给下游
        payload.pop("phase", None)
        raw = await post_json(f"{settings.INTERVIEW_AGENT_URL}/api/{phase}", payload)
        state["interview_result"] = raw
        _advance(state, 90, agent)
    except AgentError as exc:
        _fail(state, agent, exc)
    return state


async def call_cover_letter_agent(state: AgentState) -> AgentState:
    """调用 cover-letter-agent 生成求职信"""
    agent = "cover-letter-agent"
    _advance(state, 30, agent)
    try:
        payload = dict(state.get("input_data") or {})
        raw = await post_json(f"{settings.COVER_LETTER_AGENT_URL}/api/generate", payload)
        state["cover_letter_result"] = raw
        _advance(state, 90, agent)
    except AgentError as exc:
        _fail(state, agent, exc)
    return state


async def call_career_advisor(state: AgentState) -> AgentState:
    """调用 career-advisor-agent 生成职业规划"""
    agent = "career-advisor-agent"
    _advance(state, 30, agent)
    try:
        payload = dict(state.get("input_data") or {})
        raw = await post_json(f"{settings.CAREER_ADVISOR_URL}/api/plan", payload)
        state["career_plan_result"] = raw
        _advance(state, 90, agent)
    except AgentError as exc:
        _fail(state, agent, exc)
    return state


# ─── HR 侧（recruiter 模式）──────────────────────────────────────

async def call_hr_insight(state: AgentState) -> AgentState:
    """
    生成完整 HR 洞察报告。

    两步：
    1. match-agent 算出差距清单（L1，复用同一套匹配引擎）
    2. interview-agent 的 recruiter 模式做归因与验证计划（L2 + L3）

    这里只做**编排**（决定谁先谁后、怎么串），业务逻辑全在 Agent 侧。
    """
    agent = "interview-agent"
    _advance(state, 20, "match-agent")
    try:
        payload = dict(state.get("input_data") or {})

        # ── 1. L1：匹配打分，拿到 gap_list ──
        match_payload = {
            "jd_text": payload.get("jd_text", ""),
            "resume_text": payload.get("resume_text", ""),
        }
        if payload.get("jd_skills"):
            match_payload["jd_skills"] = payload["jd_skills"]
        match_result = await post_json(f"{settings.MATCH_AGENT_URL}/api/match", match_payload)
        state["match_result"] = match_result

        # ── 2. L2 + L3：归因 + 验证计划 ──
        _advance(state, 50, agent)
        insight_payload = {
            "jd_text": payload.get("jd_text", ""),
            "resume_text": payload.get("resume_text", ""),
            "jd_skills": payload.get("jd_skills") or [],
            "gaps": match_result.get("gap_list") or [],
            "match_result": match_result,
            "max_questions": payload.get("max_questions", 12),
        }
        raw = await post_json(f"{settings.INTERVIEW_AGENT_URL}/api/hr/insight", insight_payload)
        state["hr_insight_result"] = raw
        _advance(state, 90, agent)
    except AgentError as exc:
        _fail(state, agent, exc)
    return state


async def call_jd_calibrate(state: AgentState) -> AgentState:
    """JD 反向校准"""
    agent = "interview-agent"
    _advance(state, 30, agent)
    try:
        payload = dict(state.get("input_data") or {})
        raw = await post_json(f"{settings.INTERVIEW_AGENT_URL}/api/hr/calibrate", payload)
        state["jd_calibrate_result"] = raw
        _advance(state, 90, agent)
    except AgentError as exc:
        _fail(state, agent, exc)
    return state


async def call_hr_verify(state: AgentState) -> AgentState:
    """对候选人回答做覆盖判定"""
    agent = "interview-agent"
    _advance(state, 30, agent)
    try:
        payload = dict(state.get("input_data") or {})
        raw = await post_json(f"{settings.INTERVIEW_AGENT_URL}/api/hr/verify", payload)
        state["hr_verify_result"] = raw
        _advance(state, 90, agent)
    except AgentError as exc:
        _fail(state, agent, exc)
    return state


# ─── 收口节点 ──────────────────────────────────────────────────

async def finalize(state: AgentState) -> AgentState:
    """
    收敛输出。

    成功时 final_output 就是下游 Agent 的原始结果（契约 §6.1：不额外包一层）；
    失败时 final_output 为 None，错误通过 state["error"] 交给 HTTP 层。
    """
    _advance(state, 100, state.get("current_agent", ""))

    if state.get("error"):
        state["final_output"] = None
        return state

    result: Any = result_for(state)
    state["final_output"] = result if result is not None else {}
    return state


# ─── 条件边 ────────────────────────────────────────────────────

def should_continue(state: AgentState) -> str:
    """把 task_type 映射到具体的节点名；出错则直接收口"""
    if state.get("error"):
        return "finalize"

    task_type = state.get("task_type", "")
    node = {
        "jd_parse": "jd_parser",
        "resume_analyze": "resume_analyzer",
        "match_score": "match_agent",
        "resume_optimize": "resume_optimizer",
        "interview": "interview_agent",
        "cover_letter": "cover_letter_agent",
        "career_plan": "career_advisor",
        "hr_insight": "hr_insight",
        "jd_calibrate": "jd_calibrate",
        "hr_verify": "hr_verify",
    }.get(task_type)
    return node or "finalize"


# ─── 构建图 ────────────────────────────────────────────────────

# task_type -> (节点名, 处理函数)
NODE_HANDLERS: tuple[tuple[str, str, Any], ...] = (
    ("jd_parse", "jd_parser", call_jd_parser),
    ("resume_analyze", "resume_analyzer", call_resume_analyzer),
    ("match_score", "match_agent", call_match_agent),
    ("resume_optimize", "resume_optimizer", call_resume_optimizer),
    ("interview", "interview_agent", call_interview_agent),
    ("cover_letter", "cover_letter_agent", call_cover_letter_agent),
    ("career_plan", "career_advisor", call_career_advisor),
    ("hr_insight", "hr_insight", call_hr_insight),
    ("jd_calibrate", "jd_calibrate", call_jd_calibrate),
    ("hr_verify", "hr_verify", call_hr_verify),
)


def build_graph() -> Any:
    """构建并编译编排图"""
    graph = StateGraph(AgentState)

    graph.add_node("route", route_task)
    for _task_type, node_name, handler in NODE_HANDLERS:
        graph.add_node(node_name, handler)
    graph.add_node("finalize", finalize)

    graph.set_entry_point("route")

    routing: dict[str, str] = {"finalize": "finalize"}
    for _task_type, node_name, _handler in NODE_HANDLERS:
        routing[node_name] = node_name

    graph.add_conditional_edges("route", should_continue, routing)

    # 所有业务节点处理完都进入 finalize
    for _task_type, node_name, _handler in NODE_HANDLERS:
        graph.add_edge(node_name, "finalize")
    graph.add_edge("finalize", END)

    return graph.compile()


orchestrator_graph = build_graph()

# 供外部确认映射关系（契约 §7 各 Agent 输出结构）
__all__ = [
    "orchestrator_graph",
    "build_graph",
    "route_task",
    "finalize",
    "should_continue",
    "RESULT_KEYS",
    "AGENT_NAMES",
]
