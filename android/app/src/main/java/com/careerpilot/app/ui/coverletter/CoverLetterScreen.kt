package com.careerpilot.app.ui.coverletter

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.careerpilot.app.core.Format
import com.careerpilot.app.ui.components.CopyableBlock
import com.careerpilot.app.ui.components.EmptyBox
import com.careerpilot.app.ui.components.ErrorBox
import com.careerpilot.app.ui.components.PickerField
import com.careerpilot.app.ui.components.PrimaryButton
import com.careerpilot.app.ui.components.SectionCard
import com.careerpilot.app.ui.components.TagChip
import com.careerpilot.app.ui.components.TagFlow
import com.careerpilot.app.ui.components.TaskProgress

private val toneOptions = listOf(
    "professional" to "专业稳重",
    "passionate" to "热情积极",
    "humble" to "谦逊真诚",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverLetterScreen(
    onBack: () -> Unit,
    viewModel: CoverLetterViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { viewModel.loadOptions() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("生成求职信") },
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
                SectionCard(title = "求职信设置", subtitle = "结合岗位 JD 自动撰写，突出核心技能") {
                    PickerField(
                        label = "职位 JD（可选）",
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
                        value = viewModel.jobTitle,
                        onValueChange = { viewModel.jobTitle = it },
                        label = { Text("应聘岗位") },
                        placeholder = { Text("如：高级后端工程师") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = viewModel.companyName,
                        onValueChange = { viewModel.companyName = it },
                        label = { Text("目标公司") },
                        placeholder = { Text("如：某厂") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("写作语气", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        toneOptions.forEach { (value, label) ->
                            FilterChip(
                                selected = viewModel.tone == value,
                                onClick = { viewModel.tone = value },
                                label = { Text(label) },
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "生成求职信",
                        onClick = { viewModel.submit { } },
                        loading = viewModel.running,
                        enabled = viewModel.jobTitle.isNotBlank(),
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
                SectionCard(title = "求职信正文") {
                    if (result == null) {
                        EmptyBox("填写岗位信息后点击「生成求职信」")
                        return@SectionCard
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TagChip(
                            text = toneOptions.firstOrNull { it.first == result.tone }?.second
                                ?: result.tone.ifBlank { "默认" },
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        TagChip(
                            text = "${result.wordCount} 字",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    CopyableBlock(result.content, minHeight = 260)

                    val skills = result.highlightedSkills.orEmpty()
                    Spacer(Modifier.height(14.dp))
                    Text("突出技能", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    TagFlow(skills, emptyText = "暂无")
                }
            }
        }
    }
}
