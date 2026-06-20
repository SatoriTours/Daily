package com.dailysatori

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.dailysatori.core.worker.ExternalFavoriteSyncScheduler
import com.dailysatori.service.externalfavorites.FavoriteSyncMode
import com.dailysatori.service.externalfavorites.XOAuthCoordinator
import com.dailysatori.ui.theme.DailySatoriTheme
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleOAuthIntent(intent)
        enableEdgeToEdge()
        setContent {
            DailySatoriTheme {
                DailySatoriApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri = intent?.data?.toString()?.takeIf { it.startsWith("dailysatori://oauth/x") } ?: return
        lifecycleScope.launch {
            runCatching {
                val koin = GlobalContext.get()
                val sourceId = koin.get<XOAuthCoordinator>().handleCallbackUrl(uri)
                koin.get<ExternalFavoriteSyncScheduler>().enqueue(sourceId, FavoriteSyncMode.history.name)
            }
        }
    }
}
