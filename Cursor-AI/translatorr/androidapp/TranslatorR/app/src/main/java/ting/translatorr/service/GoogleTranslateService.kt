package ting.translatorr.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import ting.translatorr.R
import java.io.IOException

/**
 * Google Cloud Translation API 服務類
 * 使用 Google Cloud Translation API 進行在線翻譯
 */
class GoogleTranslateService {
    companion object {
        // 用於日誌記錄的標籤
        private const val TAG = "GoogleTranslateService"
        // Google Cloud Translation API 的端點URL
        private const val API_URL = "https://translation.googleapis.com/language/translate/v2"
    }

    // HTTP 客戶端實例，用於發送API請求
    private val client = OkHttpClient()
    // Google Cloud API 密鑰
    private var apiKey: String = ""

    /**
     * 初始化服務
     * 從資源文件中獲取 Google Cloud API 密鑰
     *
     * @param context 應用程序上下文
     */
    fun initialize(context: Context) {
        apiKey = context.getString(R.string.google_translate_api_key)
    }

    /**
     * 翻譯文本
     *
     * @param text 要翻譯的文本
     * @param sourceLanguage 源語言代碼
     * @param targetLanguage 目標語言代碼
     * @return 翻譯後的文本
     * @throws IOException 如果API請求失敗
     */
    suspend fun translate(text: String, sourceLanguage: String, targetLanguage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // 構建請求體，包含翻譯所需參數
                val formBody = FormBody.Builder()
                    .add("q", text)
                    .add("source", sourceLanguage)
                    .add("target", targetLanguage)
                    .add("format", "text")
                    .build()

                // 構建HTTP請求
                val request = Request.Builder()
                    .url("$API_URL?key=$apiKey")
                    .post(formBody)
                    .build()

                // 執行請求並處理響應
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Request failed: ${response.code}")
                    }

                    // 解析響應體
                    val responseBody = response.body?.string() ?: throw IOException("Empty response")
                    val jsonResponse = JSONObject(responseBody)

                    // 從JSON響應中提取翻譯結果
                    val translationsArray = jsonResponse
                        .getJSONObject("data")
                        .getJSONArray("translations")

                    if (translationsArray.length() > 0) {
                        val translatedText = translationsArray
                            .getJSONObject(0)
                            .getString("translatedText")
                        return@withContext translatedText
                    } else {
                        throw IOException("No translation returned")
                    }
                }
            } catch (e: Exception) {
                // 記錄錯誤並重新拋出異常
                Log.e(TAG, "Translation error", e)
                throw e
            }
        }
    }
}