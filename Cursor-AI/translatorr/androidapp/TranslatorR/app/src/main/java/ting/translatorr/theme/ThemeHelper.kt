package ting.translatorr.theme

import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors

/**
 * 主题辅助类，用于应用动态颜色和处理兼容性
 */
object ThemeHelper {

    /**
     * 应用动态颜色到整个应用
     * 在Android 12+设备上使用系统提供的动态颜色
     * 在旧版本上使用默认颜色
     */
    fun applyDynamicColors(application: Application) {
        // 在Android 12+上启用动态颜色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(application)
        }
    }

    /**
     * 根据系统设置切换深色模式
     */
    fun applySystemTheme() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
}