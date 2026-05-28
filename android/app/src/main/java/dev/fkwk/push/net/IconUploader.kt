package dev.fkwk.push.net

import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.fkwk.push.data.NtfySettings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IconUploader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val png = "image/png".toMediaType()

    fun uploadIfConfigured(settings: NtfySettings, packageName: String) {
        val base = settings.barkIconBaseUrl.trim().trimEnd('/')
        val token = settings.barkIconUploadToken.trim()
        if (base.isBlank() || token.isBlank()) return

        runCatching {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val bitmap = drawable.toBitmap(width = 256, height = 256)
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            val req = Request.Builder()
                .url("$base/$packageName.png")
                .put(bytes.toRequestBody(png))
                .header("Authorization", "Bearer $token")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Timber.w("上传应用图标失败: %s HTTP %d", packageName, resp.code)
                }
            }
        }.onFailure {
            Timber.w(it, "上传应用图标失败: %s", packageName)
        }
    }
}
