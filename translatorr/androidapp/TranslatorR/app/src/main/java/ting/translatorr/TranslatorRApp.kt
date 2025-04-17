package ting.translatorr

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import ting.translatorr.service.TranslationService
import ting.translatorr.theme.ThemeHelper

/**
 * 应用程序类，负责初始化应用级组件
 */
class TranslatorRApp : Application() {
    companion object {
        private const val TAG = "TranslatorRApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate")

        // 应用动态颜色和系统主题设置
        ThemeHelper.applyDynamicColors(this)
        ThemeHelper.applySystemTheme()

        // 確保通知通道被創建
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        Log.d(TAG, "Creating notification channel")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "translation_service_channel"
            val channelName = "Translation Service Channel"
            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Channel for Translation Service"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 先刪除舊的通道（如果存在）
            try {
                notificationManager.deleteNotificationChannel(channelId)
                Log.d(TAG, "Deleted existing notification channel")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting notification channel", e)
            }

            // 創建新的通道
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Created notification channel with importance: $importance")
        } else {
            Log.d(TAG, "Skipping notification channel creation - not needed for API < 26")
        }
    }
}