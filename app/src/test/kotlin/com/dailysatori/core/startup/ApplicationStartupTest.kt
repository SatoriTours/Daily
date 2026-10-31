package com.dailysatori.core.startup

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.os.Looper
import com.dailysatori.DailySatoriApplication
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.*
import androidx.work.*
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor
import com.dailysatori.core.worker.*
import com.dailysatori.core.reminder.ReminderWorker
import com.dailysatori.core.reminder.ReminderBroadcastWorker
import com.dailysatori.core.sms.SmsIntakeWorker
import com.dailysatori.core.bookkeeping.BookkeepingNotificationListener
import java.util.UUID
import java.util.concurrent.Executor
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = SlowStartupApplication::class)
class ApplicationStartupTest {
    private val app get() = RuntimeEnvironment.getApplication() as SlowStartupApplication

    @After
    fun releasePreparation() {
        app.release.countDown()
        assertTrue(app.entered.await(3, TimeUnit.SECONDS))
        runBlocking { withTimeout(5_000) { app.initializingJob.join() } }
    }

    @Test
    fun applicationOnCreateReturnsAndMainLooperStaysResponsiveDuringSlowNativePreparation() {
        assertTrue(app.entered.await(3, TimeUnit.SECONDS))
        assertEquals(StartupState.Preparing, app.startup.state.value)
        assertNotSame(Looper.getMainLooper().thread, app.runtimeThread)
        var dispatched = false
        android.os.Handler(Looper.getMainLooper()).post { dispatched = true }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(dispatched, "The real Application.onCreate must not wait for the native database call")
        assertEquals(0, app.backgroundStarts.get())
    }

    @Test
    fun ordinaryInitializationDoesNotShowDataPreparationPage() {
        assertLaunchWindowUntilReady(WaitingStartupActivity::class.java)
    }

    @Test
    fun systemSplashStaysUntilBusinessCanDraw() {
        assertLaunchWindowUntilReady(SplashWaitingStartupActivity::class.java)
    }

    @Test
    fun destroyedActivityDoesNotKeepRetainedSplashWindowBlocked() {
        assertDestroyedActivityReleasesWindow(SplashWaitingStartupActivity::class.java)
    }

    @Test
    fun destroyedActivityDoesNotKeepLegacyLaunchWindowBlocked() {
        assertDestroyedActivityReleasesWindow(WaitingStartupActivity::class.java)
    }

    private fun assertDestroyedActivityReleasesWindow(activityClass: Class<out WaitingStartupActivity>) {
        val controller = Robolectric.buildActivity(activityClass, request("recreated"))
            .create().start().resume()
        val observer = controller.get().findViewById<android.view.View>(android.R.id.content).viewTreeObserver
        assertTrue(observer.dispatchOnPreDraw(), "Initialization still owns the launch window")
        controller.pause().stop().destroy()
        assertFalse(observer.dispatchOnPreDraw(), "An old Activity must not block the window reused by its replacement")
    }

    private fun assertLaunchWindowUntilReady(activityClass: Class<out WaitingStartupActivity>) {
        val controller = Robolectric.buildActivity(activityClass, request("normal"))
            .create().start().resume()
        assertFalse(controller.get().waitingShown, "Ordinary database opening is not a data migration")
        val content = controller.get().findViewById<android.view.View>(android.R.id.content)
        assertTrue(content.viewTreeObserver.dispatchOnPreDraw(), "Keep the launch window while DI is unavailable")
        assertTrue(controller.get().handled.isEmpty(), "Business DI must still wait for readiness")
        completePreparation()
        assertFalse(content.viewTreeObserver.dispatchOnPreDraw(), "Allow the normal page to draw immediately")
        assertFalse(controller.get().waitingShown)
        assertEquals(listOf("normal"), controller.get().handled)
        controller.pause().stop().destroy()
    }

    @Test
    fun activityWaitsAndReplaysAllIntentsOnlyAfterReady() {
        app.startup.requireDataPreparation()
        val controller = Robolectric.buildActivity(WaitingStartupActivity::class.java, request("initial"))
            .create().start().resume()
        val activity = controller.get()
        assertTrue(activity.waitingShown)
        assertTrue(activity.handled.isEmpty())
        activity.onNewIntent(request("oauth"))
        activity.onNewIntent(request("reminder"))
        completePreparation()
        assertEquals(listOf("initial", "oauth", "reminder"), activity.handled)
        assertEquals(1, app.runtimeStarts.get())
        controller.pause().stop().destroy()
    }

