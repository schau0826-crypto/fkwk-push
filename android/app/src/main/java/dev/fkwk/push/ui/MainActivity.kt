package dev.fkwk.push.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import dev.fkwk.push.data.BarkLevel
import dev.fkwk.push.data.InstalledApp
import dev.fkwk.push.data.LogEntity
import dev.fkwk.push.domain.Priority
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ScreenGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFD4E2F9),
        Color(0xFFE9D5CA),
        Color(0xFFC9D6FF)
    )
)
private val GlassCard = Color.White.copy(alpha = 0.46f)
private val GlassInput = Color.White.copy(alpha = 0.64f)
private val AppleBlue = Color(0xFF0071E3)
private val AppleGreen = Color(0xFF34C759)
private val TextPrimary = Color(0xFF1D1D1F)
private val TextSecondary = Color(0xFF6E6E73)
private val CardShape = RoundedCornerShape(20.dp)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        onOpenNotificationAccess = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onOpenAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )
                }
            }
        }
    }
}

private enum class HomeTab(val title: String) {
    HISTORY("历史"),
    APPS("App"),
    SETTINGS("设置")
}

@Composable
fun HomeScreen(
    onOpenNotificationAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.settings.collectAsState()
    val logs by vm.recentLogs.collectAsState()
    val installedApps by vm.installedApps.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var appQuery by rememberSaveable { mutableStateOf("") }
    var highlightedPackage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.refreshInstalledApps()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenGradient)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = Color.White.copy(alpha = 0.42f)) {
                    NavigationBarItem(
                        selected = selectedTab == HomeTab.HISTORY.ordinal,
                        onClick = { selectedTab = HomeTab.HISTORY.ordinal },
                        icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                        label = { Text(HomeTab.HISTORY.title) },
                        colors = fastNavColors()
                    )
                    NavigationBarItem(
                        selected = selectedTab == HomeTab.APPS.ordinal,
                        onClick = { selectedTab = HomeTab.APPS.ordinal },
                        icon = { Icon(Icons.Outlined.Apps, contentDescription = null) },
                        label = { Text(HomeTab.APPS.title) },
                        colors = fastNavColors()
                    )
                    NavigationBarItem(
                        selected = selectedTab == HomeTab.SETTINGS.ordinal,
                        onClick = { selectedTab = HomeTab.SETTINGS.ordinal },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text(HomeTab.SETTINGS.title) },
                        colors = fastNavColors()
                    )
                }
            }
        ) { innerPadding ->
            when (HomeTab.entries[selectedTab]) {
                HomeTab.HISTORY -> HistoryTab(
                    logs = logs,
                    selectedCount = state.monitoredPackages.size,
                    appLabels = installedApps.associate { it.packageName to it.label },
                    onDisableApp = { vm.setPackageEnabled(it, false) },
                    onSetPriority = vm::setPackagePriority,
                    onOpenAppSettings = {
                        appQuery = it
                        highlightedPackage = it
                        selectedTab = HomeTab.APPS.ordinal
                    },
                    modifier = Modifier.padding(innerPadding)
                )
                HomeTab.APPS -> AppsTab(
                    apps = installedApps,
                    selectedPackages = state.monitoredPackages,
                    packagePriorities = state.packagePriorities,
                    query = appQuery,
                    highlightedPackage = highlightedPackage,
                    onQueryChange = {
                        appQuery = it
                        if (it != highlightedPackage) highlightedPackage = null
                    },
                    onRefresh = vm::refreshInstalledApps,
                    onSetPackages = vm::setPackagesEnabled,
                    onSetPackage = vm::setPackageEnabled,
                    onSetPriority = vm::setPackagePriority,
                    modifier = Modifier.padding(innerPadding)
                )
                HomeTab.SETTINGS -> SettingsTab(
                    barkServerUrl = state.barkServerUrl,
                    barkDeviceKey = state.barkDeviceKey,
                    barkTitleTemplate = state.barkTitleTemplate,
                    barkIconEnabled = state.barkIconEnabled,
                    barkIconBaseUrl = state.barkIconBaseUrl,
                    barkIconUploadToken = state.barkIconUploadToken,
                    lowLevel = state.barkLowLevel,
                    normalLevel = state.barkNormalLevel,
                    urgentLevel = state.barkUrgentLevel,
                    pauseWhenInteractive = state.pauseWhenInteractive,
                    blockedKeywords = state.blockedKeywords,
                    onSetBarkServerUrl = vm::setBarkServerUrl,
                    onSetBarkDeviceKey = vm::setBarkDeviceKey,
                    onSetTitleTemplate = vm::setBarkTitleTemplate,
                    onSetIconEnabled = vm::setBarkIconEnabled,
                    onSetIconBaseUrl = vm::setBarkIconBaseUrl,
                    onSetIconUploadToken = vm::setBarkIconUploadToken,
                    onSetLowLevel = vm::setBarkLowLevel,
                    onSetNormalLevel = vm::setBarkNormalLevel,
                    onSetUrgentLevel = vm::setBarkUrgentLevel,
                    onSetPauseWhenInteractive = vm::setPauseWhenInteractive,
                    onAddBlockedKeyword = vm::addBlockedKeyword,
                    onRemoveBlockedKeyword = vm::removeBlockedKeyword,
                    onOpenNotificationAccess = onOpenNotificationAccess,
                    onOpenAccessibility = onOpenAccessibility,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun HistoryTab(
    logs: List<LogEntity>,
    selectedCount: Int,
    appLabels: Map<String, String>,
    onDisableApp: (String) -> Unit,
    onSetPriority: (String, Priority) -> Unit,
    onOpenAppSettings: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
    val lastSeen = logs.firstOrNull()?.let { formatter.format(Date(it.postTime)) } ?: "暂无记录"
    Page(
        modifier = modifier,
        title = "通知历史",
        subtitle = "监听 $selectedCount 个 App · 最近 $lastSeen"
    ) {
        if (logs.isEmpty()) {
            SectionCard("还没有抓到通知", "开启通知读取权限后，让微信或企业微信发一条真实消息。") {
                Text("如果这里没有记录，问题在 Android 端捕获；如果有记录但 iPhone 没响，再查 Bark 或 iOS 通知设置。")
            }
        } else {
            logs.forEach {
                NotificationLogRow(
                    log = it,
                    appName = appLabels[it.packageName],
                    onDisableApp = onDisableApp,
                    onSetPriority = onSetPriority,
                    onOpenAppSettings = onOpenAppSettings
                )
            }
        }
    }
}

@Composable
private fun AppsTab(
    apps: List<InstalledApp>,
    selectedPackages: Set<String>,
    packagePriorities: Map<String, Priority>,
    query: String,
    highlightedPackage: String?,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onSetPackages: (Collection<String>, Boolean) -> Unit,
    onSetPackage: (String, Boolean) -> Unit,
    onSetPriority: (String, Priority) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSystemApps by rememberSaveable { mutableStateOf(false) }
    val visibleApps = apps.visibleApps(query, showSystemApps, selectedPackages)
    val visiblePackages = visibleApps.map { it.packageName }

    Page(
        modifier = modifier,
        title = "App 选择",
        subtitle = "已选 ${selectedPackages.size} 个 · 当前显示 ${visibleApps.size} 个"
    ) {
        SectionCard("筛选", "全选只作用于当前搜索结果，避免误选整机应用。") {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("搜索应用名称或包名") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = showSystemApps,
                    onClick = { showSystemApps = !showSystemApps },
                    label = { Text("系统应用") }
                )
                OutlinedButton(onClick = onRefresh) { Text("刷新") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSetPackages(visiblePackages, true) },
                    enabled = visiblePackages.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
                ) {
                    Text("全选当前")
                }
                OutlinedButton(
                    onClick = { onSetPackages(visiblePackages, false) },
                    enabled = visiblePackages.any { it in selectedPackages },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("清空当前")
                }
            }
        }

        if (visibleApps.isEmpty()) {
            SectionCard("没有匹配的应用", "换个关键词，或打开系统应用。") {}
        } else {
            visibleApps.forEach { app ->
                AppRow(
                    app = app,
                    checked = app.packageName in selectedPackages,
                    priority = packagePriorities[app.packageName] ?: Priority.NORMAL,
                    highlighted = app.packageName == highlightedPackage,
                    onCheckedChange = { onSetPackage(app.packageName, it) },
                    onPriorityChange = { onSetPriority(app.packageName, it) }
                )
            }
            if (visibleApps.size == 100) {
                Text(
                    "只显示前 100 个结果，搜索能更快定位。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsTab(
    barkServerUrl: String,
    barkDeviceKey: String,
    barkTitleTemplate: String,
    barkIconEnabled: Boolean,
    barkIconBaseUrl: String,
    barkIconUploadToken: String,
    lowLevel: BarkLevel,
    normalLevel: BarkLevel,
    urgentLevel: BarkLevel,
    pauseWhenInteractive: Boolean,
    blockedKeywords: Set<String>,
    onSetBarkServerUrl: (String) -> Unit,
    onSetBarkDeviceKey: (String) -> Unit,
    onSetTitleTemplate: (String) -> Unit,
    onSetIconEnabled: (Boolean) -> Unit,
    onSetIconBaseUrl: (String) -> Unit,
    onSetIconUploadToken: (String) -> Unit,
    onSetLowLevel: (BarkLevel) -> Unit,
    onSetNormalLevel: (BarkLevel) -> Unit,
    onSetUrgentLevel: (BarkLevel) -> Unit,
    onSetPauseWhenInteractive: (Boolean) -> Unit,
    onAddBlockedKeyword: (String) -> Unit,
    onRemoveBlockedKeyword: (String) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    var keywordDraft by rememberSaveable { mutableStateOf("") }
    Page(
        modifier = modifier,
        title = "设置",
        subtitle = "Bark、通知样式、保活权限"
    ) {
        SectionCard("Bark", "只保留 Bark 主链路，ntfy 已从界面移除。") {
            OutlinedTextField(
                value = barkServerUrl,
                onValueChange = onSetBarkServerUrl,
                label = { Text("服务器地址") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = barkDeviceKey,
                onValueChange = onSetBarkDeviceKey,
                label = { Text("Bark Key") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = barkTitleTemplate,
                onValueChange = onSetTitleTemplate,
                label = { Text("标题格式，例如 {app}-{title}") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("{app}", "{title}", "{package}").forEach { tag ->
                    FilterChip(
                        selected = false,
                        onClick = { onSetTitleTemplate(barkTitleTemplate + tag) },
                        label = { Text(tag) },
                        colors = glassChipColors()
                    )
                }
            }
        }

        SectionCard("默认提醒级别", "App 页里的单独分级优先；这里是全局映射。") {
            BarkLevelRow("低", lowLevel, onSetLowLevel)
            BarkLevelRow("普通", normalLevel, onSetNormalLevel)
            BarkLevelRow("紧急", urgentLevel, onSetUrgentLevel)
        }

        SectionCard("使用中策略", "减少你正在用 Android 时，iPhone 还重复响的打扰。") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("亮屏且未锁屏时暂停转发", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "锁屏亮屏仍会转发；跳过的通知会留在历史里，方便确认策略是否命中。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(checked = pauseWhenInteractive, onCheckedChange = onSetPauseWhenInteractive)
            }
        }

        SectionCard("关键词屏蔽", "命中标题或正文时只写入历史，不推送到 iPhone。") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = keywordDraft,
                    onValueChange = { keywordDraft = it },
                    label = { Text("例如：广告、验证码、群助手") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        onAddBlockedKeyword(keywordDraft)
                        keywordDraft = ""
                    },
                    enabled = keywordDraft.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
                ) {
                    Text("添加")
                }
            }
            if (blockedKeywords.isEmpty()) {
                Text(
                    "还没有屏蔽词。添加后，新通知命中会在历史里显示“已屏蔽”。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            } else {
                blockedKeywords.sorted().forEach { keyword ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.36f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(keyword, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        OutlinedButton(
                            onClick = { onRemoveBlockedKeyword(keyword) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("移除")
                        }
                    }
                }
            }
        }

        SectionCard("图标", "服务器没准备好时保持关闭，不影响推送。") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("启用 Bark 图标")
                Switch(checked = barkIconEnabled, onCheckedChange = onSetIconEnabled)
            }
            if (barkIconEnabled) {
                OutlinedTextField(
                    value = barkIconBaseUrl,
                    onValueChange = onSetIconBaseUrl,
                    label = { Text("图标基础地址") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = barkIconUploadToken,
                    onValueChange = onSetIconUploadToken,
                    label = { Text("图标上传 Token") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        SectionCard("Android 权限", "这部分决定后台是否能长期活着。") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenNotificationAccess,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
                ) {
                    Text("通知读取")
                }
                OutlinedButton(
                    onClick = onOpenAccessibility,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("无障碍保活")
                }
            }
            Text(
                "系统设置里还要开启自启动、省电无限制、后台锁定。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Page(
    modifier: Modifier,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        content()
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = GlassCard),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            content()
        }
    }
}

@Composable
private fun BarkLevelRow(
    label: String,
    selected: BarkLevel,
    onSelected: (BarkLevel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BarkLevel.entries.forEach { level ->
            FilterChip(
                selected = selected == level,
                onClick = { onSelected(level) },
                label = { Text(level.label) },
                colors = glassChipColors()
            )
        }
    }
    }
}

@Composable
private fun PriorityRow(
    selected: Priority,
    enabled: Boolean,
    onSelected: (Priority) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Priority.entries.forEach { priority ->
            FilterChip(
                enabled = enabled,
                selected = selected == priority,
                onClick = { onSelected(priority) },
                label = { Text(priority.shortLabel) },
                colors = glassChipColors()
            )
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    priority: Priority,
    highlighted: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onPriorityChange: (Priority) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (highlighted) {
                Color(0xFFFFF4D6).copy(alpha = 0.86f)
            } else if (checked) {
                Color.White.copy(alpha = 0.56f)
            } else {
                Color.White.copy(alpha = 0.34f)
            }
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (highlighted) {
                Text(
                    "从历史定位到此 App",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9A5B00)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(packageName = app.packageName, label = app.label)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            }
            PriorityRow(
                selected = priority,
                enabled = checked,
                onSelected = onPriorityChange
            )
        }
    }
}

@Composable
private fun AppIcon(packageName: String, label: String) {
    val context = LocalContext.current
    val image = remember(packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(packageName)
                .toBitmap(width = 80, height = 80)
                .asImageBitmap()
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.68f)),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Text(
                label.take(1).ifBlank { "A" },
                style = MaterialTheme.typography.titleMedium,
                color = AppleBlue
            )
        }
    }
}

