package com.dailysatori.ui.feature.settings.remotenews

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deleting a subscription removes only the source and its sync records; downloaded articles are kept.
 * The confirmation must say exactly that (it must never promise a cleanup the code does not perform),
 * and the stored range of an existing source is labelled as a first-import range.
 */
class RemoteNewsHistoryCopyContractTest {
    @Test
    fun theDeleteConfirmationMatchesWhatIsReallyDeleted() {
        val zh = File("src/main/res/values/settings_interactions.xml").readText()
        val en = File("src/main/res/values-en/settings_interactions.xml").readText()

        assertTrue(
            zh.contains("将删除订阅“%1\$s”及其同步记录，之后不再从此来源同步。已导入的文章数据不会删除。"),
            "the Chinese confirmation states the real behaviour",
        )
        assertFalse(zh.contains("一并移除"), "no promise of removing articles")
        assertTrue(en.contains("and its sync records"), en)
        assertTrue(en.contains("Downloaded article data is kept"), en)
        assertFalse(en.contains("will be removed"), "no promise of removing articles")
    }

    @Test
    fun theExistingSourceRangeIsLabelledAsTheFirstImportRange() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsScreen.kt").readText()
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsViewModel.kt").readText()

        assertTrue(screen.contains("\"首次导入范围\" to remoteNewsSourceHistoryRangeText(source)"))
        assertTrue(screen.contains("Text(\"首次导入范围\""))
        assertFalse(screen.contains("\"导入范围\" to"), "the card detail never claims a permanent range")
        // The label is wording only: the stored first-import policy and the explicit full backfill stay.
        assertTrue(viewModel.contains("RemoteFeedHistoryStrategy.ALL_HISTORY, nowMillis()"))
    }
}

