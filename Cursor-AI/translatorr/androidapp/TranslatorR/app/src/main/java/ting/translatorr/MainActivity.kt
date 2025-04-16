/**
 * 主要活動類別
 * 提供翻譯和文字轉語音功能的主要界面
 */
package ting.translatorr

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import ting.translatorr.service.GoogleTranslateService
import ting.translatorr.service.MLKitTranslateService
import ting.translatorr.service.TranslationService
import java.util.Locale
import java.util.regex.Pattern
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import android.net.Uri
import android.provider.MediaStore
import android.content.ClipboardManager

/**
 * 主要活動類別，實現OnInitListener介面以處理TTS初始化
 */
class MainActivity : AppCompatActivity(), OnInitListener {

    companion object {
        // 日誌標籤
        private const val TAG = "MainActivity"
        // 相機請求碼
        private const val REQUEST_CAMERA_TEXT = 100
        // Google翻譯請求碼
        private const val REQUEST_GOOGLE_TRANSLATE = 101
        // SharedPreferences名稱和鍵值
        private const val PREFS_NAME = "TranslatorRPrefs"
        private const val KEY_EDITOR_CONTENT = "editor_content"
        private const val KEY_RESULT_CONTENT = "result_content"
        private const val KEY_IS_TRANSLATED = "is_translated"
        private const val KEY_TRANSLATION_ENGINE = "translation_engine"

        // 翻譯引擎類型常量
        const val TRANSLATION_ENGINE_ML_KIT = 0
        const val TRANSLATION_ENGINE_GOOGLE_API = 1
    }

    // UI元件
    private lateinit var inputText: EditText
    private lateinit var translateButton: MaterialButton
    private lateinit var playStopButton: MaterialButton
    private lateinit var cameraButton: MaterialButton
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var playCountBadge: TextView
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var toolbar: MaterialToolbar
    private lateinit var translationEngineSpinner: Spinner

    // 翻譯服務實例
    private val googleTranslateService = GoogleTranslateService()
    private lateinit var mlKitTranslateService: MLKitTranslateService
    private var selectedTranslationEngine = TRANSLATION_ENGINE_ML_KIT

    // 狀態標誌
    private var isTranslated = false
    private var lastTranslatedText = ""
    private var translatedEnglishText = ""
    private var isPlaying = false
    private var currentPlayCount = 0

    // 服務相關
    private var isServiceBound = false
    private var translationService: TranslationService? = null

