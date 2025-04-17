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

class GoogleTranslateService {
    companion object {
        private const val TAG = "GoogleTranslateService"
        private const val API_URL = "https://translation.googleapis.com/language/translate/v2"
    }

    private val client = OkHttpClient()
    private var apiKey: String = ""

    fun initialize(context: Context) {
        apiKey = context.getString(R.string.google_translate_api_key)
    }

    suspend fun translate(text: String, sourceLanguage: String, targetLanguage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val formBody = FormBody.Builder()
                    .add("q", text)
                    .add("source", sourceLanguage)
                    .add("target", targetLanguage)
                    .add("format", "text")
                    .build()

                val request = Request.Builder()
                    .url("$API_URL?key=$apiKey")
                    .post(formBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Request failed: ${response.code}")
                    }

                    val responseBody = response.body?.string() ?: throw IOException("Empty response")
                    val jsonResponse = JSONObject(responseBody)

                    // Parse the response
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
                Log.e(TAG, "Translation error", e)
                throw e
            }
        }
    }
}