package dev.fkwk.push.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fkwk.push.data.InstalledApp
import dev.fkwk.push.data.InstalledAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fkwk.push.data.BarkLevel
import dev.fkwk.push.data.LogDao
import dev.fkwk.push.data.NtfySettings
import dev.fkwk.push.data.SettingsRepository
import dev.fkwk.push.domain.Priority
import dev.fkwk.push.net.IconUploader
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: SettingsRepository,
    logDao: LogDao,
    private val installedAppRepository: InstalledAppRepository,
    private val iconUploader: IconUploader
) : ViewModel() {

    val settings: StateFlow<NtfySettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NtfySettings())

    val recentLogs = logDao.observeRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedApps = kotlinx.coroutines.flow.MutableStateFlow<List<InstalledApp>>(emptyList())

    // 本地编辑缓存：简单起见直接写回 DataStore（小项目够用）
    fun setBarkServerUrl(v: String) = launchUpdate { it.copy(barkServerUrl = v.trim()) }
    fun setBarkDeviceKey(v: String) = launchUpdate { it.copy(barkDeviceKey = v.trim()) }
    fun setBarkTitleTemplate(v: String) = launchUpdate { it.copy(barkTitleTemplate = v) }
    fun setBarkIconEnabled(v: Boolean) = launchUpdate { it.copy(barkIconEnabled = v) }
    fun setBarkIconBaseUrl(v: String) = launchUpdate { it.copy(barkIconBaseUrl = v.trim()) }
    fun setBarkIconUploadToken(v: String) = launchUpdate { it.copy(barkIconUploadToken = v.trim()) }
    fun setBarkLowLevel(v: BarkLevel) = launchUpdate { it.copy(barkLowLevel = v) }
    fun setBarkNormalLevel(v: BarkLevel) = launchUpdate { it.copy(barkNormalLevel = v) }
    fun setBarkUrgentLevel(v: BarkLevel) = launchUpdate { it.copy(barkUrgentLevel = v) }
    fun setPauseWhenInteractive(v: Boolean) = launchUpdate { it.copy(pauseWhenInteractive = v) }
    fun addBlockedKeyword(v: String) = launchUpdate {
        val keyword = v.trim()
        if (keyword.isBlank()) it else it.copy(blockedKeywords = it.blockedKeywords + keyword)
    }
    fun removeBlockedKeyword(v: String) = launchUpdate {
        it.copy(blockedKeywords = it.blockedKeywords - v)
    }
    fun addPackage(v: String) = launchUpdate {
        val pkg = v.trim()
        if (pkg.isBlank()) it else it.copy(monitoredPackages = it.monitoredPackages + pkg)
    }
    fun removePackage(v: String) = launchUpdate {
        it.copy(monitoredPackages = it.monitoredPackages - v)
    }
    fun setPackagesEnabled(packageNames: Collection<String>, enabled: Boolean) {
        val packages = packageNames.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (packages.isEmpty()) return
        viewModelScope.launch {
            repo.update {
                it.copy(
                    monitoredPackages = if (enabled) {
                        it.monitoredPackages + packages
                    } else {
                        it.monitoredPackages - packages
                    },
                    packagePriorities = if (enabled) {
                        it.packagePriorities + packages.associateWith { pkg ->
                            it.packagePriorities[pkg] ?: Priority.NORMAL
                        }
                    } else {
                        it.packagePriorities - packages
                    }
                )
            }
            val settings = repo.current()
            if (enabled && settings.barkIconEnabled) {
                packages.forEach { iconUploader.uploadIfConfigured(settings, it) }
            }
        }
    }
    fun setPackageEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            repo.update {
                it.copy(
                    monitoredPackages = if (enabled) {
                        it.monitoredPackages + packageName
                    } else {
                        it.monitoredPackages - packageName
                    },
                    packagePriorities = if (enabled) {
                        it.packagePriorities + (packageName to (it.packagePriorities[packageName] ?: Priority.NORMAL))
                    } else {
                        it.packagePriorities - packageName
                    }
                )
            }
            if (enabled && repo.current().barkIconEnabled) {
                iconUploader.uploadIfConfigured(repo.current(), packageName)
            }
        }
    }

    fun setPackagePriority(packageName: String, priority: Priority) = launchUpdate {
        it.copy(
            monitoredPackages = it.monitoredPackages + packageName,
            packagePriorities = it.packagePriorities + (packageName to priority)
        )
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            installedApps.value = installedAppRepository.loadInstalledApps()
        }
    }

    fun save() { /* 字段已实时写入，这里保留按钮语义，可加校验/提示 */ }

    private fun launchUpdate(transform: (NtfySettings) -> NtfySettings) {
        viewModelScope.launch { repo.update(transform) }
    }
}
