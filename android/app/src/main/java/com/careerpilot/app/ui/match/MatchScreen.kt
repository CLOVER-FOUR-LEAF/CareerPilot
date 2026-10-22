package com.careerpilot.app.ui.match

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(
    onOpenRecord: (String) -> Unit,
    viewModel: MatchViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { viewModel.loadAll() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("匹配打分") },
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
                SectionCard(title = "选择对象", subtitle = "选一份简历和一个 JD") {
                    PickerField(
                        label = "简历",
                        items = viewModel.resumes,
                        selected = viewModel.resumes.firstOrNull { it.resumeId == viewModel.selectedResumeId },
                        itemLabel = { it.title.ifBlank { "未命名简历" } },
                        onSelect = { viewModel.selectedResumeId = it.resumeId },
                        placeholder = "选择简历",
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
                        onSelect = { viewModel.selectedJdId = it.jdId },
                        placeholder = "选择 JD",
                        emptyText = "还没有 JD",
                    )

                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "开始评估",
                        onClick = { viewModel.evaluate { } },
                        loading = viewModel.evaluating,
                        enabled = viewModel.selectedResumeId != null && viewModel.selectedJdId != null,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (viewModel.evaluating) {
                        Spacer(Modifier.height(12.dp))
                        TaskProgress(
                            progress = viewModel.pollProgress,
                            statusText = "评估中，请稍候",
                        )
                    }
                }
            }

            if (viewModel.error.isNotBlank()) {
                item { ErrorBox(viewModel.error) }
            }

            viewModel.record?.let { record ->
                item { MatchResultCard(record) }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "历史记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (viewModel.history.isEmpty()) {
                item { EmptyBox("还没有匹配记录") }
            } else {
                items(viewModel.history, key = { it.recordId }) { record ->
                    HistoryRow(
                        title = "记录 ${record.recordId.takeLast(6)}",
                        score = record.finalScore,
                        status = record.status,
                        createdAt = record.createdAt,
                        onClick = { onOpenRecord(record.recordId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    title: String,
    score: Double?,
    status: String,
    createdAt: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.CenterFocusStrong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TagChip(
                        text = Format.taskStatus(status),
                        color = if (status == "SUCCESS") {
                            BrandSuccess
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Format.dateTime(createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                Format.score(score),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
