package tours.satori.daily

import android.content.Intent
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import androidx.annotation.NonNull

class MainActivity: FlutterActivity() {
    private val CHANNEL = "tours.sator.daily/share"
    private val BACK_CHANNEL = "android/back/desktop"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_SEND == intent.action && intent.type != null) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val binaryMessenger = flutterEngine?.dartExecutor?.binaryMessenger
            if (sharedText != null && binaryMessenger != null) {
                // 通过 MethodChannel 将数据传递给 Flutter
                MethodChannel(binaryMessenger, CHANNEL)
                    .invokeMethod("receiveSharedText", sharedText)
            }
        }
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, BACK_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "backDesktop" -> {
                    moveTaskToBack(false)
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
    }
}