@Composable
private fun rememberAppLabel(packageName: String): String {
    val context = LocalContext.current
    return remember(packageName) {
        val pm = context.packageManager
        runCatching {
            pm.getApplicationInfo(packageName, 0)
                .loadLabel(pm)
                .toString()
                .ifBlank { packageName }
        }.getOrElse { appLabelFallback(packageName) }
    }
}

@Composable
private fun NotificationLogRow(
    log: LogEntity,
    appName: String?,
    onDisableApp: (String) -> Unit,
    onSetPriority: (String, Priority) -> Unit,
    onOpenAppSettings: (String) -> Unit
) {
    val displayAppName = appName ?: rememberAppLabel(log.packageName)
    val formatter = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
    val isSkipped = log.error?.startsWith("已跳过") == true
    val isBlocked = log.error?.startsWith("已屏蔽") == true
    val status = when {
        log.forwarded -> "已转发"
        isBlocked -> "已屏蔽"
        isSkipped -> "已跳过"
        else -> "失败/待重试"
    }
    val statusColor = when {
        log.forwarded -> AppleGreen
        isBlocked -> Color(0xFF8E6A00)
        isSkipped -> Color(0xFFFF9500)
        else -> Color(0xFFFF3B30)
    }
    var expanded by rememberSaveable(log.id) { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable { expanded = !expanded },
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = GlassCard),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    AppIcon(packageName = log.packageName, label = displayAppName)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "$displayAppName · ${log.title.ifBlank { "无标题" }}",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            formatter.format(Date(log.postTime)),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                StatusBadge(status = status, priority = log.priority, color = statusColor)
            }
            Text(log.text.ifBlank { "无正文" }, style = MaterialTheme.typography.bodySmall)
            if (!log.error.isNullOrBlank()) {
                Text(
                    if (isSkipped || isBlocked) log.error else "错误：${log.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSkipped || isBlocked) Color(0xFF9A5B00) else Color(0xFFFF3B30)
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.035f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("包名: ${log.packageName}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("HTTP: ${log.httpCode ?: "-"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("规则: ${log.matchedRuleName ?: "App 默认分级"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("快捷设置", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onOpenAppSettings(log.packageName) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("去 App 设置")
                        }
                        OutlinedButton(
                            onClick = { onDisableApp(log.packageName) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("关闭此 App")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Priority.entries.forEach { priority ->
                            FilterChip(
                                selected = log.priority == priority,
                                onClick = { onSetPriority(log.packageName, priority) },
                                label = { Text(priority.label) },
                                colors = glassChipColors()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String, priority: Priority, color: Color) {
    Text(
        "$status · ${priority.label}",
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

private fun List<InstalledApp>.visibleApps(
    query: String,
    showSystemApps: Boolean,
    selectedPackages: Set<String>
): List<InstalledApp> =
    asSequence()
        .filter { showSystemApps || !it.isSystem || it.packageName in selectedPackages }
        .filter {
            query.isBlank() ||
                it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
        .take(100)
        .toList()

private val Priority.label: String
    get() = when (this) {
        Priority.LOW -> "低"
        Priority.NORMAL -> "普通"
        Priority.URGENT -> "紧急"
    }

private val Priority.shortLabel: String
    get() = when (this) {
        Priority.LOW -> "低"
        Priority.NORMAL -> "普"
        Priority.URGENT -> "紧"
    }

@Composable
private fun glassChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Color.White,
    selectedLabelColor = TextPrimary,
    containerColor = Color.Black.copy(alpha = 0.045f),
    labelColor = TextSecondary
)

private fun appLabelFallback(pkg: String): String = when (pkg) {
    "com.tencent.mm" -> "微信"
    "com.tencent.wework" -> "企业微信"
    else -> pkg
}

@Composable
private fun fastNavColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = AppleBlue,
    selectedTextColor = AppleBlue,
    indicatorColor = Color.Transparent,
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary
)
