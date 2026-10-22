package com.careerpilot.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.careerpilot.app.core.Format
import com.careerpilot.app.data.repo.Session
import com.careerpilot.app.ui.components.ErrorBox
import com.careerpilot.app.ui.components.LabeledValue
import com.careerpilot.app.ui.components.PrimaryButton
import com.careerpilot.app.ui.components.SectionCard
import com.careerpilot.app.ui.components.TagChip
import com.careerpilot.app.ui.theme.BrandSuccess
import com.careerpilot.app.ui.theme.BrandWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: ProfileViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人中心") },
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
            if (viewModel.error.isNotBlank()) {
                item { ErrorBox(viewModel.error) }
            }
            if (viewModel.message.isNotBlank()) {
                item {
                    SectionCard {
                        Text(
                            viewModel.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = BrandSuccess,
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val avatar = Session.currentUser?.avatar
                        if (!avatar.isNullOrBlank()) {
                            AsyncImage(
                                model = avatar,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    Session.initials,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                Session.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                Session.currentUser?.email.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                val membership = viewModel.membership
                SectionCard(title = "会员权益") {
                    if (membership == null) {
                        LabeledValue("状态", "加载中…")
                    } else {
                        LabeledValue("等级", membership.levelName.ifBlank { membership.level })
                        LabeledValue(
                            "到期时间",
                            membership.expireAt?.let { Format.dateTime(it) } ?: "永久有效",
                        )
                        LabeledValue(
                            "AI 额度",
                            "${membership.aiQuotaRemaining} / ${membership.aiQuotaTotal}",
                        )
                        Spacer(Modifier.height(6.dp))
                        TagChip(
                            text = if (membership.autoRenew) "自动续费中" else "未开通自动续费",
                            color = if (membership.autoRenew) BrandSuccess else BrandWarning,
                        )
                    }
                }
            }

            item {
                SectionCard(title = "求职画像", subtitle = "这些信息会用于匹配与规划") {
                    ProfileField("昵称", viewModel.nickname, viewModel::onNicknameChange)
                    ProfileField("手机号", viewModel.phone, viewModel::onPhoneChange)
                    ProfileField("所在城市", viewModel.city, viewModel::onCityChange)
                    ProfileField("当前职位", viewModel.currentTitle, viewModel::onCurrentTitleChange)
                    ProfileField(
                        label = "工作年限",
                        value = viewModel.yearsOfExperience,
                        onValueChange = viewModel::onYearsChange,
                        numeric = true,
                    )
                    ProfileField("学历", viewModel.educationLevel, viewModel::onEducationChange)
                    ProfileField("学校", viewModel.school, viewModel::onSchoolChange)
                    ProfileField("专业", viewModel.major, viewModel::onMajorChange)
                    ProfileField(
                        label = "毕业年份",
                        value = viewModel.graduationYear,
                        onValueChange = viewModel::onGraduationYearChange,
                        numeric = true,
                    )
                    ProfileField("目标行业", viewModel.targetIndustry, viewModel::onTargetIndustryChange)
                    ProfileField("目标岗位", viewModel.targetPosition, viewModel::onTargetPositionChange)
                    ProfileField(
                        label = "技能（用顿号分隔）",
                        value = viewModel.skillsText,
                        onValueChange = viewModel::onSkillsChange,
                    )

                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = "保存资料",
                        onClick = { viewModel.save { } },
                        loading = viewModel.saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                SectionCard(title = "设置") {
                    SettingRow(
                        icon = { Icon(Icons.Default.Dns, contentDescription = null) },
                        title = "服务器地址",
                        subtitle = "切换后端环境",
                        onClick = onOpenServerSettings,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.logout(onLoggedOut) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("退出登录", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (numeric) {
                androidx.compose.ui.text.input.KeyboardType.Number
            } else {
                androidx.compose.ui.text.input.KeyboardType.Text
            },
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
