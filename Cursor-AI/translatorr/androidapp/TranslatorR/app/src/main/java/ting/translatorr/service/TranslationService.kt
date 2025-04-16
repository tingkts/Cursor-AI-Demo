package ting.translatorr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import ting.translatorr.MainActivity
import ting.translatorr.R
import java.util.Locale

class TranslationService : Service(), TextToSpeech.OnInitListener {
    companion object {
        private const val TAG = "TranslationService"
        private const val NOTIFICATION_ID = 100  // 使用更明確的ID
        private const val CHANNEL_ID = "translation_service_channel"

        // Action constants for notification controls
        const val ACTION_PLAY = "ting.translatorr.action.PLAY"
        const val ACTION_STOP = "ting.translatorr.action.STOP"
        const val ACTION_EXIT = "ting.translatorr.action.EXIT"
        const val EXTRA_TEXT = "ting.translatorr.extra.TEXT"
        const val ACTION_SHOW_NOTIFICATION = "ting.translatorr.action.SHOW_NOTIFICATION"
        const val ACTION_HIDE_NOTIFICATION = "ting.translatorr.action.HIDE_NOTIFICATION"
    }

    private lateinit var textToSpeech: TextToSpeech
    private var isPlaying = false
    private var currentPlayCount = 0
    private var currentText = ""
    private val binder = TranslationBinder()
    private lateinit var notificationManager: NotificationManager
    private var shouldShowNotification = false

    inner class TranslationBinder : Binder() {
        fun getService(): TranslationService = this@TranslationService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Initializing service")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 檢查通知通道是否存在
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (channel == null) {
                Log.w(TAG, "Notification channel not found, creating it in service")
                createNotificationChannel()
            } else {
                Log.d(TAG, "Notification channel already exists with importance: ${channel.importance}")
            }
        }

