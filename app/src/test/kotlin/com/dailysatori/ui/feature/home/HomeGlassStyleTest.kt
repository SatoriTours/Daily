package com.dailysatori.ui.feature.home

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeGlassStyleTest {
    @Test
    fun transparentTabContentCannotRevealSharpTextBehindTheBlur() {
        listOf(lightColorScheme(), darkColorScheme()).forEach { colors ->
            val style = homeBottomBarHazeStyle(colors.surface)
            assertEquals(1f, style.backgroundColor.alpha, "模糊内容后必须有不透明底色，不能让原始文字穿透")
            assertEquals(colors.surface, style.backgroundColor)
            assertTrue(style.blurRadius.value > 0f, "仍然保留毛玻璃模糊，不是用实色遮罩替代")
            assertTrue(style.tints.isNotEmpty())
        }
    }
}
