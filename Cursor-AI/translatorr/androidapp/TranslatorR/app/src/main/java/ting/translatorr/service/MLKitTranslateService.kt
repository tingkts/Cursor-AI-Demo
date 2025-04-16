package ting.translatorr.service

import android.content.Context
import android.util.Log
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * MLKit 翻譯服務類
 * 使用 Google MLKit 的離線翻譯功能
 *
 * @property context 應用程序上下文
 */
class MLKitTranslateService(private val context: Context) {
    companion object {
        private const val TAG = "MLKitTranslateService"
    }

    // 儲存已創建的翻譯器實例，避免重複創建
    private val translators = mutableMapOf<String, com.google.mlkit.nl.translate.Translator>()

    /**
     * 翻譯文本
     *
     * @param text 要翻譯的文本
     * @param sourceLanguage 源語言代碼
     * @param targetLanguage 目標語言代碼
     * @return 翻譯後的文本
     * @throws IOException 如果下載模型失敗
     */
    suspend fun translate(text: String, sourceLanguage: String, targetLanguage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // 將語言代碼轉換為 MLKit 格式
                val mlKitSourceLanguage = convertToMLKitLanguageCode(sourceLanguage)
                val mlKitTargetLanguage = convertToMLKitLanguageCode(targetLanguage)

                // 獲取或創建翻譯器
                val translator = getTranslator(mlKitSourceLanguage, mlKitTargetLanguage)

                // 確保翻譯模型已下載
                try {
                    translator.downloadModelIfNeeded().await()
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading translation model", e)
                    throw IOException("Failed to download translation model: ${e.message}")
                }

                // 執行翻譯
                val result = translator.translate(text).await()
                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "Translation error", e)
                throw e
            }
        }
    }

    /**
     * 獲取或創建翻譯器實例
     *
     * @param sourceLanguage 源語言代碼
     * @param targetLanguage 目標語言代碼
     * @return 翻譯器實例
     */
    private fun getTranslator(sourceLanguage: String, targetLanguage: String): com.google.mlkit.nl.translate.Translator {
        val key = "$sourceLanguage-$targetLanguage"
        return translators.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build()
            Translation.getClient(options)
        }
    }

    /**
     * 將標準語言代碼轉換為 MLKit 支持的語言代碼
     *
     * @param languageCode 標準語言代碼
     * @return MLKit 支持的語言代碼
     */
    private fun convertToMLKitLanguageCode(languageCode: String): String {
        return when (languageCode) {
            "zh-CN" -> TranslateLanguage.CHINESE
            "en" -> TranslateLanguage.ENGLISH
            else -> languageCode
        }
    }

    /**
     * 關閉所有翻譯器實例並清理資源
     */
    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}