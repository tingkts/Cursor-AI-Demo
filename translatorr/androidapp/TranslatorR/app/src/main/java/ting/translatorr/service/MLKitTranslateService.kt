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

class MLKitTranslateService(private val context: Context) {
    companion object {
        private const val TAG = "MLKitTranslateService"
    }

    private val translators = mutableMapOf<String, com.google.mlkit.nl.translate.Translator>()

    suspend fun translate(text: String, sourceLanguage: String, targetLanguage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Convert language codes to MLKit format
                val mlKitSourceLanguage = convertToMLKitLanguageCode(sourceLanguage)
                val mlKitTargetLanguage = convertToMLKitLanguageCode(targetLanguage)

                // Get or create translator
                val translator = getTranslator(mlKitSourceLanguage, mlKitTargetLanguage)

                // Ensure model is downloaded
                try {
                    translator.downloadModelIfNeeded().await()
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading translation model", e)
                    throw IOException("Failed to download translation model: ${e.message}")
                }

                // Translate the text
                val result = translator.translate(text).await()
                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "Translation error", e)
                throw e
            }
        }
    }

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

    private fun convertToMLKitLanguageCode(languageCode: String): String {
        return when (languageCode) {
            "zh-CN" -> TranslateLanguage.CHINESE
            "en" -> TranslateLanguage.ENGLISH
            else -> languageCode
        }
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}