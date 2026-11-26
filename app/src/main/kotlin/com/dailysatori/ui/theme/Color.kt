package com.dailysatori.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Daily Satori 「素纸墨青 · Calm Editorial」主题色板。
 * 浅色：暖宣纸 + 炭墨 + 靛青主色 + 赭石机会强调 + 松石完成色；
 * 深色：夜读墨砚 + 月白 + 浅靛青 + 暖赭石 + 夜松石。
 */
object AppColors {
    // 品牌与语义强调色
    val editorialTeal = Color(0xFF255B7A)
    val editorialTealDark = Color(0xFF68B3D8)
    val editorialTerracotta = Color(0xFFB85D3B)
    val editorialTerracottaDark = Color(0xFFE59172)
    val editorialSage = Color(0xFF2F7A5A)
    val editorialSageDark = Color(0xFF64C49A)
    val editorialVermilion = Color(0xFFC94136)
    val editorialVermilionDark = Color(0xFFE86B61)

    // 浅色 · 宣纸色阶
    val paperLightBackground = Color(0xFFF7F5F0)
    val paperLightSurface = Color(0xFFFFFDF9)
    val paperLightSurfaceLow = Color(0xFFFAF8F3)
    val paperLightSurfaceContainer = Color(0xFFEFECE4)
    val paperLightSurfaceHigh = Color(0xFFEAE6DC)
    val paperLightSurfaceHighest = Color(0xFFE5E0D5)
    val paperLightOnSurface = Color(0xFF1A1917)
    val paperLightOnSurfaceVariant = Color(0xFF6F6A60)
    val paperLightOutline = Color(0x161A1917)
    val paperLightOutlineHigh = Color(0x241A1917)

    val paperLightPrimaryRole = editorialTeal
    val paperLightOnPrimaryRole = Color(0xFFFFFDF9)
    val paperLightPrimaryContainer = Color(0xFFE3EFF5)
    val paperLightOnPrimaryContainer = Color(0xFF19435C)
    val paperLightSecondaryRole = editorialTerracotta
    val paperLightOnSecondaryRole = Color(0xFFFFFDF9)
    val paperLightSecondaryContainer = Color(0xFFF7E9E3)
    val paperLightOnSecondaryContainer = Color(0xFF8F4226)
    val paperLightTertiaryRole = editorialSage
    val paperLightOnTertiaryRole = Color(0xFFFFFDF9)
    val paperLightTertiaryContainer = Color(0xFFE4F2EC)
    val paperLightOnTertiaryContainer = Color(0xFF1D523B)
    val paperLightErrorContainer = Color(0xFFFCE6E4)
    val paperLightOnErrorContainer = Color(0xFF6E1C15)

    // 深色 · 夜读墨砚色阶
    val obsidianDarkBackground = Color(0xFF111110)
    val obsidianDarkSurface = Color(0xFF1A1917)
    val obsidianDarkSurfaceLow = Color(0xFF151412)
    val obsidianDarkSurfaceContainer = Color(0xFF242320)
    val obsidianDarkSurfaceHigh = Color(0xFF2A2825)
    val obsidianDarkSurfaceHighest = Color(0xFF2F2D29)
    val obsidianDarkOnSurface = Color(0xFFECE9E2)
    val obsidianDarkOnSurfaceVariant = Color(0xFF9A958B)
    val obsidianDarkOutline = Color(0x1AECE9E2)
    val obsidianDarkOutlineHigh = Color(0x29ECE9E2)

    val obsidianDarkPrimaryRole = editorialTealDark
    val obsidianDarkOnPrimaryRole = Color(0xFF0E1D26)
    val obsidianDarkPrimaryContainer = Color(0xFF163344)
    val obsidianDarkOnPrimaryContainer = Color(0xFFB9E1F5)
    val obsidianDarkSecondaryRole = editorialTerracottaDark
    val obsidianDarkOnSecondaryRole = Color(0xFF2E140B)
    val obsidianDarkSecondaryContainer = Color(0xFF382119)
    val obsidianDarkOnSecondaryContainer = Color(0xFFF7C3AE)
    val obsidianDarkTertiaryRole = editorialSageDark
    val obsidianDarkOnTertiaryRole = Color(0xFF0E291D)
    val obsidianDarkTertiaryContainer = Color(0xFF183328)
    val obsidianDarkOnTertiaryContainer = Color(0xFFBBE8D3)
    val obsidianDarkErrorRole = editorialVermilionDark
    val obsidianDarkErrorContainer = Color(0xFF4A1D19)
    val obsidianDarkOnErrorContainer = Color(0xFFFFDAD6)

    // 兼容既有命名别名（平滑映射到素纸墨青体系）
    val iosBlue = editorialTeal
    val iosBlueDark = editorialTealDark
    val iosGreen = editorialSage
    val iosRed = editorialVermilion
    val iosOrange = editorialTerracotta
    val iosPurple = editorialTeal