    @Test
    fun ordinaryInitializationFailureShowsRecoveryAndRetryStillWaitsForReadiness() {
        val controller = Robolectric.buildActivity(WaitingStartupActivity::class.java, request("retry"))
            .create().start().resume()
        app.failPreparation = true
        app.release.countDown()
        runBlocking { withTimeout(5_000) { app.initializingJob.join() } }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.get().waitingShown)
        assertTrue(controller.get().handled.isEmpty())
        app.failPreparation = false
        app.startup.start()
        completePreparation()
        assertEquals(listOf("retry"), controller.get().handled)
        controller.pause().stop().destroy()
    }

    @Test
    fun migrationDetectedAfterActivityStartShowsPreparationPage() {
        assertMigrationReleasesLaunchWindow(WaitingStartupActivity::class.java)
    }

    @Test
    fun systemSplashDoesNotHideMigrationProgress() {
        assertMigrationReleasesLaunchWindow(SplashWaitingStartupActivity::class.java)
    }

    private fun assertMigrationReleasesLaunchWindow(activityClass: Class<out WaitingStartupActivity>) {
        val controller = Robolectric.buildActivity(activityClass, request("upgrade"))
            .create().start().resume()
        assertFalse(controller.get().waitingShown)
        app.startup.requireDataPreparation()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.get().waitingShown)
        val content = controller.get().findViewById<android.view.View>(android.R.id.content)
        assertFalse(content.viewTreeObserver.dispatchOnPreDraw(), "Real migration progress must be drawable")
        assertTrue(controller.get().handled.isEmpty())
        completePreparation()
        assertEquals(listOf("upgrade"), controller.get().handled)
        controller.pause().stop().destroy()
    }

    @Test
    fun activityRecreationKeepsPendingIntentsWithoutStartingAnotherUpgrade() {
        val controller = Robolectric.buildActivity(WaitingStartupActivity::class.java, request("share"))
            .create().start().resume()
        controller.get().onNewIntent(request("reminder"))
        val saved = Bundle()
        controller.pause().saveInstanceState(saved).stop().destroy()
        val recreated = Robolectric.buildActivity(WaitingStartupActivity::class.java, request("stale"))
            .create(saved).start().resume()
        assertTrue(recreated.get().handled.isEmpty())
        completePreparation()
        assertEquals(listOf("share", "reminder"), recreated.get().handled)
        assertEquals(1, app.runtimeStarts.get())
        recreated.pause().stop().destroy()
    }

    private fun completePreparation() {
        finishPreparation()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun readinessAfterSavedStateDoesNotActivateStoppedActivityOrErasePendingIntents() {
        val controller = Robolectric.buildActivity(WaitingStartupActivity::class.java, request("share"))
            .create().start().resume()
        controller.get().onNewIntent(request("oauth"))
        val saved = Bundle()
        controller.pause().saveInstanceState(saved).stop()
        completePreparation()
        assertTrue(controller.get().handled.isEmpty(), "A stopped activity must not execute business startup")
        controller.destroy()
        val recreated = Robolectric.buildActivity(WaitingStartupActivity::class.java, request("stale"))
            .create(saved).start().resume()
        assertEquals(listOf("share", "oauth"), recreated.get().handled)
        recreated.pause().stop().destroy()
    }

    @Test
    fun realCoroutineWorkerWaitsAndCancellationCannotCancelApplicationPreparation() = runBlocking {
        var businessCalls = 0
        val worker = object : StartupWorker(app, parameters()) {
            override suspend fun doReadyWork(): Result { businessCalls++; return Result.success() }
        }
        val cancelled = async(start = CoroutineStart.UNDISPATCHED) { worker.doWork() }
        assertFalse(cancelled.isCompleted)
        assertEquals(0, businessCalls)
        cancelled.cancelAndJoin()
        assertEquals(StartupState.Preparing, app.startup.state.value)
        val surviving = async(start = CoroutineStart.UNDISPATCHED) { worker.doWork() }
        assertFalse(surviving.isCompleted)
        finishPreparation()
        assertEquals(ListenableWorker.Result.success(), surviving.await())
        assertEquals(1, businessCalls)
    }

    @Test
    fun failedWorkerRetriesWithoutResolvingBusinessDependenciesAndExplicitRetryRecovers() = runBlocking {
        var businessCalls = 0
        val worker = object : StartupWorker(app, parameters()) {
            override suspend fun doReadyWork(): Result { businessCalls++; return Result.success() }
        }
        app.failPreparation = true
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { worker.doWork() }
        app.release.countDown()
        assertEquals(ListenableWorker.Result.retry(), withTimeout(5_000) { waiting.await() })
        assertEquals(0, businessCalls)
        app.failPreparation = false
        app.startup.start()
        withTimeout(5_000) { assertTrue(app.startup.awaitReady()) }
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(1, businessCalls)
        assertEquals(2, app.runtimeStarts.get())
    }

    @Test
    fun everyBusinessWorkerUsesTheReadinessGate() {
        val workers = listOf(GenericAsyncTaskWorker::class.java, ArticleProcessingWorker::class.java,
            BackupWorker::class.java, DiaryThoughtWorker::class.java, UnifiedNewsWorker::class.java,
            WeeklySummaryWorker::class.java, ExternalFavoriteSyncWorker::class.java,
            ExternalFavoriteQueueWorker::class.java, ExternalFavoriteQueueDelayWorker::class.java,
            ReminderWorker::class.java, ReminderBroadcastWorker::class.java, SmsIntakeWorker::class.java)
        workers.forEach { assertTrue(StartupWorker::class.java.isAssignableFrom(it), it.name) }
    }

    @Test
    fun notificationServiceConnectionAndDestructionDoNotResolveDiWhilePreparing() {
        val controller = Robolectric.buildService(BookkeepingNotificationListener::class.java).create()
        controller.get().onListenerConnected()
        controller.get().onListenerDisconnected()
        controller.destroy()
        assertEquals(StartupState.Preparing, app.startup.state.value)
    }

    private fun parameters(): WorkerParameters {
        val executor = Executor { it.run() }
        return WorkerParameters(UUID.randomUUID(), Data.EMPTY, emptyList(), WorkerParameters.RuntimeExtras(),
            0, 0, executor, Dispatchers.IO, WorkManagerTaskExecutor(executor),
            object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? = null
            },
            ProgressUpdater { _, _, _ -> error("Unexpected progress before business work") },
            ForegroundUpdater { _, _, _ -> error("Unexpected foreground transition before business work") })
    }

    @Test
    fun restoreNoticeIsConsumedOnlyOnceAcrossActivityCreations() {
        org.robolectric.util.ReflectionHelpers.setField(app, "startupNotice", "fixture restore notice")
        assertEquals("fixture restore notice", app.consumeStartupNotice())
        assertNull(app.consumeStartupNotice())
    }

    private fun finishPreparation() {
        app.release.countDown()
        assertTrue(app.entered.await(3, TimeUnit.SECONDS))
        runBlocking {
            withTimeout(5_000) {
                // Ready is published before its subscribers are all dispatched. Join the owner
                // before draining Robolectric's paused looper, rather than racing that dispatch.
                app.initializingJob.join()
                assertTrue(app.startup.awaitReady())
            }
        }
    }

    private fun request(value: String) = Intent().putExtra("request", value)
}

class SlowStartupApplication : DailySatoriApplication() {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val runtimeStarts = AtomicInteger()
    val backgroundStarts = AtomicInteger()
    @Volatile var runtimeThread: Thread? = null
    @Volatile var failPreparation = false
    lateinit var initializingJob: Job
    override suspend fun initializeRuntime() {
        runtimeStarts.incrementAndGet()
        initializingJob = currentCoroutineContext().job
        runtimeThread = Thread.currentThread()
        entered.countDown()
        check(release.await(15, TimeUnit.SECONDS))
        if (failPreparation) throw java.io.IOException("Controlled preparation failure")
    }
    override fun initializeBackgroundServices() { backgroundStarts.incrementAndGet() }
}

open class WaitingStartupActivity : StartupActivity() {
    var waitingShown = false
    val handled = mutableListOf<String>()
    override fun showStartup() { waitingShown = true }
    override fun onStartupReady(savedInstanceState: Bundle?) { handled.add(intent.getStringExtra("request").orEmpty()) }
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (businessReady) handled.add(intent.getStringExtra("request").orEmpty())
    }
}

class SplashWaitingStartupActivity : WaitingStartupActivity() {
    override val useLaunchSplash = true
}
