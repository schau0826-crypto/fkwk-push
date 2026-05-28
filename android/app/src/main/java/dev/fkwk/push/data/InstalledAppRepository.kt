package dev.fkwk.push.data

import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

@Singleton
class InstalledAppRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun loadInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.getInstalledApplications(0)
            .map { app ->
                InstalledApp(
                    packageName = app.packageName,
                    label = app.loadLabel(pm).toString().ifBlank { app.packageName },
                    isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
                )
            }
            .sortedWith(compareBy<InstalledApp> { it.isSystem }.thenBy { it.label.lowercase() })
    }
}
