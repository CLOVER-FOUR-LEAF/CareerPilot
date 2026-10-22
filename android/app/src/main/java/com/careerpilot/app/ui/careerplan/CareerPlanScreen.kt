package com.careerpilot.app.ui.careerplan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Flag
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.careerpilot.app.core.Format
import com.careerpilot.app.ui.components.EmptyBox
import com.careerpilot.app.ui.components.ErrorBox
import com.careerpilot.app.ui.components.PrimaryButton
import com.careerpilot.app.ui.components.SectionCard
import com.careerpilot.app.ui.components.TagChip
import com.careerpilot.app.ui.components.TagFlow
import com.careerpilot.app.ui.components.TaskProgress
import com.careerpilot.app.ui.theme.BrandWarning

private val industries = listOf(
    "互联网",
    "金融科技",
    "人工智能",
    "企业服务",
    "电商零售",
    "智能制造",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CareerPlanScreen(
    onBack: () -> Unit,
    viewModel: CareerPlanViewModel = viewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("职业规划") },
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
                SectionCard(title = "规划输入", subtitle = "基于当前岗位与目标行业生成成长路线图") {
                    OutlinedTextField(
                        value = viewModel.currentTitle,
                        onValueChange = { viewModel.currentTitle = it },
                        label = { Text("当前职位") },
                        placeholder = { Text("如：后端工程师") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = viewModel.yearsOfExperience.toString(),
                        onValueChange = { input ->
                            viewModel.yearsOfExperience =
                                input.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0
                        },
                        label = { Text("工作年限") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("目标行业", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {                        industries.forEach { industry ->
                            FilterChip(
                                selected = viewModel.targetIndustry == industry,
                                onClick = { viewModel.targetIndustry = industry },
                                label = { Text(industry) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = viewModel.targetPosition,
                        onValueChange = { viewModel.targetPosition = it },
                        label = { Text("目标岗位") },
                        placeholder = { Text("如：高级后端工程师") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = viewModel.skillsText,
                        onValueChange = { viewModel.skillsText = it },
                        label = { Text("现有技能（可选，用顿号分隔）") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "生成规划",
                        onClick = { viewModel.submit { } },
                        loading = viewModel.running,
                        enabled = viewModel.currentTitle.isNotBlank() &&
                            viewModel.targetPosition.isNotBlank(),
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
                SectionCard(title = "规划结果") {
                    if (result == null) {
                        EmptyBox("填写左侧信息后点击「生成规划」")
                        return@SectionCard
                    }

                    if (result.summary.isNotBlank()) {
                        Text("总体建议", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(14.dp),
                        ) {
                            Text(result.summary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    val roadmap = result.roadmap.orEmpty()
                    if (roadmap.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("成长路线图", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        roadmap.forEachIndexed { index, phase ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(
                                                if (index == 0) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (index == 0) {
                                                androidx.compose.ui.graphics.Color.White
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        phase.phase.ifBlank { "阶段 ${index + 1}" },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                if (phase.goal.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        phase.goal,
                                        modifier = Modifier.padding(start = 32.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                val actions = phase.actions.orEmpty()
                                if (actions.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Column(Modifier.padding(start = 32.dp)) {
                                        actions.forEach { action ->
                                            Row(Modifier.fillMaxWidth()) {
                                                Text("• ", style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    action,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                    }
                                }
                                if (phase.milestone.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.padding(start = 32.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.Flag,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = BrandWarning,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "里程碑：${phase.milestone}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BrandWarning,
                                        )
                                    }
                                }
                                val docIds = phase.docIds.orEmpty()
                                if (docIds.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    TagFlow(
                                        docIds,
                                        modifier = Modifier.padding(start = 32.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    val gap = result.skillGap.orEmpty()
                    if (gap.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("待补齐技能", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        TagFlow(gap, color = BrandWarning)
                    }

                    val citations = result.citations.orEmpty()
                    if (citations.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("引用知识库", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        citations.forEach { citation ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TagChip(
                                        text = citation.docId,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        citation.source,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                if (citation.version.isNotBlank()) {
                                    Text(
                                        "版本：${citation.version}",
                                        modifier = Modifier.padding(start = 4.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
