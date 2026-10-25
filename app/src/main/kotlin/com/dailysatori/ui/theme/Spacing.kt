package com.dailysatori.ui.theme

import androidx.compose.ui.unit.dp

object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object Radius {
    val none = 0.dp
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val inset = 10.dp
    val card = 16.dp
    val l = 22.dp
    val xl = 24.dp
    val circular = 100.dp
}

object IconSize {
    val xs = 16.dp
    val s = 18.dp
    val m = 20.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object Height {
    val button = 46.dp
    val buttonSmall = 34.dp
    val input = 46.dp
    val listItem = 54.dp
    val listItemSmall = 46.dp
    val appBar = 54.dp
    val appBarCompact = 48.dp
    val navBar = 52.dp
    val chip = 30.dp
    val searchBar = 46.dp
    val touchTarget = 48.dp
}

/** Shared proportions for compact floating controls, separate from their accessible hit targets. */
object FloatingControl {
    val visual = 36.dp
    val edgeReveal = 14.dp
    val gripWidth = 3.dp
    val gripHeight = 12.dp
    val bottomClearance = Height.navBar + Spacing.m
    val scrollClearance = bottomClearance + Height.touchTarget + Spacing.l
}

object BorderWidth {
    val xs = 0.5.dp
    val s = 1.dp
    val m = 1.5.dp
    val l = 2.dp
    val xl = 4.dp
}

object Breakpoint {
    val mobile = 600.dp
    val tablet = 900.dp
    val desktop = 1200.dp
}

object Anim {
    const val durationFast = 150
    const val durationNormal = 300
    const val durationSlow = 500
}
