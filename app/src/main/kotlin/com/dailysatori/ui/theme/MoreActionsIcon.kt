package com.dailysatori.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path

val MoreActionsIcon: ImageVector by lazy {
    ImageVector.Builder("MoreActions", IconSize.l, IconSize.l, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            for (center in listOf(5f, 12f, 19f)) {
                moveTo(center - 1.8f, 12f)
                arcTo(1.8f, 1.8f, 0f, false, true, center + 1.8f, 12f)
                arcTo(1.8f, 1.8f, 0f, false, true, center - 1.8f, 12f)
                close()
            }
        }
    }.build()
}
