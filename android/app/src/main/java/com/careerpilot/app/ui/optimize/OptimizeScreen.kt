package com.careerpilot.app.ui.optimize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.careerpilot.app.core.Format
import com.careerpilot.app.ui.components.CopyableBlock
import com.careerpilot.app.ui.components.EmptyBox
import com.careerpilot.app.ui.components.ErrorBox
import com.careerpilot.app.ui.components.LabeledValue
import com.careerpilot.app.ui.components.PickerField
import com.careerpilot.app.ui.components.PrimaryButton
import com.careerpilot.app.ui.components.SectionCard
import com.careerpilot.app.ui.components.TagChip
import com.careerpilot.app.ui.components.TagFlow
import com.careerpilot.app.ui.components.TaskProgress
import com.careerpilot.app.ui.theme.BrandSuccess

private val tones = listOf(
    "professional" to "专业严谨",
    "data-driven" to "量化导向",
    "concise" to "简洁精炼",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimizeScreen(
    onBack: () -> Unit,
    viewModel: OptimizeViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { viewModel.loadJds() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("简历优化") },
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
                SectionCard(
                    title = "待优化的经历描述",
                    subtitle = "用 STAR 法则重写，补齐量化结果与关键词",
                ) {
                    PickerField(
                        label = "目标 JD（可选）",
                        items = viewModel.jds,
                        selected = viewModel.jds.firstOrNull { it.jdId == viewModel.selectedJdId },
                        itemLabel = {
                            if (it.company.isNullOrBlank()) it.title else "${it.title} · ${it.company}"
                        },
                        onSelect = { viewModel.selectJd(it.jdId) },
                        placeholder = "选择一个 JD",
                        emptyText = "还没有 JD",
                    )
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = viewModel.original,
                        onValueChange = { viewModel.original = it },
                        label = { Text("经历描述") },
                        placeholder = { Text("如：负责用户增长相关工作") },
                        minLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("改写风格", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tones.forEach { (value, label) ->
                            FilterChip(
                                selected = viewModel.tone == value,
                                onClick = { viewModel.tone = value },
                                label = { Text(label) },
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "开始优化",
                        onClick = { viewModel.submit { } },
                        loading = viewModel.running,
                        enabled = viewModel.original.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
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
                val result = viewModel.result
                SectionCard(title = "优化结果") {
                    if (result == null) {
                        EmptyBox("填写左侧内容并点击「开始优化」")
                        return@SectionCard
                    }

                    Text("原文", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    CopyableBlock(result.original, minHeight = 90)

                    Spacer(Modifier.height(14.dp))
                    Text("改写后", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    CopyableBlock(result.rewritten, minHeight = 90)

                    val injected = result.injectedSkills.orEmpty()
                    if (injected.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("植入关键词", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        TagFlow(injected, color = BrandSuccess)
                    }

                    if (result.changeNote.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text("改写说明", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            result.changeNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val star = result.starBreakdown
                    if (star != null) {
                        Spacer(Modifier.height(14.dp))
                        Text("STAR 拆解", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        LabeledValue("S 情境", star.situation.ifBlank { "—" })
                        LabeledValue("T 任务", star.task.ifBlank { "—" })
                        LabeledValue("A 行动", star.action.ifBlank { "—" })
                        LabeledValue("R 结果", star.result.ifBlank { "—" })
                    }
                }
            }
        }
    }
}
