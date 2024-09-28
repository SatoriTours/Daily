package tours.satori.daily

import android.content.Intent
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "tours.sator.daily/share"

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
}
