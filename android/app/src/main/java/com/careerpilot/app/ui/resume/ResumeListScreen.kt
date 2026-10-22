package com.careerpilot.app.ui.resume

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.careerpilot.app.core.Format
import com.careerpilot.app.ui.components.EmptyBox
import com.careerpilot.app.ui.components.ErrorBox
import com.careerpilot.app.ui.components.LoadingBox
import com.careerpilot.app.ui.components.TagChip
import com.careerpilot.app.ui.theme.BrandSuccess
import com.careerpilot.app.ui.theme.BrandWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeListScreen(
    onOpenResume: (Long) -> Unit,
    viewModel: ResumeViewModel = viewModel(),
) {
    val context = LocalContext.current
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.upload(context, uri, title = null) { }
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("简历管理") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    picker.launch(
                        arrayOf(
                            "application/pdf",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        ),
                    )
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("上传简历") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = viewModel.keyword,
                onValueChange = { viewModel.search(it) },
                placeholder = { Text("搜索简历标题") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (viewModel.error.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                ErrorBox(viewModel.error)
            }

            Spacer(Modifier.height(10.dp))

            when {
                viewModel.loading && viewModel.resumes.isEmpty() -> LoadingBox()
                viewModel.resumes.isEmpty() -> EmptyBox("还没有简历，点右下角上传第一份吧")
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(viewModel.resumes, key = { it.resumeId }) { item ->
                        ResumeRow(
                            title = item.title.ifBlank { "未命名简历" },
                            score = item.atsScore,
                            parseStatus = item.parseStatus,
                            updatedAt = item.updatedAt,
                            onClick = { onOpenResume(item.resumeId) },
                            onDelete = {
                                pendingDeleteId = item.resumeId
                                showDeleteDialog = true
                            },
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog && pendingDeleteId != null) {
        val id = pendingDeleteId!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除简历") },
            text = { Text("删除后不可恢复，确认删除这份简历吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    pendingDeleteId = null
                    viewModel.delete(id) { }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    pendingDeleteId = null
                }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ResumeRow(
    title: String,
    score: Int?,
    parseStatus: String,
    updatedAt: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
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
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TagChip(
                        text = Format.parseStatus(parseStatus),
                        color = when (parseStatus.uppercase()) {
                            "SUCCESS" -> BrandSuccess
                            "FAILED" -> MaterialTheme.colorScheme.error
                            else -> BrandWarning
                        },
                    )
                    if (score != null) {
                        Spacer(Modifier.width(6.dp))
                        TagChip(text = "ATS $score", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "更新于 ${Format.dateTime(updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
