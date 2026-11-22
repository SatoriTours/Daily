package com.dailysatori.ui.feature.settings.taskcenter

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.dailysatori.ui.theme.DailySatoriTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class)
class TaskCenterBulkCancelDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp-xhdpi")
    fun previewDisplaysCountsAndBackDoesNotConfirm() {
        val events = mutableListOf<String>()
        showPreview(events, dark = false)
        compose.onNodeWithText("排队中：300 · 运行中：7 · 重试中：12", substring = true).assertIsDisplayed()
        compose.onNodeWithText("确认取消").assertIsDisplayed()
        screenshot("confirm-zh-light")
        compose.onNodeWithText("返回").performClick()
        compose.runOnIdle { assertEquals(listOf("back"), events) }
    }

    @Test
    @Config(qualifiers = "en-rUS-w320dp-h640dp-xhdpi")
    fun longEnglishCopyLeavesBothActionsAvailableInDarkTheme() {
        val events = mutableListOf<String>()
        showPreview(events, dark = true)
        compose.onNodeWithText("History, existing results, and future schedules will be kept.", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Go back").assertIsDisplayed()
        screenshot("confirm-en-dark")
        compose.onNodeWithText("Confirm cancellation").performClick()
        compose.runOnIdle { assertEquals(listOf("confirm"), events) }
    }

    private fun showPreview(events: MutableList<String>, dark: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                DailySatoriTheme(darkTheme = dark) {
                    TaskCenterBulkCancelDialog(
                        TaskCenterBulkCancelState.Confirm(setOf(1L), queued = 300, running = 7, retrying = 12),
                        onDismiss = { events += "back" },
                        onConfirm = { events += "confirm" },
                    )
                }
            }
        }
    }

    private fun screenshot(name: String) {
        val dialog = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File("build/test-screenshots/task-center", "$name.png")
        output.parentFile.mkdirs()
        output.outputStream().use { assertTrue(dialog.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
