package ting.translatorr

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

class MainActivity : AppCompatActivity(), OnInitListener {
    private lateinit var inputText: EditText
    private lateinit var translateButton: MaterialButton
    private lateinit var resultText: TextView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var progressBar: ProgressBar
    private lateinit var repeatSeekBar: SeekBar
    private lateinit var repeatCountText: TextView
    private lateinit var playCountText: TextView
    private lateinit var englishChineseTranslator: Translator
    private lateinit var textToSpeech: TextToSpeech
    private var isPlaying = false
    private var currentPlayCount = 0
    private var isTranslated = false
    private var lastTranslatedText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        inputText = findViewById(R.id.inputText)
        translateButton = findViewById(R.id.translateButton)
        resultText = findViewById(R.id.resultText)
        toolbar = findViewById(R.id.toolbar)
        progressBar = findViewById(R.id.progressBar)
        repeatSeekBar = findViewById(R.id.repeatSeekBar)
        repeatCountText = findViewById(R.id.repeatCountText)
        playCountText = findViewById(R.id.playCountText)

        // 隱藏重複次數控制
        repeatSeekBar.visibility = View.GONE
        repeatCountText.visibility = View.GONE
        findViewById<TextView>(R.id.repeatLabel).visibility = View.GONE

        // 初始化已播放次數
        currentPlayCount = 0
        updatePlayCountText()

        // 初始化文字轉語音引擎
        textToSpeech = TextToSpeech(this, this)

        // Setup toolbar title
        toolbar.title = getString(R.string.app_name)

        // Create an English-Chinese translator
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.CHINESE)
            .build()
        englishChineseTranslator = Translation.getClient(options)

        // Download the translation model if needed
        progressBar.visibility = View.VISIBLE
        englishChineseTranslator.downloadModelIfNeeded()
            .addOnSuccessListener {
                // Model downloaded successfully, the translator can be used
                progressBar.visibility = View.GONE
                Toast.makeText(this, getString(R.string.model_ready), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                // Model download failed
                progressBar.visibility = View.GONE
                Toast.makeText(this, getString(R.string.model_failed, exception.message), Toast.LENGTH_SHORT).show()
            }

        // 設置翻譯按鈕為切換按鈕
        translateButton.setOnClickListener {
            val text = inputText.text.toString()

            if (text.isNotBlank()) {
                if (!isTranslated) {
                    // 尚未翻譯，進行翻譯
                    resultText.text = getString(R.string.translating)
                    progressBar.visibility = View.VISIBLE

                    // Perform the translation
                    englishChineseTranslator.translate(text)
                        .addOnSuccessListener { translatedText ->
                            // Translation successful
                            progressBar.visibility = View.GONE
                            resultText.text = translatedText
                            isTranslated = true
                            lastTranslatedText = text

                            // 翻譯成功後開始播放原始英文文本
                            toggleSpeaking(text)
                            // 更新按鈕文字
                            updateButtonText()
                        }
                        .addOnFailureListener { exception ->
                            // Error during translation
                            progressBar.visibility = View.GONE
                            resultText.text = getString(R.string.translation_failed, exception.message)
                        }
                } else {
                    // 已翻譯，切換播放/停止
                    toggleSpeaking(lastTranslatedText)
                    // 更新按鈕文字
                    updateButtonText()
                }
            } else {
                Toast.makeText(this, getString(R.string.enter_text), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 更新按鈕文字
    private fun updateButtonText() {
        translateButton.text = if (!isTranslated) {
            getString(R.string.translate_button)
        } else if (isPlaying) {
            getString(R.string.stop_button)
        } else {
            getString(R.string.play_button)
        }
    }

    // 實現TextToSpeech.OnInitListener接口
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // 設置語言為英文
            val result = textToSpeech.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, getString(R.string.tts_not_supported), Toast.LENGTH_SHORT).show()
            }

            // 設置語音播放的監聽器
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isPlaying = true
                    runOnUiThread {
                        updateButtonText()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    // 播放完成一次，計數增加
                    if (utteranceId?.startsWith("play_") == true) {
                        currentPlayCount++
                        runOnUiThread {
                            updatePlayCountText()
                        }

                        // 繼續循環播放
                        if (isPlaying) {
                            val text = utteranceId.substring(5) // 去掉 "play_" 前綴
                            runOnUiThread {
                                speakText(text, "play_$text")
                            }
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    isPlaying = false
                    runOnUiThread {
                        updateButtonText()
                    }
                }
            })
        } else {
            Toast.makeText(this, getString(R.string.tts_init_failed), Toast.LENGTH_SHORT).show()
        }
    }

    // 播放/停止切換
    private fun toggleSpeaking(text: String) {
        if (isPlaying) {
            // 當前正在播放，停止播放
            stopSpeaking()
        } else {
            // 當前未播放，開始播放
            startSpeaking(text)
        }
    }

    // 開始播放
    private fun startSpeaking(text: String) {
        if (text.isNotBlank()) {
            isPlaying = true
            speakText(text, "play_$text")
            updateButtonText()
        }
    }

    // 停止播放
    private fun stopSpeaking() {
        if (isPlaying) {
            isPlaying = false
            textToSpeech.stop()
            updateButtonText()
        }
    }

    // 播放文本
    private fun speakText(text: String, utteranceId: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    // 更新播放次數顯示
    private fun updatePlayCountText() {
        playCountText.text = currentPlayCount.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Close the translator when the activity is destroyed
        if (::englishChineseTranslator.isInitialized) {
            englishChineseTranslator.close()
        }
        // 關閉TTS引擎
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }
}