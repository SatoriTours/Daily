package com.dailysatori.ui.feature.article

import com.dailysatori.service.articleai.ArticleBodyKind
import com.dailysatori.service.parser.ArticleBodyMissingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArticleRefreshPolicyTest {
    @Test
    fun previewOnlyOrEmptyFetchIsReportedAndKeepsTheStoredContent() {
        val error = assertFailsWith<ArticleBodyMissingException> { refreshedRemoteArticleBody(null) }
        assertTrue(error.message!!.contains("保留现有内容"))
        assertTrue(error.message!!.contains("AI 处理"))
        assertFailsWith<ArticleBodyMissingException> {
            refreshedRemoteArticleBody("# Access denied\nPlease verify you are human")
        }
    }

    @Test
    fun completeFetchedBodyIsAcceptedWithItsOwnFingerprint() {
        val body = refreshedRemoteArticleBody(
            "A complete article paragraph.\n\nA second paragraph with more details.",
        )
        assertEquals(ArticleBodyKind.BODY, body.kind)
        assertTrue(body.hash.isNotBlank())
    }
}
