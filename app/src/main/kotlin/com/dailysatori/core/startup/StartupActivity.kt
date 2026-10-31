package com.dailysatori.core.startup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.dailysatori.DailySatoriApplication
import com.dailysatori.DiagnosticRecoveryActivity
import com.dailysatori.core.service.I18nInitializer
import com.dailysatori.ui.theme.DailySatoriTheme
import kotlinx.coroutines.flow.combine
import android.view.ViewTreeObserver
import kotlinx.coroutines.launch

/** No business DI, locale lookup or saved-state consumer runs before the database is ready. */
abstract class StartupActivity : ComponentActivity() {
    protected open val useLaunchSplash = false
    protected var businessReady = false
        private set
    private var attachedLanguage: String? = null
    private val pendingIntents = arrayListOf<Intent>()
    private var startupShown = false
    // A locale recreation may reuse the window and its pre-draw observers.
    private val shouldRetainLaunchWindow: Boolean
        get() = !isDestroyed && !isFinishing && !businessReady && !startupShown

    override fun attachBaseContext(newBase: Context) {
        val app = newBase.applicationContext as DailySatoriApplication
        val language = if (app.startup.isReady) app.startupLanguage else null
        attachedLanguage = language ?: newBase.resources.configuration.locales[0].language
        super.attachBaseContext(language?.let { I18nInitializer.localizedContext(newBase, it) } ?: newBase)
    }

    final override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = if (useLaunchSplash) installSplashScreen() else null
        super.onCreate(savedInstanceState)
        splashScreen?.setKeepOnScreenCondition { shouldRetainLaunchWindow }
        @Suppress("DEPRECATION")
        savedInstanceState?.getParcelable<Intent>(PENDING_INTENT)?.let { setIntent(it) }
        @Suppress("DEPRECATION")
        val restored = savedInstanceState?.getParcelableArrayList<Intent>(PENDING_INTENTS)
        if (restored != null) pendingIntents.addAll(restored)
        else if (!appStartup.isReady) pendingIntents.add(Intent(intent))
        enableEdgeToEdge()
        if (appStartup.isReady) openBusiness(savedInstanceState) else {
            if (splashScreen == null) retainLaunchWindow()
            lifecycleScope.launch {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    combine(appStartup.state, appStartup.dataPreparation) { state, preparing ->
                        state to preparing
                    }.collect { (state, preparing) ->
                        when {
                            state == StartupState.Ready -> openBusiness(savedInstanceState)
                            !startupShown && (preparing || state == StartupState.Failed) -> {
                                startupShown = true
                                showStartup()
                            }
                        }
                    }
                }
            }
        }
    }

    /** Keep the launch window for ordinary initialization; do not flash a migration page. */
    private fun retainLaunchWindow() {
        val content = findViewById<android.view.View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (shouldRetainLaunchWindow) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    protected open fun showStartup() {
        setContent {
            val state by appStartup.state.collectAsState()
            DailySatoriTheme {
                StartupScreen(state, onRetry = { appStartup.start() }, onDiagnostics = {
                    startActivity(Intent(this, DiagnosticRecoveryActivity::class.java)
                        .setAction(DiagnosticRecoveryActivity.ACTION_EXPORT))
                })
            }
        }
    }

    private fun openBusiness(savedInstanceState: Bundle?) {
        if (isFinishing || isDestroyed || businessReady) return
        val app = application as DailySatoriApplication
        if (app.startupLanguage != null && attachedLanguage != app.startupLanguage) {
            recreate()
            return
        }
        businessReady = true
        val queued = pendingIntents.toList()
        pendingIntents.clear()
        queued.firstOrNull()?.let { setIntent(it) }
        app.consumeStartupNotice()?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        onStartupReady(savedInstanceState)
        recordFirstBusinessDraw()
        queued.drop(1).forEach { onNewIntent(it) }
    }

    private fun recordFirstBusinessDraw() {
        val content = findViewById<android.view.View>(android.R.id.content)
        var recorded = false
        content.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                if (recorded) return
                recorded = true
                com.dailysatori.service.diagnostics.DiagnosticLog.diagnostics.emit(
                    com.dailysatori.service.diagnostics.DiagnosticCode.OPERATION_PROGRESS,
                    com.dailysatori.service.diagnostics.DiagnosticSource.APP,
                    fields = mapOf("startupStage" to "first_draw"),
                )
                content.post {
                    if (content.viewTreeObserver.isAlive) content.viewTreeObserver.removeOnDrawListener(this)
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!businessReady) pendingIntents.add(Intent(intent))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(PENDING_INTENT, intent)
        outState.putParcelableArrayList(PENDING_INTENTS, ArrayList(pendingIntents))
        super.onSaveInstanceState(outState)
    }

    protected abstract fun onStartupReady(savedInstanceState: Bundle?)

    private companion object {
        const val PENDING_INTENT = "startup.pending_intent"
        const val PENDING_INTENTS = "startup.pending_intents"
    }
}