    /**
     * 服務連接回調
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TranslationService.TranslationBinder
            translationService = binder.getService()
            isServiceBound = true
            Log.d(TAG, "服務已連接")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            translationService = null
            isServiceBound = false
            Log.d(TAG, "服務已斷開")
        }
    }

    /**
     * 權限請求回調
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "權限已授予")
        } else {
            Log.d(TAG, "權限被拒絕")
        }
    }

    /**
     * 相機權限請求回調
     */
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startGoogleTranslateCamera()
        } else {
            Toast.makeText(
                this,
                "無法使用相機功能，因為相機權限被拒絕",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Google翻譯活動結果回調
     */
    private val googleTranslateActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Google Lens返回: resultCode=${result.resultCode}")

        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
                // 嘗試從所有可能的Extra鍵獲取文本
                var recognizedText: String? = null

                // 遍歷所有Extra并記錄
                data.extras?.keySet()?.forEach { key ->
                    val value = data.extras?.get(key)
                    Log.d(TAG, "Lens返回 Extra: $key = $value")

                    // 檢查是否為字符串類型，如果是，可能包含識別的文本
                    if (value is String && value.isNotEmpty() && recognizedText.isNullOrEmpty()) {
                        recognizedText = value
                        Log.d(TAG, "可能的識別文本來源: $key")
                    }
                }

                // 檢查常見的Extra鍵
                if (recognizedText.isNullOrEmpty()) {
                    recognizedText = data.getStringExtra(Intent.EXTRA_TEXT)
                    Log.d(TAG, "EXTRA_TEXT: $recognizedText")
                }

                if (recognizedText.isNullOrEmpty()) {
                    recognizedText = data.getStringExtra("recognized_text")
                    Log.d(TAG, "recognized_text: $recognizedText")
                }

                if (recognizedText.isNullOrEmpty()) {
                    recognizedText = data.getStringExtra("ocr_result")
                    Log.d(TAG, "ocr_result: $recognizedText")
                }

                // 如果找到文本，設置到輸入框
                if (!recognizedText.isNullOrEmpty()) {
                    inputText.setText(recognizedText)
                    Toast.makeText(
                        this,
                        getString(R.string.text_recognized),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // 可能需要從剪貼板獲取文本
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = clipboard.primaryClip
                    if (clipData != null && clipData.itemCount > 0) {
                        val clipText = clipData.getItemAt(0).text.toString()
                        if (clipText.isNotEmpty()) {
                            inputText.setText(clipText)
                            Toast.makeText(
                                this,
                                getString(R.string.text_from_clipboard),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                getString(R.string.no_text_recognized),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.no_text_recognized),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * 存儲權限請求回調
     */
    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "存儲權限已授予")
        } else {
            Log.d(TAG, "存儲權限被拒絕")
            Toast.makeText(
                this,
                "無法保存翻譯到文件，因為存儲權限被拒絕",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Activity創建時的回調
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 設置窗口不與狀態欄重疊
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }

        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        requestNotificationPermission()
        requestStoragePermission()

        // 初始化UI元件
        inputText = findViewById(R.id.inputText)
        translateButton = findViewById(R.id.translateButton)
        playStopButton = findViewById(R.id.playStopButton)
        cameraButton = findViewById(R.id.cameraButton)
        resultText = findViewById(R.id.resultText)
        progressBar = findViewById(R.id.progressBar)
        playCountBadge = findViewById(R.id.playCountBadge)
        toolbar = findViewById(R.id.toolbar)
        translationEngineSpinner = findViewById(R.id.translationEngineSpinner)

        // 設置工具欄
        toolbar.title = getString(R.string.app_name)
        setSupportActionBar(toolbar)

        // 初始化翻譯服務
        googleTranslateService.initialize(this)
        mlKitTranslateService = MLKitTranslateService(this)

        // 設置翻譯引擎選擇器
        setupTranslationEngineSpinner()

        // 設置相機按鈕點擊事件
        cameraButton.setOnClickListener {
            checkCameraPermissionAndOpenGoogleTranslate()
        }

        currentPlayCount = 0
        updatePlayCountBadge()

        textToSpeech = TextToSpeech(this, this)

        progressBar.visibility = View.GONE
        loadSavedContent()

        // 設置翻譯按鈕點擊事件
        translateButton.setOnClickListener {
            val text = inputText.text.toString()

            if (text.isNotBlank()) {
                resultText.text = getString(R.string.translating)
                progressBar.visibility = View.VISIBLE

                if (isPlaying) {
                    stopSpeaking()
                }

                isTranslated = false
                playStopButton.isEnabled = false

                lifecycleScope.launch {
                    try {
                        val isSourceChinese = isChineseText(text)
                        val sourceLanguage = if (isSourceChinese) "zh-CN" else "en"
                        val targetLanguage = if (isSourceChinese) "en" else "zh-CN"

                        val translatedText = when (selectedTranslationEngine) {
                            TRANSLATION_ENGINE_ML_KIT -> {
                                mlKitTranslateService.translate(
                                    text = text,
                                    sourceLanguage = sourceLanguage,
                                    targetLanguage = targetLanguage
                                )
                            }
                            TRANSLATION_ENGINE_GOOGLE_API -> {
                                googleTranslateService.translate(
                                    text = text,
                                    sourceLanguage = sourceLanguage,
                                    targetLanguage = targetLanguage
                                )
                            }
                            else -> {
                                // 默認使用ML Kit
                                mlKitTranslateService.translate(
                                    text = text,
                                    sourceLanguage = sourceLanguage,
                                    targetLanguage = targetLanguage
                                )
                            }
                        }

                        handleTranslationSuccess(text, translatedText, isSourceChinese)
                    } catch (e: Exception) {
                        handleTranslationFailure(e)
                    }
                }
            } else {
                Toast.makeText(this, getString(R.string.enter_text), Toast.LENGTH_SHORT).show()
            }
        }

        // 設置播放/停止按鈕點擊事件
        playStopButton.setOnClickListener {
            if (isTranslated && translatedEnglishText.isNotEmpty()) {
                if (!isPlaying) {
                    startSpeaking(translatedEnglishText)
                } else {
                    stopSpeaking()
                }
            }
        }

        bindTranslationService()
    }

    /**
     * 設置翻譯引擎選擇器
     */
    private fun setupTranslationEngineSpinner() {
        // 創建ArrayAdapter使用字符串數組和默認的spinner佈局
        ArrayAdapter.createFromResource(
            this,
            R.array.translation_engines,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // 指定下拉列表的佈局
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // 將適配器應用到spinner
            translationEngineSpinner.adapter = adapter
        }

        // 加載保存的偏好設置
        selectedTranslationEngine = sharedPreferences.getInt(KEY_TRANSLATION_ENGINE, TRANSLATION_ENGINE_ML_KIT)
        translationEngineSpinner.setSelection(selectedTranslationEngine)

        // 設置spinner監聽器
        translationEngineSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedTranslationEngine = position
                // 保存偏好設置
                with(sharedPreferences.edit()) {
                    putInt(KEY_TRANSLATION_ENGINE, selectedTranslationEngine)
                    apply()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // 不做任何事
            }
        }
    }

    /**
     * 處理翻譯成功
     */
    private fun handleTranslationSuccess(originalText: String, translatedText: String, isSourceChinese: Boolean) {
        progressBar.visibility = View.GONE
        resultText.text = translatedText
        isTranslated = true
        lastTranslatedText = originalText

        translatedEnglishText = if (isSourceChinese) {
            translatedText
        } else {
            originalText
        }

        playStopButton.isEnabled = true

        saveTranslationToFile(originalText, translatedText)

        startSpeaking(translatedEnglishText)
    }

    /**
     * 處理翻譯失敗
     */
    private fun handleTranslationFailure(exception: Exception) {
        progressBar.visibility = View.GONE
        resultText.text = getString(R.string.translation_failed, exception.message)
        playStopButton.isEnabled = false
    }

    /**
     * 綁定翻譯服務
     */
    private fun bindTranslationService() {
        Intent(this, TranslationService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * 更新播放/停止按鈕文本
     */
    private fun updatePlayStopButtonText() {
        if (isPlaying) {
            playStopButton.setIconResource(R.drawable.ic_pause)
            playStopButton.contentDescription = getString(R.string.stop_button)
        } else {
            playStopButton.setIconResource(R.drawable.ic_play)
            playStopButton.contentDescription = getString(R.string.play_button)
        }
    }

    /**
     * TTS初始化回調
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, getString(R.string.tts_not_supported), Toast.LENGTH_SHORT).show()
            }

            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isPlaying = true
                    runOnUiThread {
                        updatePlayStopButtonText()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId?.startsWith("play_") == true) {
                        currentPlayCount++
                        runOnUiThread {
                            updatePlayCountBadge()
                        }

                        if (isPlaying) {
                            val text = utteranceId.substring(5)
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
                        updatePlayStopButtonText()
                    }
                }
            })
        } else {
            Toast.makeText(this, getString(R.string.tts_init_failed), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 開始朗讀文本
     */
    private fun startSpeaking(text: String) {
        if (text.isNotBlank()) {
            if (isPlaying) {
                textToSpeech.stop()

                val serviceIntent = Intent(this, TranslationService::class.java).apply {
                    action = TranslationService.ACTION_STOP
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }

            isPlaying = true

            startTranslationService(text)

            speakText(text, "play_$text")
            updatePlayStopButtonText()
        }
    }

    /**
     * 啟動翻譯服務
     */
    private fun startTranslationService(text: String) {
        Log.d(TAG, "啟動翻譯服務，文本長度: ${text.length}")
        val serviceIntent = Intent(this, TranslationService::class.java).apply {
            action = TranslationService.ACTION_PLAY
            putExtra(TranslationService.EXTRA_TEXT, text)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
            Log.d(TAG, "使用startForegroundService啟動服務")
        } else {
            startService(serviceIntent)
            Log.d(TAG, "使用startService啟動服務")
        }

        if (!isServiceBound) {
            bindTranslationService()
            Log.d(TAG, "綁定到翻譯服務")
        }
    }

    /**
     * 停止朗讀
     */
    private fun stopSpeaking() {
        if (isPlaying) {
            isPlaying = false

            val serviceIntent = Intent(this, TranslationService::class.java).apply {
                action = TranslationService.ACTION_STOP
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            textToSpeech.stop()
            updatePlayStopButtonText()

            currentPlayCount = 0
            updatePlayCountBadge()
        }
    }

    /**
     * 朗讀文本
     */
    private fun speakText(text: String, utteranceId: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * 更新播放次數徽章
     */
    private fun updatePlayCountBadge() {
        playCountBadge.text = currentPlayCount.toString()
        if (currentPlayCount > 0) {
            playCountBadge.visibility = View.VISIBLE
        } else {
            playCountBadge.visibility = View.GONE
        }
    }

    /**
     * Activity開始時的回調
     */
    override fun onStart() {
        super.onStart()
        if (!isServiceBound) {
            bindTranslationService()
        }

        if (isServiceBound) {
            val notificationIntent = Intent(this, TranslationService::class.java).apply {
                action = TranslationService.ACTION_HIDE_NOTIFICATION
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(notificationIntent)
            } else {
                startService(notificationIntent)
            }
        }
    }

    /**
     * Activity暫停時的回調
     */
    override fun onPause() {
        super.onPause()
        saveEditorContent()
    }

    /**
     * Activity停止時的回調
     */
    override fun onStop() {
        super.onStop()
        saveEditorContent()

        if (isPlaying && isTranslated && lastTranslatedText.isNotBlank()) {
            startTranslationService(translatedEnglishText)

            val notificationIntent = Intent(this, TranslationService::class.java).apply {
                action = TranslationService.ACTION_SHOW_NOTIFICATION
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(notificationIntent)
            } else {
                startService(notificationIntent)
            }

            if (::textToSpeech.isInitialized) {
                textToSpeech.stop()
            }
        }
    }

    /**
     * Activity銷毀時的回調
     */
    override fun onDestroy() {
        super.onDestroy()
        saveEditorContent()

        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }

        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }

        if (::mlKitTranslateService.isInitialized) {
            mlKitTranslateService.close()
        }
    }

    /**
     * 請求通知權限
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "已有通知權限")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Toast.makeText(
                        this,
                        "需要通知權限才能在背景播放時顯示控制項",
                        Toast.LENGTH_LONG
                    ).show()
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    /**
     * 請求存儲權限
     */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "已有存儲權限")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    Toast.makeText(
                        this,
                        "需要存儲權限才能保存翻譯結果到文件",
                        Toast.LENGTH_LONG
                    ).show()
                    requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                else -> {
                    requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    /**
     * 保存編輯器內容
     */
    private fun saveEditorContent() {
        val editorContent = inputText.text.toString()
        val translationResult = resultText.text.toString()

        sharedPreferences.edit().apply {
            putString(KEY_EDITOR_CONTENT, editorContent)
            putString(KEY_RESULT_CONTENT, translationResult)
            putBoolean(KEY_IS_TRANSLATED, isTranslated)
            apply()
        }

        Log.d(TAG, "Editor content saved: $editorContent")
    }

    /**
     * 加載保存的內容
     */
    private fun loadSavedContent() {
        val savedEditorContent = sharedPreferences.getString(KEY_EDITOR_CONTENT, "")
        val savedResultContent = sharedPreferences.getString(KEY_RESULT_CONTENT, "")
        val savedIsTranslated = sharedPreferences.getBoolean(KEY_IS_TRANSLATED, false)

        if (!savedEditorContent.isNullOrEmpty()) {
            inputText.setText(savedEditorContent)
            lastTranslatedText = savedEditorContent

            if (savedIsTranslated && !savedResultContent.isNullOrEmpty()) {
                resultText.text = savedResultContent
                isTranslated = true

                if (isChineseText(savedEditorContent)) {
                    translatedEnglishText = savedResultContent
                } else {
                    translatedEnglishText = savedEditorContent
                }

                playStopButton.isEnabled = true
                updatePlayStopButtonText()
            }

            Log.d(TAG, "Loaded saved editor content: $savedEditorContent")
        }
    }

    /**
     * 檢查相機權限並打開Google翻譯
     */
    private fun checkCameraPermissionAndOpenGoogleTranslate() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startGoogleTranslateCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Toast.makeText(
                    this,
                    "需要相機權限才能使用此功能",
                    Toast.LENGTH_LONG
                ).show()
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * 啟動Google翻譯相機
     */
    private fun startGoogleTranslateCamera() {
        try {
            // 嘗試方法1：通過Google搜尋應用啟動Lens
            val googleSearchIntent = Intent().apply {
                action = Intent.ACTION_PROCESS_TEXT
                setPackage("com.google.android.googlequicksearchbox")
            }

            if (isIntentResolvable(googleSearchIntent)) {
                googleTranslateActivityResultLauncher.launch(googleSearchIntent)
                Log.d(TAG, "方法1: 通過Google搜尋啟動Lens")
                return
            }

            // 嘗試方法2：直接啟動Google Lens應用
            val lensIntent = Intent(Intent.ACTION_VIEW).apply {
                setPackage("com.google.android.apps.lens")
            }

            if (isIntentResolvable(lensIntent)) {
                googleTranslateActivityResultLauncher.launch(lensIntent)
                Log.d(TAG, "方法2: 直接啟動Google Lens")
                return
            }

            // 嘗試方法3：通過ACTION_WEB_SEARCH啟動Google
            val webSearchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", "lens")
            }

            if (isIntentResolvable(webSearchIntent)) {
                googleTranslateActivityResultLauncher.launch(webSearchIntent)
                Log.d(TAG, "方法3: 通過Web搜尋啟動Google")
                return
            }

            // 嘗試方法4：使用Chrome自訂搜尋
            val chromeIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://lens.google.com")
                setPackage("com.android.chrome")
            }

            if (isIntentResolvable(chromeIntent)) {
                googleTranslateActivityResultLauncher.launch(chromeIntent)
                Log.d(TAG, "方法4: 通過Chrome啟動Google Lens")
                return
            }

            // 如果上述方法都失敗，使用系統原生的OCR功能
            // (如果有的話，如Samsung的Bixby Vision或其他製造商的OCR功能)
            val intentList = ArrayList<Intent>()

            // 嘗試Samsung的Bixby Vision
            val bixbyIntent = Intent("com.samsung.android.bixby.vision.action.OCR")
            if (isIntentResolvable(bixbyIntent)) {
                intentList.add(bixbyIntent)
            }

            // 嘗試小米的OCR
            val miuiIntent = Intent("com.miui.contentextension.action.TAKE_NOTES_WITH_OCR")
            if (isIntentResolvable(miuiIntent)) {
                intentList.add(miuiIntent)
            }

            // 添加相機作為最後選項
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (isIntentResolvable(cameraIntent)) {
                intentList.add(cameraIntent)
            }

            if (intentList.isNotEmpty()) {
                // 使用第一個可用的意圖
                val firstIntent = intentList[0]
                if (firstIntent.action == MediaStore.ACTION_IMAGE_CAPTURE) {
                    // 如果是相機意圖，使用startActivityForResult
                    startActivityForResult(firstIntent, REQUEST_CAMERA_TEXT)
                } else {
                    // 其他OCR功能使用activityResultLauncher
                    googleTranslateActivityResultLauncher.launch(firstIntent)
                }
                Log.d(TAG, "使用備用OCR方法: ${firstIntent.action}")
            } else {
                // 真的沒有任何可用方法，顯示錯誤
                Toast.makeText(
                    this,
                    getString(R.string.lens_not_found),
                    Toast.LENGTH_LONG
                ).show()

                // 提示用戶安裝Google Lens
                try {
                    val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("market://details?id=com.google.android.apps.lens")
                    }
                    startActivity(playStoreIntent)
                } catch (e: ActivityNotFoundException) {
                    val webIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.lens")
                    }
                    startActivity(webIntent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "啟動智慧鏡頭失敗: ${e.message}", e)
            Toast.makeText(
                this,
                getString(R.string.lens_launch_failed, e.message),
                Toast.LENGTH_LONG
            ).show()

            // 作為最後手段，嘗試啟動系統相機
            try {
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(cameraIntent, REQUEST_CAMERA_TEXT)
            } catch (e: Exception) {
                Log.e(TAG, "啟動系統相機也失敗: ${e.message}", e)
            }
        }
    }

    // 檢查意圖是否可被解析（是否有應用能處理此意圖）
    private fun isIntentResolvable(intent: Intent): Boolean {
        return intent.resolveActivity(packageManager) != null
    }

    private fun isChineseText(text: String): Boolean {
        val pattern = Pattern.compile("[\\u4e00-\\u9fa5]")
        val matcher = pattern.matcher(text)
        return matcher.find()
    }

    private fun saveTranslationToFile(originalText: String, translatedText: String) {
        try {
            val directory = getExternalFilesDir(null) ?: filesDir
            val translationsDir = File(directory, "translations")

            if (!translationsDir.exists()) {
                translationsDir.mkdirs()
            }

            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val fileName = "translations_${dateFormat.format(Date())}.txt"
            val file = File(translationsDir, fileName)

            val translationLine = "$originalText    $translatedText\n"

            file.appendText(translationLine)

            val message = "翻譯已保存到: ${file.absolutePath}"
            Log.d(TAG, message)
            Toast.makeText(this, "翻譯已保存", Toast.LENGTH_SHORT).show()

        } catch (e: IOException) {
            Log.e(TAG, "保存翻譯到文件失敗", e)
            Toast.makeText(this, "無法保存翻譯: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_view_translations -> {
                viewSavedTranslations()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun viewSavedTranslations() {
        try {
            // 獲取存儲翻譯的目錄
            val directory = getExternalFilesDir(null) ?: filesDir
            val translationsDir = File(directory, "translations")

            if (!translationsDir.exists() || translationsDir.listFiles()?.isEmpty() == true) {
                Toast.makeText(this, "沒有找到已保存的翻譯文件", Toast.LENGTH_SHORT).show()
                return
            }

            // 獲取最新的翻譯文件
            val translationFiles = translationsDir.listFiles()?.filter { it.isFile && it.name.startsWith("translations_") }

            if (translationFiles.isNullOrEmpty()) {
                Toast.makeText(this, "沒有找到已保存的翻譯文件", Toast.LENGTH_SHORT).show()
                return
            }

            // 按時間排序，獲取最新的文件
            val latestFile = translationFiles.maxByOrNull { it.lastModified() }

            if (latestFile == null) {
                Toast.makeText(this, "無法確定最新的翻譯文件", Toast.LENGTH_SHORT).show()
                return
            }

            // 使用系統默認的文件查看器打開文件
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    latestFile
                )
            } else {
                Uri.fromFile(latestFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "沒有應用程序可以打開此類型的文件", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "查看翻譯文件失敗", e)
            Toast.makeText(this, "無法打開翻譯文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}