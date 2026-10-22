package com.careerpilot.app.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.careerpilot.app.core.Format
import com.careerpilot.app.data.model.MatchRecord
import com.careerpilot.app.ui.components.LabeledValue
import com.careerpilot.app.ui.components.SectionCard
import com.careerpilot.app.ui.components.TagChip
import com.careerpilot.app.ui.components.TagFlow
import com.careerpilot.app.ui.theme.BrandSuccess
import com.careerpilot.app.ui.theme.BrandWarning

/** 差距的严重程度：>=5 是硬性条件，>=3 是中等，其余较轻。 */
fun severityLabel(severity: Int): String = when {
    severity >= 5 -> "硬性条件"
    severity >= 3 -> "$severity · 中"
    else -> "$severity · 低"
}

@Composable
fun severityColor(severity: Int): Color = when {
    severity >= 5 -> Color(0xFFE5484D)
    severity >= 3 -> BrandWarning
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** 匹配结果卡片，Match 页与详情页共用。 */
@Composable
fun MatchResultCard(
    record: MatchRecord,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("综合匹配分", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                Format.score(record.finalScore),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                Format.dateTime(record.completedAt ?: record.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreCell("语义匹配", record.semanticScore, Modifier.weight(1f))
            ScoreCell("关键词匹配", record.keywordScore, Modifier.weight(1f))
            ScoreCell("业务匹配", record.businessScore, Modifier.weight(1f))
        }

        val matched = record.matchedSkills.orEmpty()
        if (matched.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("命中技能", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            TagFlow(matched, color = BrandSuccess)
        }

        val gaps = record.gapList.orEmpty()
        if (gaps.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("能力差距", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            gaps.sortedByDescending { it.severity }.forEach { gap ->
                val color = severityColor(gap.severity)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.06f))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TagChip(text = severityLabel(gap.severity), color = color)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            gap.gapType.ifBlank { "差距" },
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    if (gap.gapContent.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            gap.gapContent,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (gap.suggestion.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "建议：${gap.suggestion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        LabeledValue("记录 ID", record.recordId)
        LabeledValue("简历 ID", record.resumeId.toString())
        LabeledValue("JD ID", record.jdId.toString())
        LabeledValue(
            "状态",
            Format.taskStatus(record.status),
            valueColor = if (record.status == "SUCCESS") {
                BrandSuccess
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun ScoreCell(label: String, score: Double?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            Format.score(score),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
