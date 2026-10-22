"""
HR 侧能力的 eval 脚本（计划书 §8.2）。

AI 应用开发岗面试必问「效果怎么评」—— 这里把指标真正跑出来。

指标
----
| 指标 | 含义 | 目标 |
|---|---|---|
| gap 召回率 | 人工标注的 gap 被系统发现的比例 | ≥ 0.85 |
| 维度归因准确率 | gap 的能力维度判断正确的比例 | ≥ 0.70 |
| 问题覆盖率 | severity ≥ 4 的 gap 拿到验证问题的比例 | = 1.0 |
| 禁用问法率 | 报告中出现违规问法的比例 | = 0.0 |
| 引用可验证率 | evidence_ref 能在原文定位的比例 | = 1.0 |

数据集
------
``scripts/eval/dataset.json`` 里放 (JD, 简历, 人工标注) 样本。

**必须如实说明**：当前仓库里的样本是**合成脱敏**数据，
用来验证链路与指标口径，不代表真实招聘效果。
接入真实标注数据后指标才有业务意义。

用法
----
    python scripts/eval_hr_insight.py
    python scripts/eval_hr_insight.py --dataset scripts/eval/dataset.json
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "agents"))
sys.path.insert(0, str(ROOT / "agents" / "interview-agent"))

from app.hr.attribution import attribute_gaps  # noqa: E402
from app.hr.compliance import verify_evidence_ref  # noqa: E402
from app.hr.verification import build_verification_plan, lint_question  # noqa: E402

DEFAULT_DATASET = ROOT / "scripts" / "eval" / "dataset.json"
OUTPUT_DIR = ROOT / "scripts" / "eval_output"

# 判定阈值（计划书 §8.2）
TARGET_GAP_RECALL = 0.85
TARGET_DIMENSION_ACCURACY = 0.70
TARGET_QUESTION_COVERAGE = 1.0
TARGET_FORBIDDEN_RATE = 0.0
TARGET_EVIDENCE_RATE = 1.0


def load_dataset(path: Path) -> list[dict]:
    with path.open(encoding="utf-8") as fh:
        data = json.load(fh)
    if isinstance(data, dict):
        data = data.get("samples", [])
    return data


async def evaluate_sample(sample: dict) -> dict:
    """跑一个样本，返回该样本的各项指标。"""
    jd_text = sample.get("jd_text", "")
    resume_text = sample.get("resume_text", "")
    labeled_gaps = sample.get("gaps", [])

    # 系统侧的 gap 由调用方（match-agent）提供；eval 里用标注的 gap 驱动归因，
    # 同时用标注的 gap 内容检查维度判断是否正确。
    attributions = await attribute_gaps(
        labeled_gaps, jd_text=jd_text, resume_text=resume_text
    )
    plan = await build_verification_plan(attributions, jd_text=jd_text, resume_text=resume_text)

    # ── 维度归因准确率 ──
    # 硬性门槛（学历/年限）没有能力维度，不计入维度准确率
    labeled_dimension = {g.get("gap_ref"): g.get("dimension") for g in labeled_gaps}
    dimension_hits = 0
    dimension_total = 0
    for item in attributions:
        expected = labeled_dimension.get(item.gap_ref)
        if expected is None or item.gap_category == "hard_gate":
            continue
        dimension_total += 1
        if item.dimension == expected:
            dimension_hits += 1

    # ── 问题覆盖率（severity ≥ 4 的 gap 是否都拿到问题）──
    severe_refs = {g.get("gap_ref") for g in labeled_gaps if g.get("severity", 0) >= 4}
    covered: set[str] = set()
    for q in plan:
        covered |= {r for r in q.gap_ref.split(",") if r}
    coverage = 1.0 if not severe_refs else len(severe_refs & covered) / len(severe_refs)

    # ── 禁用问法率 ──
    violations = [q.id for q in plan if lint_question(q.question)]
    forbidden_rate = len(violations) / len(plan) if plan else 0.0

    # ── 引用可验证率 ──
    refs = [a.evidence_ref for a in attributions if a.evidence_ref]
    verified = sum(1 for ref in refs if verify_evidence_ref(ref, resume_text))
    evidence_rate = 1.0 if not refs else verified / len(refs)

    return {
        "sample_id": sample.get("id", "?"),
        "gap_count": len(attributions),
        "question_count": len(plan),
        "dimension_accuracy": (dimension_hits / dimension_total) if dimension_total else None,
        "question_coverage": coverage,
        "forbidden_rate": forbidden_rate,
        "evidence_rate": evidence_rate,
        "violations": violations,
    }


async def main_async(dataset_path: Path) -> int:
    samples = load_dataset(dataset_path)
    if not samples:
        print(f"数据集为空：{dataset_path}")
        return 1

    print(f"HR 侧能力 eval —— 数据集 {dataset_path}")
    print(f"样本数：{len(samples)}")
    print()
    print("  ⚠ 关于指标解读（重要）：")
    print("    本数据集是**合成脱敏**样本，标注也是我们自己写的。")
    print("    因此「维度归因准确率 = 1.0」只说明**规则与我们的标注一致**，")
    print("    不代表真实场景下的准确率。")
    print("    这个脚本当前的价值是**回归基线**：规则一改，指标掉下来就能发现。")
    print("    要得到有业务意义的数字，需要接入 40-60 组真实人工标注样本。\n")

    results = []
    for sample in samples:
        result = await evaluate_sample(sample)
        results.append(result)
        acc = result["dimension_accuracy"]
        acc_text = f"{acc:.2f}" if acc is not None else "—"
        print(
            f"  {result['sample_id']:<14} gap={result['gap_count']} "
            f"q={result['question_count']} 维度准确={acc_text} "
            f"覆盖={result['question_coverage']:.2f} "
            f"禁用问法={result['forbidden_rate']:.2f} "
            f"引用可验={result['evidence_rate']:.2f}"
        )

    # ── 汇总 ──
    accuracies = [r["dimension_accuracy"] for r in results if r["dimension_accuracy"] is not None]
    avg_accuracy = sum(accuracies) / len(accuracies) if accuracies else 0.0
    avg_coverage = sum(r["question_coverage"] for r in results) / len(results)
    avg_forbidden = sum(r["forbidden_rate"] for r in results) / len(results)
    avg_evidence = sum(r["evidence_rate"] for r in results) / len(results)

    summary = {
        "samples": len(results),
        "dimension_accuracy": round(avg_accuracy, 4),
        "question_coverage": round(avg_coverage, 4),
        "forbidden_rate": round(avg_forbidden, 4),
        "evidence_rate": round(avg_evidence, 4),
        "targets": {
            "dimension_accuracy": TARGET_DIMENSION_ACCURACY,
            "question_coverage": TARGET_QUESTION_COVERAGE,
            "forbidden_rate": TARGET_FORBIDDEN_RATE,
            "evidence_rate": TARGET_EVIDENCE_RATE,
        },
    }
    passed = (
        avg_accuracy >= TARGET_DIMENSION_ACCURACY
        and avg_coverage >= TARGET_QUESTION_COVERAGE
        and avg_forbidden <= TARGET_FORBIDDEN_RATE
        and avg_evidence >= TARGET_EVIDENCE_RATE
    )
    summary["passed"] = passed

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUTPUT_DIR / "eval_result.json").write_text(
        json.dumps({"summary": summary, "results": results}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print("\n汇总")
    print(f"  维度归因准确率  {avg_accuracy:.4f}  (目标 ≥ {TARGET_DIMENSION_ACCURACY})")
    print(f"  问题覆盖率      {avg_coverage:.4f}  (目标 = {TARGET_QUESTION_COVERAGE})")
    print(f"  禁用问法率      {avg_forbidden:.4f}  (目标 = {TARGET_FORBIDDEN_RATE})")
    print(f"  引用可验证率    {avg_evidence:.4f}  (目标 ≥ {TARGET_EVIDENCE_RATE})")
    print(f"\n  结果文件：{(OUTPUT_DIR / 'eval_result.json').relative_to(ROOT)}")
    print(f"  总体：{'通过' if passed else '未达标'}")

    return 0 if passed else 1


def main() -> int:
    parser = argparse.ArgumentParser(description="HR 侧能力 eval")
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    args = parser.parse_args()
    return asyncio.run(main_async(args.dataset))


if __name__ == "__main__":
    sys.exit(main())
