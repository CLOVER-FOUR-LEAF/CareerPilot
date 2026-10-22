"""
HR 报告组装（L1 + L2 + L3 汇总）。

把匹配层的结果、归因层的结果、验证计划汇总成一份给 HR 看的报告。

对应 HR 视图的六个分区（计划书 §4.6）：
① 匹配总览  ② 能力维度覆盖  ③ Gap 清单
④ 面试地图  ⑤ 追问记录      ⑥ 免责与审计

合规约束 R2：**免责声明由代码拼接**，不可由 LLM 生成。
这里把 DISCLAIMER_TEXT 挂在报告对象上，LLM 碰不到它。
"""
from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field

from .attribution import CapabilityCoverage, GapAttribution
from .compliance import AUDIT_TEXT, DISCLAIMER_TEXT
from .verification import VerificationQuestion


class MatchOverview(BaseModel):
    """① 匹配总览：复用 match-agent 的三段式分数。"""

    final_score: float = 0.0
    semantic_score: float = 0.0
    keyword_score: float = 0.0
    business_score: float = 0.0
    matched_skills: list[str] = Field(default_factory=list)


class HrInsightReport(BaseModel):
    """
    完整的 HR 洞察报告。

    这是 ``POST /api/v1/hr/insight`` 的返回结构，
    也是 t_hr_insight.report_json 的内容。
    """

    # ① 匹配总览
    match_overview: MatchOverview = Field(default_factory=MatchOverview)

    # ② 能力维度覆盖
    capability_coverage: list[CapabilityCoverage] = Field(default_factory=list)

    # ③ Gap 清单（含归因）
    attributions: list[GapAttribution] = Field(default_factory=list)

    # ④ 面试地图（验证计划）
    verification_plan: list[VerificationQuestion] = Field(default_factory=list)

    # ⑥ 免责与审计 —— 由代码拼接，LLM 不参与
    disclaimer: str = DISCLAIMER_TEXT
    audit_note: str = AUDIT_TEXT

    # 统计信息，便于前端展示与埋点
    gap_count: int = 0
    question_count: int = 0
    dimension_count: int = 0


def build_report(
    match_result: Optional[dict] = None,
    attributions: Optional[list[GapAttribution]] = None,
    verification_plan: Optional[list[VerificationQuestion]] = None,
    capability_coverage: Optional[list[CapabilityCoverage]] = None,
) -> HrInsightReport:
    """
    组装 HR 报告。

    :param match_result: match-agent 的原始输出（MatchResult）
    :param attributions: 归因结果
    :param verification_plan: 验证计划
    :param capability_coverage: 能力维度覆盖
    """
    attributions = attributions or []
    verification_plan = verification_plan or []
    capability_coverage = capability_coverage or []

    overview = MatchOverview()
    if match_result:
        overview = MatchOverview(
            final_score=float(match_result.get("final_score") or 0.0),
            semantic_score=float(match_result.get("semantic_score") or 0.0),
            keyword_score=float(match_result.get("keyword_score") or 0.0),
            business_score=float(match_result.get("business_score") or 0.0),
            matched_skills=list(match_result.get("matched_skills") or []),
        )

    return HrInsightReport(
        match_overview=overview,
        capability_coverage=capability_coverage,
        attributions=attributions,
        verification_plan=verification_plan,
        gap_count=len(attributions),
        question_count=len(verification_plan),
        dimension_count=len(capability_coverage),
    )