    val iosLightBackground = paperLightBackground
    val iosLightSurface = paperLightSurface
    val iosLightSurfaceHigh = paperLightSurfaceContainer
    val iosLightSurfaceHighest = paperLightSurfaceHighest
    val iosLightOnSurface = paperLightOnSurface
    val iosLightOnSurfaceVariant = paperLightOnSurfaceVariant
    val iosLightOutline = paperLightOutline
    val iosLightOutlineHigh = paperLightOutlineHigh
    val iosLightPrimaryRole = paperLightPrimaryRole
    val iosLightOnPrimaryRole = paperLightOnPrimaryRole
    val iosLightPrimaryContainer = paperLightPrimaryContainer
    val iosLightOnPrimaryContainer = paperLightOnPrimaryContainer
    val iosLightSecondaryRole = paperLightSecondaryRole
    val iosLightOnSecondaryRole = paperLightOnSecondaryRole
    val iosLightSecondaryContainer = paperLightSecondaryContainer
    val iosLightOnSecondaryContainer = paperLightOnSecondaryContainer
    val iosLightTertiaryRole = paperLightTertiaryRole
    val iosLightOnTertiaryRole = paperLightOnTertiaryRole
    val iosLightTertiaryContainer = paperLightTertiaryContainer
    val iosLightErrorContainer = paperLightErrorContainer
    val iosLightOnErrorContainer = paperLightOnErrorContainer
    val iosLightSurfaceContainerLow = paperLightSurfaceLow

    val iosDarkBackground = obsidianDarkBackground
    val iosDarkSurface = obsidianDarkSurface
    val iosDarkSurfaceHigh = obsidianDarkSurfaceContainer
    val iosDarkSurfaceHighest = obsidianDarkSurfaceHighest
    val iosDarkOnSurface = obsidianDarkOnSurface
    val iosDarkOnSurfaceVariant = obsidianDarkOnSurfaceVariant
    val iosDarkOutline = obsidianDarkOutline
    val iosDarkOutlineHigh = obsidianDarkOutlineHigh
    val iosDarkPrimaryRole = obsidianDarkPrimaryRole
    val iosDarkOnPrimaryRole = obsidianDarkOnPrimaryRole
    val iosDarkPrimaryContainer = obsidianDarkPrimaryContainer
    val iosDarkOnPrimaryContainer = obsidianDarkOnPrimaryContainer
    val iosDarkSecondaryRole = obsidianDarkSecondaryRole
    val iosDarkOnSecondaryRole = obsidianDarkOnSecondaryRole
    val iosDarkSecondaryContainer = obsidianDarkSecondaryContainer
    val iosDarkOnSecondaryContainer = obsidianDarkOnSecondaryContainer
    val iosDarkTertiaryRole = obsidianDarkTertiaryRole
    val iosDarkOnTertiaryRole = obsidianDarkOnTertiaryRole
    val iosDarkTertiaryContainer = obsidianDarkTertiaryContainer
    val iosDarkErrorRole = obsidianDarkErrorRole
    val iosDarkErrorContainer = obsidianDarkErrorContainer
    val iosDarkOnErrorContainer = obsidianDarkOnErrorContainer
    val iosDarkSurfaceContainerLow = obsidianDarkSurfaceLow

    val primary = editorialTeal
    val onAccent = paperLightOnPrimaryRole
    val primaryLight = editorialTealDark
    val background = obsidianDarkBackground
    val surface = obsidianDarkSurface
    val surfaceContainer = obsidianDarkSurfaceContainer
    val surfaceContainerHighest = obsidianDarkSurfaceHighest
    val onBackground = obsidianDarkOnSurface
    val onSurface = obsidianDarkOnSurface
    val onSurfaceVariant = obsidianDarkOnSurfaceVariant
    val outline = obsidianDarkOutline
    val outlineVariant = obsidianDarkOutlineHigh

    val success = editorialSage
    val error = editorialVermilion
    val warning = editorialTerracotta
    val info = editorialTeal

    val secondary = editorialTerracotta
    val secondaryContainer = paperLightSecondaryContainer
    val onSecondaryContainer = paperLightOnSecondaryContainer
    val tertiaryContainer = paperLightTertiaryContainer

    // 低饱和矿物色系标签，兼容宣纸与墨砚背景
    val tagColors = listOf(
        Color(0xFF255B7A), Color(0xFFB85D3B), Color(0xFF2F7A5A),
        Color(0xFF6B5B8A), Color(0xFF8C6B3F), Color(0xFF3E6B6B),
        Color(0xFF9E5252), Color(0xFF4D6A88), Color(0xFF7B6B58),
        Color(0xFF56735B),
    )
}
