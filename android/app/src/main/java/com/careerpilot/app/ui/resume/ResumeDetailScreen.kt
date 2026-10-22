package com.careerpilot.app.ui.resume

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.careerpilot.app.core.Format
import com.careerpilot.app.ui.components.CopyableBlock
import com.careerpilot.app.ui.components.EmptyBox
import com.careerpilot.app.ui.components.ErrorBox
import com.careerpilot.app.ui.components.LabeledValue
import com.careerpilot.app.ui.components.LoadingBox
import com.careerpilot.app.ui.components.ScoreBadge
import com.careerpilot.app.ui.components.SectionCard
import com.careerpilot.app.ui.components.TagChip
import com.careerpilot.app.ui.components.TagFlow
import com.careerpilot.app.ui.theme.BrandSuccess
import com.careerpilot.app.ui.theme.BrandWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeDetailScreen(
    resumeId: Long,
    onBack: () -> Unit,
    viewModel: ResumeDetailViewModel = viewModel(key = "resume-detail-$resumeId"),
) {
    LaunchedEffect(resumeId) { viewModel.load(resumeId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.detail?.title?.ifBlank { "简历详情" } ?: "简历详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.reparse(resumeId) { } },
                        enabled = !viewModel.reparsing,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "重新解析")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        val detail = viewModel.detail
        if (viewModel.loading && detail == null) {
            BoxedContent(padding) { LoadingBox() }
            return@Scaffold
        }
        if (detail == null) {
            BoxedContent(padding) {
                ErrorBox(viewModel.error.ifBlank { "简历加载失败" })
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (viewModel.error.isNotBlank()) {
                item { ErrorBox(viewModel.error) }
            }

            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            LabeledValue("标题", detail.title.ifBlank { "未命名简历" })
                            LabeledValue(
                                "解析状态",
                                Format.parseStatus(detail.parseStatus),
                                valueColor = when (detail.parseStatus.uppercase()) {
                                    "SUCCESS" -> BrandSuccess
                                    "FAILED" -> MaterialTheme.colorScheme.error
                                    else -> BrandWarning
                                },
                            )
                            LabeledValue("更新时间", Format.dateTime(detail.updatedAt))
                            LabeledValue("业务 ID", detail.resumeBizId)
                        }
                        ScoreBadge(detail.atsScore?.toDouble())
                    }
                }
            }

            item {
                SectionCard(
                    title = "ATS 评分",
                    subtitle = "综合体检结果与优化建议",
                ) {
                    val ats = viewModel.ats
                    if (ats == null) {
                        EmptyBox("暂无评分结果，可点右上角重新解析")
                        return@SectionCard
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreBadge(ats.overallScore, suffix = " 分")
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "段落 ${ats.sectionCount} · 词数 ${ats.wordCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (ats.hasContact) "已包含联系方式" else "缺少联系方式",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    val dimensions = ats.dimensions.orEmpty()
                    if (dimensions.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("分维度得分", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        dimensions.forEach { dim ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        dim.name.ifBlank { "未命名维度" },
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    TagChip(
                                        text = Format.score(dim.score),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (dim.score.coerceIn(0.0, 100.0) / 100.0).toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                )
                                if (dim.feedback.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        dim.feedback,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    val skills = ats.skills.orEmpty()
                    if (skills.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("识别到的技能", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        TagFlow(skills.map { it.name })
                    }

                    val suggestions = ats.suggestions.orEmpty()
                    if (suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("优化建议", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        suggestions.forEach { suggestion ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    suggestion.title.ifBlank { suggestion.category },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (suggestion.detail.isNotBlank()) {
                                    Text(
                                        suggestion.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "简历正文") {
                    CopyableBlock(detail.textContent, minHeight = 160)
                }
            }

            if (viewModel.versions.isNotEmpty()) {
                item {
                    SectionCard(title = "版本记录") {
                        viewModel.versions.forEach { version ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "v${version.versionNo}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        Format.dateTime(version.createdAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (version.atsScore != null) {
                                    TagChip(
                                        text = "ATS ${version.atsScore}",
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxedContent(
    padding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
    ) {
        content()
    }
}
