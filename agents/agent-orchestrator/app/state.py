"""
编排层共享状态。

采用 total=False：LangGraph 的节点只需返回自己改动的字段，
其余字段在合并时保持不变，避免因为"某个节点没写某个 key"而 KeyError。
"""
from typing import Any, Optional, TypedDict


class AgentState(TypedDict, total=False):
    """编排图各节点共享的状态"""
    # ── 任务标识 ──
    task_id: str
    task_type: str
    user_id: str
    input_data: dict

    # ── 执行状态 ──
    current_agent: str
    progress: int              # 0-100，只增不减
    error: Optional[str]

    # ── 中间结果（最终会被收敛成 raw_output）──
    jd_parsed: Optional[dict]
    resume_analyzed: Optional[dict]
    match_result: Optional[dict]
    optimize_result: Optional[dict]
    interview_result: Optional[dict]
    cover_letter_result: Optional[dict]
    career_plan_result: Optional[dict]

    # ── HR 侧（recruiter 模式）──
    hr_insight_result: Optional[dict]
    jd_calibrate_result: Optional[dict]
    hr_verify_result: Optional[dict]

    # ── 输出 ──
    final_output: Optional[dict]
    phase: str                 # interview 任务的两阶段标记


def new_state(
    task_id: str,
    task_type: str,
    user_id: str = "",
    input_data: Optional[dict] = None,
) -> AgentState:
    """构造一份初始状态，保证所有 key 都存在，避免节点里直接取下标报 KeyError"""
    return AgentState(
        task_id=task_id,
        task_type=task_type,
        user_id=user_id,
        input_data=input_data or {},
        current_agent="",
        progress=0,
        error=None,
        jd_parsed=None,
        resume_analyzed=None,
        match_result=None,
        optimize_result=None,
        interview_result=None,
        cover_letter_result=None,
        career_plan_result=None,
        hr_insight_result=None,
        jd_calibrate_result=None,
        hr_verify_result=None,
        final_output=None,
        phase=(input_data or {}).get("phase", ""),
    )


# 任务类型 -> 承载结果的 key，供 finalize 收敛输出时使用
RESULT_KEYS: dict[str, str] = {
    "jd_parse": "jd_parsed",
    "resume_analyze": "resume_analyzed",
    "match_score": "match_result",
    "resume_optimize": "optimize_result",
    "interview": "interview_result",
    "cover_letter": "cover_letter_result",
    "career_plan": "career_plan_result",
    "hr_insight": "hr_insight_result",
    "jd_calibrate": "jd_calibrate_result",
    "hr_verify": "hr_verify_result",
}

# 任务类型 -> 当前执行的 agent 名称（写入 current_agent 供前端展示进度）
AGENT_NAMES: dict[str, str] = {
    "jd_parse": "jd-parser-agent",
    "resume_analyze": "resume-analyzer-agent",
    "match_score": "match-agent",
    "resume_optimize": "resume-optimizer-agent",
    "interview": "interview-agent",
    "cover_letter": "cover-letter-agent",
    "career_plan": "career-advisor-agent",
    # HR 侧复用 interview-agent 的 recruiter 模式 —— 计划书硬约束 C1：
    # 本期不新增 Python Agent 进程
    "hr_insight": "interview-agent",
    "jd_calibrate": "interview-agent",
    "hr_verify": "interview-agent",
}


def result_for(state: AgentState) -> Any:
    """按任务类型取出对应的中间结果"""
    key = RESULT_KEYS.get(state.get("task_type", ""))
    if key is None:
        return None
    return state.get(key)
