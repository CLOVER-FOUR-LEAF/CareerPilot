package com.careerpilot.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Work
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.careerpilot.app.data.repo.Session
import com.careerpilot.app.ui.components.ErrorBox
import com.careerpilot.app.ui.components.LoadingBox
import com.careerpilot.app.ui.nav.Routes

private data class Feature(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val route: String,
)

private data class Tool(
    val title: String,
    val icon: ImageVector,
    val route: String,
)

private val features = listOf(
    Feature(
        title = "简历分析",
        description = "上传简历，获取 ATS 多维度评分与优化建议",
        icon = Icons.Default.Description,
        gradient = listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
        route = Routes.RESUME,
    ),
    Feature(
        title = "JD 解析",
        description = "粘贴职位描述，智能提取关键信息与技能要求",
        icon = Icons.Default.Work,
        gradient = listOf(Color(0xFFF093FB), Color(0xFFF5576C)),
        route = Routes.JD,
    ),
    Feature(
        title = "匹配打分",
        description = "简历与 JD 智能匹配，发现能力差距",
        icon = Icons.Default.CenterFocusStrong,
        gradient = listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)),
        route = Routes.MATCH,
    ),
)

private val tools = listOf(
    Tool("简历优化", Icons.Default.EditNote, Routes.OPTIMIZE),
    Tool("模拟面试", Icons.Default.Forum, Routes.INTERVIEW),
    Tool("生成求职信", Icons.Default.Email, Routes.COVER_LETTER),
    Tool("职业规划", Icons.Default.RocketLaunch, Routes.CAREER_PLAN),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("欢迎回来", style = MaterialTheme.typography.titleLarge)
                        Text(
                            Session.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(features.size) { index ->
                val feature = features[index]
                FeatureCard(feature) { onNavigate(feature.route) }
            }

            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("快速统计")
                Spacer(Modifier.height(10.dp))
                StatsCard(
                    resumeCount = viewModel.resumeCount,
                    jdCount = viewModel.jdCount,
                    matchCount = viewModel.matchCount,
                    loading = viewModel.loading,
                )
            }

            if (viewModel.error.isNotBlank()) {
                item { ErrorBox(viewModel.error) }
            }

            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("AI 工具")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tools.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { tool ->
                                ToolCard(
                                    tool = tool,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigate(tool.route) },
                                )
                            }
                            // 奇数个时补一个空位，保持两列对齐
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun FeatureCard(feature: Feature, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(feature.gradient)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    feature.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(feature.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    feature.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsCard(
    resumeCount: Int,
    jdCount: Int,
    matchCount: Int,
    loading: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        if (loading) {
            LoadingBox(label = "统计中…")
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem("简历数量", resumeCount)
                StatItem("JD 数量", jdCount)
                StatItem("匹配记录", matchCount)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ToolCard(tool: Tool, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                tool.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(tool.title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
