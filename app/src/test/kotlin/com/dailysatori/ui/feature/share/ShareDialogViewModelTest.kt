package com.dailysatori.ui.feature.share

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.core.worker.ArticleProcessingScheduler
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class ShareDialogViewModelTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: DailySatoriDatabase
    private lateinit var articles: ArticleRepository
    private lateinit var tags: TagRepository
    private lateinit var scheduler: ArticleProcessingScheduler
    private lateinit var ioExecutor: ExecutorService
    private lateinit var mainExecutor: ExecutorService

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(Log.ERROR).setExecutor(SynchronousExecutor()).build(),
        )
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        db = DailySatoriDatabase(driver)
        articles = ArticleRepository(db)
        tags = TagRepository(db)
        scheduler = ArticleProcessingScheduler(context, articleRepo = articles)
        ioExecutor = Executors.newSingleThreadExecutor { Thread(it, IO_THREAD) }
        mainExecutor = Executors.newSingleThreadExecutor { Thread(it, MAIN_THREAD) }
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ioExecutor.shutdownNow()
        mainExecutor.shutdownNow()
        driver.close()
    }

    private fun viewModel() = ShareDialogViewModel(
        articleRepo = articles,
        tagRepo = tags,
        scheduler = scheduler,
        parserService = null,
        ioDispatcher = ioExecutor.asCoroutineDispatcher(),
        mainDispatcher = mainExecutor.asCoroutineDispatcher(),
    )

    /** initialize 现在会把"初始化中"视为不可保存状态，测试须等它结束。 */
    private suspend fun initializeAndAwait(vm: ShareDialogViewModel, url: String = SHARED_URL) {
        vm.initialize(url)
        withTimeout(5_000) { vm.state.first { !it.isInitializing } }
    }

    /** Runs save and returns the thread the success callback ran on. */
    private suspend fun saveAndAwaitCallback(vm: ShareDialogViewModel): String {
        val callbackThread = CompletableDeferred<String>()
        vm.save { callbackThread.complete(Thread.currentThread().name) }
        val thread = withTimeout(5_000) { callbackThread.await() }
        assertTrue(thread.startsWith(MAIN_THREAD), "成功回调必须在主线程执行，而实际线程是 $thread")
        return thread
    }

    @Test
    fun updatesExistingArticleKeepingProcessedFieldsAndPersistingTags() = runBlocking {
        val id = articles.insert(
            title = "原标题", aiTitle = "AI 标题", aiContent = "AI 摘要", aiMarkdownContent = "AI 译文",
            url = SHARED_URL, comment = "旧备注", status = "completed",
            coverImage = "/data/covers/local.png", coverImageUrl = "https://cdn.example.com/cover.png",
            pubDate = 1_700_000_000_000L,
        )
        tags.setTagsForArticle(id, listOf("旧标签"))
        val before = assertNotNull(articles.getById(id))

        val vm = viewModel()
        vm.initialize(SHARED_URL)
        val loaded = withTimeout(5_000) { vm.state.first { it.isUpdate } }
        assertEquals(listOf("旧标签"), loaded.tags, "已存在文章必须回填已有标签")

        vm.onTitleChanged("新标题")
        vm.onCommentChanged("新备注")
        vm.removeTag("旧标签")
        vm.onTagInputChanged("新标签")
        vm.addTag()
        val callbackThread = saveAndAwaitCallback(vm)

        val after = assertNotNull(articles.getById(id))
        assertEquals("新标题", after.title)
        assertEquals("新备注", after.comment)
        assertEquals(before.ai_title, after.ai_title)
        assertEquals(before.ai_content, after.ai_content)
        assertEquals(before.ai_markdown_content, after.ai_markdown_content)
        assertEquals(before.cover_image, after.cover_image)
        assertEquals(before.cover_image_url, after.cover_image_url)
        assertEquals(before.pub_date, after.pub_date)
        assertEquals(before.status, after.status)
        assertEquals(listOf("新标签"), tags.getByArticle(id).first().map { it.name })
        assertEquals(1L, articles.count(), "更新已有文章不能新增一篇")
        assertEquals("新标题", vm.state.value.existingArticle?.title, "保存后必须刷新快照，避免下次覆盖新处理结果")
        assertFalse(callbackThread.contains(IO_THREAD), "成功回调不能留在 IO 线程")
    }

    @Test
    fun newShareLinkSavesUserFieldsAndSchedulesProcessingWithoutDuplicates() = runBlocking {
        val vm = viewModel()
        initializeAndAwait(vm)
        vm.onTitleChanged("分享标题")
        vm.onCommentChanged("分享备注")
        vm.onTagInputChanged("标签A")
        vm.addTag()
        assertFalse(scheduler.isSavePending(SHARED_URL))

        saveAndAwaitCallback(vm)

        val saved = articles.getAllSync()
        assertEquals(1, saved.size, "同一 URL 不能产生两篇文章")
        assertEquals("分享标题", saved.single().title)
        assertEquals("分享备注", saved.single().comment)
        assertEquals("manual", saved.single().source_type)
        assertEquals(listOf("标签A"), tags.getByArticle(saved.single().id).first().map { it.name })
        assertTrue(scheduler.isSavePending(SHARED_URL), "启用 AI 分析时必须触发抓取调度")
    }

    @Test
    fun disablingAiAnalysisSavesWithoutScheduling() = runBlocking {
        val vm = viewModel()
        initializeAndAwait(vm)
        vm.toggleAiAnalysis()
        vm.onTitleChanged("仅保存标题")

        saveAndAwaitCallback(vm)

        val saved = articles.getAllSync().single()
        assertEquals("仅保存标题", saved.title)
        assertEquals("pending", saved.status)
        assertFalse(scheduler.isSavePending(SHARED_URL), "关闭 AI 分析时不应触发抓取调度")
    }

    @Test
    fun emptyShareLinkDefersCreationToTheSaveTaskWithoutDuplicating() = runBlocking {
        val vm = viewModel()
        initializeAndAwait(vm)

        saveAndAwaitCallback(vm)

        val saved = articles.getAllSync()
        assertEquals(1, saved.size)
        assertEquals(SHARED_URL, saved.single().url)
        assertEquals("manual", saved.single().source_type)
        assertTrue(scheduler.isSavePending(SHARED_URL))
    }

    @Test
    fun duplicateSavePressOnlyRunsOnce() = runBlocking {
        val vm = viewModel()
        initializeAndAwait(vm)
        vm.onTitleChanged("只保存一次")

        val callbackThread = CompletableDeferred<String>()
        vm.save { callbackThread.complete(Thread.currentThread().name) }
        vm.save { callbackThread.complete("second") }
        assertTrue(withTimeout(5_000) { callbackThread.await() }.startsWith(MAIN_THREAD))

        assertEquals(1L, articles.count())
    }

    @Test
    fun initializeWithoutExistingArticleResetsUpdateState() = runBlocking {
        val existingId = articles.insert(title = "已有文章", url = SHARED_URL)
        tags.setTagsForArticle(existingId, listOf("旧标签"))
        val vm = viewModel()
        vm.initialize(SHARED_URL)
        withTimeout(5_000) { vm.state.first { it.isUpdate && !it.isInitializing } }

        val fresh = "$SHARED_URL?fresh"
        vm.initialize(fresh)

        val reset = vm.state.value
        assertFalse(reset.isUpdate)
        assertNull(reset.existingArticle)
        assertTrue(reset.tags.isEmpty())
        assertEquals(fresh, reset.shareURL)
        assertEquals("", reset.title)
    }

    @Test
    fun saveBeforeInitializationCompletesIsIgnored() = runBlocking {
        val gate = CountDownLatch(1)
        val blocker = ioExecutor.submit { gate.await() }
        val vm = viewModel()
        vm.initialize(SHARED_URL)
        assertTrue(vm.state.value.isInitializing, "initialize 必须先标记初始化中")

        val premature = CompletableDeferred<Unit>()
        vm.save { premature.complete(Unit) }

        gate.countDown()
        blocker.get(5, TimeUnit.SECONDS)
        withTimeout(5_000) { vm.state.first { !it.isInitializing } }

        assertFalse(premature.isCompleted, "初始化未完成时调用 save 必须被拦截")
        assertEquals(0L, articles.count(), "被拦截的保存不能提前插入记录")

        saveAndAwaitCallback(vm)
        assertEquals(1L, articles.count(), "初始化完成后的保存仍应只产生一篇")
    }

    @Test
    fun saveWithoutLoadedExistingArticleUpdatesTheExistingRow() = runBlocking {
        val vm = viewModel()
        vm.initialize(SHARED_URL)
        withTimeout(5_000) { vm.state.first { !it.isInitializing } }
        assertFalse(vm.state.value.isUpdate, "此时页面还不知道已有文章")

        // 后台路径在初始化查询之后才写入了同一 URL 的文章行。
        val existingId = articles.insert(title = "原始标题", url = SHARED_URL, status = "pending")

        vm.onCommentChanged("新备注")
        saveAndAwaitCallback(vm)

        assertEquals(1L, articles.count(), "同一 URL 不能产生第二条记录")
        val saved = assertNotNull(articles.getById(existingId))
        assertEquals("原始标题", saved.title, "未编辑的标题不能被清空")
        assertEquals("新备注", saved.comment)
    }

    @Test
    fun userMetadataSaveKeepsFieldsWrittenAfterDialogOpened() = runBlocking {
        val id = articles.insert(title = "占位标题", url = SHARED_URL, status = "pending")
        val vm = viewModel()
        vm.initialize(SHARED_URL)
        withTimeout(5_000) { vm.state.first { it.isUpdate && !it.isInitializing } }

        // 分享页打开后，后台处理写入了 AI 字段与封面。
        articles.updateAiTitle(id, "后台 AI 标题")
        articles.updateAiContent(id, "后台 AI 摘要", "后台 AI 标题", "https://cdn.example.com/cover.png")
        articles.updateAiMarkdownContent(id, "后台 AI 译文")
        articles.updateCoverIfUrlMatches(id, "https://cdn.example.com/cover.png", "/data/covers/local.png")

        vm.onCommentChanged("仅改备注")
        saveAndAwaitCallback(vm)

        val after = assertNotNull(articles.getById(id))
        assertEquals("仅改备注", after.comment)
        assertEquals("后台 AI 标题", after.ai_title, "后写入的 AI 标题不能被旧快照覆盖")
        assertEquals("后台 AI 摘要", after.ai_content, "后写入的 AI 摘要不能被旧快照覆盖")
        assertEquals("后台 AI 译文", after.ai_markdown_content)
        assertEquals("https://cdn.example.com/cover.png", after.cover_image_url)
        assertEquals("/data/covers/local.png", after.cover_image)
        assertEquals(1L, articles.count())
    }

    @Test
    fun failuresReportStableErrorKeys() = runBlocking {
        val brokenDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val brokenRepo = ArticleRepository(DailySatoriDatabase(brokenDriver))
        brokenDriver.close()
        val vm = ShareDialogViewModel(
            articleRepo = brokenRepo,
            tagRepo = null,
            scheduler = null,
            parserService = null,
            ioDispatcher = ioExecutor.asCoroutineDispatcher(),
            mainDispatcher = mainExecutor.asCoroutineDispatcher(),
        )

        vm.initialize(SHARED_URL)
        val loadFailed = withTimeout(5_000) { vm.state.first { it.error != null && !it.isInitializing } }
        assertEquals("share.load_failed", loadFailed.error, "查询失败必须使用稳定 i18n key")

        vm.onTitleChanged("标题")
        vm.save { }
        val saveFailed = withTimeout(5_000) { vm.state.first { it.error == "share.save_failed" } }
        assertEquals("share.save_failed", saveFailed.error, "保存失败必须使用稳定 i18n key")
    }

    private companion object {
        const val SHARED_URL = "https://example.com/shared-article"
        const val MAIN_THREAD = "share-main"
        const val IO_THREAD = "share-io"
    }
}
