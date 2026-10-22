package com.careerpilot.app.ui.jd

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.careerpilot.app.core.Format
import com.careerpilot.app.ui.components.CopyableBlock
import com.careerpilot.app.ui.components.EmptyBox
import com.careerpilot.app.ui.components.ErrorBox
import com.careerpilot.app.ui.components.LabeledValue
import com.careerpilot.app.ui.components.LoadingBox
import com.careerpilot.app.ui.components.SectionCard
import com.careerpilot.app.ui.components.TagFlow
import com.careerpilot.app.ui.theme.BrandSuccess
import com.careerpilot.app.ui.theme.BrandWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JdDetailScreen(
    jdId: Long,
    onBack: () -> Unit,
    viewModel: JdDetailViewModel = viewModel(key = "jd-detail-$jdId"),
) {
    LaunchedEffect(jdId) { viewModel.load(jdId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.detail?.title?.ifBlank { "JD 详情" } ?: "JD 详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.reparse(jdId) },
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
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) { LoadingBox() }
            return@Scaffold
        }
        if (detail == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                ErrorBox(viewModel.error.ifBlank { "JD 加载失败" })
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
                    LabeledValue("职位", detail.title.ifBlank { "—" })
                    LabeledValue("公司", detail.company ?: "—")
                    LabeledValue("薪资", detail.salaryRange ?: "—")
                    LabeledValue(
                        "解析状态",
                        Format.parseStatus(detail.parseStatus),
                        valueColor = when (detail.parseStatus.uppercase()) {
                            "SUCCESS" -> BrandSuccess
                            "FAILED" -> MaterialTheme.colorScheme.error
                            else -> BrandWarning
                        },
                    )
                    LabeledValue("业务 ID", detail.jdBizId)
                    LabeledValue("创建时间", Format.dateTime(detail.createdAt))
                }
            }

            item {
                val parse = viewModel.parse
                SectionCard(title = "解析结果", subtitle = "由 jd-parser-agent 提取") {
                    if (parse == null) {
                        EmptyBox("解析结果还没生成，稍后刷新或点右上角重新解析")
                        return@SectionCard
                    }

                    LabeledValue("职位", parse.jobTitle.ifBlank { "—" })
                    LabeledValue("部门", parse.department ?: "—")
                    LabeledValue("地点", parse.location ?: "—")
                    LabeledValue("雇佣类型", parse.employmentType ?: "—")
                    LabeledValue(
                        "经验要求",
                        listOfNotNull(
                            parse.experienceMinYears?.let { "$it 年起" },
                            parse.experienceMaxYears?.let { "至 $it 年" },
                        ).joinToString(" ").ifBlank { "—" },
                    )
                    LabeledValue("学历要求", parse.educationRequired ?: "—")
                    LabeledValue("级别", parse.seniority ?: "—")
                    LabeledValue("行业", parse.industry ?: "—")
                    LabeledValue("解析得分", Format.score(parse.parseScore))
                    LabeledValue("解析方式", parse.parseMethod.ifBlank { "—" })

                    val skills = parse.skills.orEmpty()
                    if (skills.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("技能要求", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        TagFlow(skills.map { it.name })
                    }

                    BulletSection("岗位职责", parse.responsibilities.orEmpty())
                    BulletSection("任职要求", parse.requirements.orEmpty())
                    BulletSection("公司福利", parse.benefits.orEmpty())
                }
            }

            item {
                SectionCard(title = "JD 原文") {
                    CopyableBlock(detail.rawText, minHeight = 160)
                }
            }
        }
    }
}

@Composable
private fun BulletSection(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(14.dp))
    Text(title, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(Modifier.fillMaxWidth()) {
                Text("• ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
