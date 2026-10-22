package com.careerpilot.app.ui.interview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.careerpilot.app.core.Format
import com.careerpilot.app.ui.components.EmptyBox
import com.careerpilot.app.ui.components.ErrorBox
import com.careerpilot.app.ui.components.PickerField
import com.careerpilot.app.ui.components.PrimaryButton
import com.careerpilot.app.ui.components.SectionCard
import com.careerpilot.app.ui.components.TagChip
import com.careerpilot.app.ui.components.TaskProgress
import com.careerpilot.app.ui.theme.BrandSuccess
import com.careerpilot.app.ui.theme.BrandWarning

private val questionTypeLabels = mapOf(
    "technical" to "技术题",
    "behavioral" to "行为面试题",
    "followup" to "追问",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterviewScreen(
    onBack: () -> Unit,
    viewModel: InterviewViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { viewModel.loadOptions() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模拟面试") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "面试设置", subtitle = "AI 出题 + STAR 结构化评分") {
                    PickerField(
                        label = "简历（可选）",
                        items = viewModel.resumes,
                        selected = viewModel.resumes.firstOrNull { it.resumeId == viewModel.selectedResumeId },
                        itemLabel = { it.title.ifBlank { "未命名简历" } },
                        onSelect = { viewModel.selectedResumeId = it.resumeId },
                        placeholder = "选择一份简历",
                        emptyText = "还没有简历",
                    )
                    Spacer(Modifier.height(10.dp))
                    PickerField(
                        label = "目标 JD",
                        items = viewModel.jds,
                        selected = viewModel.jds.firstOrNull { it.jdId == viewModel.selectedJdId },
                        itemLabel = {
                            if (it.company.isNullOrBlank()) it.title else "${it.title} · ${it.company}"
                        },
                        onSelect = { viewModel.selectJd(it.jdId) },
                        placeholder = "选择一个 JD",
                        emptyText = "还没有 JD",
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("面试轮数", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..6).forEach { count ->
                            FilterChip(
                                selected = viewModel.totalRounds == count,
                                onClick = { viewModel.totalRounds = count },
                                label = { Text("$count") },
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TagChip(
                            text = "第 ${viewModel.round} / ${viewModel.totalRounds} 题",
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (viewModel.sessionDone) {
                            Spacer(Modifier.width(8.dp))
                            TagChip(text = "面试已结束", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = if (viewModel.currentQuestion == null) "开始面试" else "下一题",
                        onClick = { viewModel.startQuestion { } },
                        loading = viewModel.running,
                        enabled = !viewModel.sessionDone,
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Default.PlayCircle, contentDescription = null) },
                    )

                    if (viewModel.running) {
                        Spacer(Modifier.height(12.dp))
                        TaskProgress(
                            progress = viewModel.progress,
                            statusText = Format.taskStatus(viewModel.status),
                            agent = viewModel.currentAgent,
                        )
                    }
                }
            }

            if (viewModel.errorMessage.isNotBlank() && !viewModel.running) {
                item { ErrorBox(viewModel.errorMessage) }
            }

            item {
                val question = viewModel.currentQuestion
                SectionCard(title = "面试现场") {
                    if (question == null) {
                        EmptyBox("选择 JD 后点击「开始面试」")
                        return@SectionCard
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(14.dp),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TagChip(
                                    text = questionTypeLabels[question.questionType]
                                        ?: question.questionType.ifBlank { "综合题" },
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (question.focus.isNotBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "考察点：${question.focus}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                question.question,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = viewModel.answer,
                        onValueChange = { viewModel.answer = it },
                        label = { Text("你的回答") },
                        placeholder = { Text("按 STAR 结构回答：情境(S) → 任务(T) → 行动(A) → 结果(R)") },
                        minLines = 6,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "提交并评分",
                        onClick = { viewModel.submitAnswer { } },
                        loading = viewModel.running,
                        enabled = viewModel.answer.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    )
                }
            }

            if (viewModel.records.isNotEmpty()) {
                item {
                    SectionCard(
                        title = "逐轮记录",
                        subtitle = "平均得分 ${viewModel.averageScore}",
                    ) {
                        viewModel.records.forEach { item ->
                            val score = item.evaluation.score
                            val color = when {
                                score >= 7 -> BrandSuccess
                                score >= 5 -> BrandWarning
                                else -> MaterialTheme.colorScheme.error
                            }
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "第 ${item.round} 题",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    TagChip(text = "${score} 分", color = color)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "问：${item.question}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "答：${item.answer}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (item.evaluation.feedback.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        item.evaluation.feedback,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                val coverage = item.evaluation.starCoverage
                                if (coverage != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        StarTag("S", coverage.situation)
                                        StarTag("T", coverage.task)
                                        StarTag("A", coverage.action)
                                        StarTag("R", coverage.result)
                                    }
                                }
                                if (item.evaluation.followup.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "追问：${item.evaluation.followup}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BrandWarning,
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
private fun StarTag(label: String, covered: Boolean) {
    TagChip(
        text = "$label ${if (covered) "✓" else "✗"}",
        color = if (covered) BrandSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
