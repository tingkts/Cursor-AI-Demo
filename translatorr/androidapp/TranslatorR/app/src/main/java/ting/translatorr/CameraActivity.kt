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