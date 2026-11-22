package com.dailysatori.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser

internal val SmartReadingIcon: ImageVector by lazy {
    val tintMask = SolidColor(Color.Black)
    // The approved “new” paths in ai-read-wen-spacing.svg, in their original 24 × 24 coordinates.
    ImageVector.Builder("SmartReading", IconSize.l, IconSize.l, 24f, 24f)
        .addPath(
            pathData = PathParser().parsePathString("M8.25 5.2l1.3 1.9M2.7 9.7h12.7M12.7 10c-.9 4.4-4.1 8.3-10 10.8M5.3 12c2 3.4 4.9 6.6 10.4 8.8").toNodes(),
            stroke = tintMask,
            strokeLineWidth = 1.65f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .addPath(
            pathData = PathParser().parsePathString("M19.7 1.6Q20.3 4.5 23 5.1q-2.7.65-3.3 3.5Q19.1 5.75 16.4 5.1q2.7-.6 3.3-3.5z").toNodes(),
            fill = tintMask,
        )
        .build()
}
