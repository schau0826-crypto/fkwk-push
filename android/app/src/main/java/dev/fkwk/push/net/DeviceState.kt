package dev.fkwk.push.net

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceState @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isActivelyUsingDevice(): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        return powerManager?.isInteractive == true && keyguardManager?.isKeyguardLocked != true
    }
}
