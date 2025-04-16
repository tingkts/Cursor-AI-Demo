package ting.translatorr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraActivity 是一個相機活動，用於實現光學字符識別(OCR)功能。
 *
 * 注意：目前這個 Activity 似乎沒有在 AndroidManifest.xml 中註冊，
 * 也沒有在其他地方被調用。如果要使用這個功能，需要：
 *
 * 1. 在 AndroidManifest.xml 中註冊這個 Activity:
 *    <activity android:name=".CameraActivity" />
 *
 * 2. 從其他 Activity (如 MainActivity) 中調用，例如：
 *    val intent = Intent(this, CameraActivity::class.java)
 *    startActivityForResult(intent, REQUEST_CODE_CAMERA)
 *
 * 3. 在調用方接收返回的文字結果：
 *    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
 *        if (requestCode == REQUEST_CODE_CAMERA && resultCode == RESULT_OK) {
 *            val recognizedText = data?.getStringExtra(CameraActivity.EXTRA_RECOGNIZED_TEXT)
 *            // 處理識別出的文字
 *        }
 *    }
 *
 * 主要功能:
 * 1. 開啟相機預覽
 * 2. 使用 ML Kit 的文字識別功能來識別相機畫面中的文字
 * 3. 當用戶點擊拍攝按鈕時，會捕獲當前畫面並進行文字識別
 * 4. 識別完成後，將結果返回給主活動進行後續處理（如翻譯）
 */
class CameraActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "CameraActivity"
        const val EXTRA_RECOGNIZED_TEXT = "recognized_text"
    }

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var textRecognizer: TextRecognizer
    private lateinit var viewFinder: PreviewView
    private lateinit var captureButton: MaterialButton
    private var hasProcessedFrame = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // 初始化視圖
        viewFinder = findViewById(R.id.viewFinder)
        captureButton = findViewById(R.id.captureButton)

        // 設置點擊按鈕立即捕捉當前幀
        captureButton.setOnClickListener {
            hasProcessedFrame = false
        }

        // 初始化文字識別器
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        // 設置相機執行器
        cameraExecutor = Executors.newSingleThreadExecutor()

        // 請求相機權限
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 設置預覽
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // 設置圖像分析
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer())
                }

            // 選擇後置相機
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // 解綁任何現有用例
                cameraProvider.unbindAll()

                // 綁定用例到相機
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )

            } catch (exc: Exception) {
                Log.e(TAG, "相機綁定失敗", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            if (hasProcessedFrame) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                // 處理文本識別
                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        // 處理識別結果
                        processTextRecognitionResult(visionText)
                        hasProcessedFrame = true
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "文本識別失敗: $e")
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    private fun processTextRecognitionResult(texts: Text) {
        val blocks = texts.textBlocks
        if (blocks.isEmpty()) {
            Toast.makeText(this, "未檢測到文本", Toast.LENGTH_SHORT).show()
            return
        }

        // 提取所有文本並合併
        val recognizedText = blocks.joinToString("\n") { it.text }

        // 將識別到的文本返回給主活動
        val resultIntent = Intent().apply {
            putExtra(EXTRA_RECOGNIZED_TEXT, recognizedText)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(Manifest.permission.CAMERA)
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "需要相機權限才能使用此功能",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}