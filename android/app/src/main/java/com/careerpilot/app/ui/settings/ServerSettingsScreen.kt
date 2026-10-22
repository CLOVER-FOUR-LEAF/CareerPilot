package com.careerpilot.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.careerpilot.app.data.repo.ServiceLocator
import com.careerpilot.app.ui.components.PrimaryButton
import com.careerpilot.app.ui.components.SectionCard

/**
 * 服务器地址设置。
 *
 * 后端上线后只需要在这里改成正式域名，不用重新打包 APK。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(onBack: () -> Unit) {
    val store = ServiceLocator.tokenStore
    var input by remember { mutableStateOf(store.baseUrl) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务器设置") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            SectionCard(
                title = "后端地址",
                subtitle = "填到网关的 /api/v1 路径，缺省会自动补上",
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        saved = false
                    },
                    label = { Text("地址") },
                    placeholder = { Text("http://10.0.2.2:8080/api/v1/") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = "保存",
                    onClick = {
                        store.baseUrl = input
                        input = store.baseUrl
                        saved = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (saved) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "已保存：${store.baseUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard(title = "当前配置") {
                Text(
                    "地址：${store.baseUrl}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "已登录：${if (store.isAuthenticated) "是" else "否"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "模拟器访问宿主机请用 10.0.2.2；真机调试请填电脑在局域网里的 IP，" +
                        "并确认手机与电脑在同一网络。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