        textToSpeech = TextToSpeech(this, this)
    }

    private fun showForegroundNotification() {
        if (!shouldShowNotification) {
            Log.d(TAG, "showForegroundNotification: Skipping notification as shouldShowNotification is false")
            return
        }

        Log.d(TAG, "showForegroundNotification: Creating foreground notification")
        try {
            val notification = createNotification()

            // 使用不同的方法啟動前台服務，根據API版本
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+
                Log.d(TAG, "Using startForeground with API 34+ method")
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10-13
                Log.d(TAG, "Using startForeground with API 29+ method")
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                // Android 9及以下
                Log.d(TAG, "Using startForeground with legacy method")
                startForeground(NOTIFICATION_ID, notification)
            }

            // 確保通知被顯示
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification displayed with ID: $NOTIFICATION_ID")
        } catch (e: Exception) {
            Log.e(TAG, "showForegroundNotification: Error starting foreground service", e)
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Action: ${intent?.action}, HasExtras: ${intent?.extras != null}")

        when (intent?.action) {
            ACTION_PLAY -> {
                val text = intent.getStringExtra(EXTRA_TEXT)
                Log.d(TAG, "onStartCommand: ACTION_PLAY with text: ${text?.take(20)}...")
                if (!text.isNullOrBlank()) {
                    currentText = text
                    startSpeaking(text)
                }

                // 只有在應該顯示通知的情況下才顯示
                if (shouldShowNotification) {
                    showForegroundNotification()
                } else {
                    // 如果處於前台狀態但需要保持服務運行
                    runAsForegroundWithoutNotification()
                }
            }
            ACTION_STOP -> {
                Log.d(TAG, "onStartCommand: ACTION_STOP")
                stopSpeaking()

                // 更新通知但不隱藏
                if (shouldShowNotification) {
                    showForegroundNotification()
                } else {
                    runAsForegroundWithoutNotification()
                }
            }
            ACTION_EXIT -> {
                Log.d(TAG, "onStartCommand: ACTION_EXIT")
                stopSpeaking()
                shouldShowNotification = false
                stopForeground(true)
                stopSelf()
            }
            ACTION_SHOW_NOTIFICATION -> {
                Log.d(TAG, "onStartCommand: ACTION_SHOW_NOTIFICATION")
                shouldShowNotification = true
                showForegroundNotification()
            }
            ACTION_HIDE_NOTIFICATION -> {
                Log.d(TAG, "onStartCommand: ACTION_HIDE_NOTIFICATION")
                shouldShowNotification = false
                stopForeground(true)
            }
            else -> {
                // 如果沒有指定動作但有文本，則播放
                val text = intent?.getStringExtra(EXTRA_TEXT)
                Log.d(TAG, "onStartCommand: No action specified, text: ${text?.take(20)}...")
                if (!text.isNullOrBlank()) {
                    currentText = text
                    startSpeaking(text)

                    // 只有在應該顯示通知的情況下才顯示
                    if (shouldShowNotification) {
                        showForegroundNotification()
                    } else {
                        runAsForegroundWithoutNotification()
                    }
                }
            }
        }

        // 返回START_STICKY，確保服務在被殺死後會重新啟動
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind: Service bound")
        return binder
    }

    override fun onInit(status: Int) {
        Log.d(TAG, "onInit: TextToSpeech status: $status")
        if (status == TextToSpeech.SUCCESS) {
            // Set US English as the language
            val result = textToSpeech.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Language not supported
                Log.w(TAG, "onInit: Language not supported")
            }

            // Set utterance progress listener
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "onStart: Started utterance: $utteranceId")
                    isPlaying = true
                    updateNotification()
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "onDone: Completed utterance: $utteranceId")
                    // Once done, increment play count and speak again if still playing
                    if (utteranceId?.startsWith("play_") == true) {
                        currentPlayCount++

                        // Continue looping playback
                        if (isPlaying) {
                            val text = utteranceId.substring(5) // Remove "play_" prefix
                            speakText(text, "play_$text")
                        }
                        updateNotification()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "onError: Error in utterance: $utteranceId")
                    isPlaying = false
                    updateNotification()
                }
            })
        } else {
            Log.e(TAG, "onInit: Failed to initialize TextToSpeech")
        }
    }

    private fun startSpeaking(text: String) {
        Log.d(TAG, "startSpeaking: Starting to speak: ${text.take(20)}...")
        if (text.isNotBlank()) {
            isPlaying = true
            // Reset play count when starting a new text
            if (text != currentText) {
                currentPlayCount = 0
            }
            speakText(text, "play_$text")
            updateNotification()
        }
    }

    private fun stopSpeaking() {
        Log.d(TAG, "stopSpeaking: Stopping speech")
        if (isPlaying) {
            isPlaying = false
            textToSpeech.stop()
            updateNotification()
        }
    }

    private fun speakText(text: String, utteranceId: String) {
        Log.d(TAG, "speakText: Speaking with utteranceId: $utteranceId")
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "createNotificationChannel: Creating notification channel")
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Translation Service Channel",
                NotificationManager.IMPORTANCE_HIGH // 提高重要性以確保顯示
            ).apply {
                description = "Channel for Translation Service"
                setShowBadge(true) // 顯示角標
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC // 在鎖屏上顯示通知
                enableLights(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            Log.d(TAG, "createNotificationChannel: Notification channel created with importance HIGH")
        }
    }

    private fun createNotification(): Notification {
        Log.d(TAG, "createNotification: Creating notification with playing=$isPlaying")

        // 創建右上角的播放次數子標題
        val playCountSubText = "播放次數: $currentPlayCount"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (isPlaying) getString(R.string.playing_translation) else getString(R.string.paused_translation))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX) // 使用最高優先級確保顯示
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 在鎖屏上顯示
            .setCategory(NotificationCompat.CATEGORY_SERVICE) // 指定為服務類通知
            .setSubText(playCountSubText) // 在右上角顯示播放次數
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) getString(R.string.stop_button) else getString(R.string.play_button),
                createPendingIntent(if (isPlaying) ACTION_STOP else ACTION_PLAY)
            )
            .addAction(
                R.drawable.ic_close,
                getString(R.string.exit_app),
                createPendingIntent(ACTION_EXIT)
            )
            .setContentIntent(createContentIntent())

        // 確保通知不會被滑動刪除
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    private fun updateNotification() {
        Log.d(TAG, "updateNotification: Updating notification")
        if (shouldShowNotification) {
            val notification = createNotification()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TranslationService::class.java).apply {
            this.action = action
            if (action == ACTION_PLAY) {
                putExtra(EXTRA_TEXT, currentText)
            }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        return PendingIntent.getService(
            this,
            when (action) {
                ACTION_PLAY -> 0
                ACTION_STOP -> 1
                ACTION_EXIT -> 2
                else -> 3
            },
            intent,
            flags
        )
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        return PendingIntent.getActivity(
            this,
            0,
            intent,
            flags
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved: App removed from recent tasks")
        // 確保在用戶移除應用程序時服務仍然運行
        if (isPlaying && currentText.isNotBlank()) {
            // 切換通知狀態為顯示
            shouldShowNotification = true
            // 再次顯示通知，確保可見
            showForegroundNotification()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Service being destroyed")
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }

    // 添加一個方法，在前台模式下運行但不顯示通知
    private fun runAsForegroundWithoutNotification() {
        try {
            // 創建一個透明通知 - 這是合法的，但通知不會顯示給用戶
            val emptyNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("")
                .setContentText("")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build()

            // 以較低的服務類型啟動前台服務
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, emptyNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE)
            } else {
                startForeground(NOTIFICATION_ID, emptyNotification)
            }
            Log.d(TAG, "Running as foreground service without visible notification")
        } catch (e: Exception) {
            Log.e(TAG, "Error running as foreground without notification", e)
        }
    }
}