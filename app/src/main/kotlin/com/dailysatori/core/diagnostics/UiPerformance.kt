package com.dailysatori.core.diagnostics

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalView
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.dailysatori.service.diagnostics.*

internal val LocalUiVisit = staticCompositionLocalOf<UiVisit?> { null }

internal class UiVisit(
    val screen: PerformanceScreen,
    private val parent: UiVisit? = null,
    private val expectsContent: Boolean = true,
    began: Long = DiagnosticLog.elapsed(),
    sink: Diagnostics = DiagnosticLog.diagnostics,
    clock: () -> Long = DiagnosticLog::elapsed,
) {
    private var handedOff = false
    fun transferToDialog() { handedOff = true }
    fun navigationEnded() { if (!handedOff) cancel() }

    val trace = DiagnosticTrace(DiagnosticLog.newId(), screen = screen)
    private val draw = PerformanceSpan(PerformancePoint.UI_FIRST_DRAW, began, trace, screen, sink, clock)
    private val content = PerformanceSpan(PerformancePoint.UI_CONTENT_DRAW, began, trace, screen, sink, clock)
    var firstPending = true
        private set
    var contentPending = expectsContent
        private set

    fun firstDraw() {
        if (!firstPending) return
        firstPending = false
        draw.finish()
    }

    fun contentDraw(result: PerformanceResult, dataTrace: DiagnosticTrace? = null) {
        firstDraw()
        if (!contentPending) return
        contentPending = false
        content.finish(result, dataTrace = dataTrace)
        parent?.contentDraw(result, dataTrace)
    }

    fun cancel() {
        if (firstPending) draw.finish(PerformanceResult.CANCELLED)
        if (contentPending) content.finish(PerformanceResult.CANCELLED)
        firstPending = false
        contentPending = false
    }
}

internal class NavigationPerformance {
    private val active = mutableStateOf<Pair<String, UiVisit>?>(null)

    fun enter(entry: NavBackStackEntry?) {
        if (active.value?.first == entry?.id) return
        active.value?.second?.navigationEnded()
        val route = entry?.destination?.route.orEmpty().substringBefore('/').substringBefore('?').substringAfterLast('.')
        val screen = routeScreens[route]
        active.value = if (screen != null && entry != null) {
            entry.id to UiVisit(screen)
        } else null
    }

    fun visit(entry: NavBackStackEntry): UiVisit? = active.value?.takeIf { it.first == entry.id }?.second

    private companion object {
        val routeScreens = PerformanceScreen.entries.filter { it.name.endsWith("Route") }.associateBy { it.name }
    }
}

@Composable
internal fun rememberNavigationPerformance(controller: NavController): NavigationPerformance {
    val performance = remember(controller) { NavigationPerformance() }
    DisposableEffect(controller) {
        val listener = NavController.OnDestinationChangedListener { _, _, _ ->
            performance.enter(controller.currentBackStackEntry)
        }
        controller.addOnDestinationChangedListener(listener)
        onDispose {
            controller.removeOnDestinationChangedListener(listener)
            performance.enter(null)
        }
    }
    return performance
}

internal inline fun <reified R : Any> NavGraphBuilder.measuredDestination(
    performance: NavigationPerformance,
    noinline enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    noinline exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    noinline popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = enterTransition,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable<R>(enterTransition = enterTransition, exitTransition = exitTransition,
    popEnterTransition = popEnterTransition) { entry ->
    MeasuredUi(performance.visit(entry)) { content(entry) }
}

@Composable
internal fun rememberUiVisit(screen: PerformanceScreen, key: Any = screen, expectsContent: Boolean = true): UiVisit? {
    val parent = LocalUiVisit.current
    val visit = remember(parent, screen, key) { parent?.let { UiVisit(screen, it, expectsContent) } }
    DisposableEffect(visit) { onDispose { visit?.cancel() } }
    return visit
}

@Composable
internal fun MeasuredUi(visit: UiVisit?, content: @Composable () -> Unit) {
    val view = LocalView.current
    CompositionLocalProvider(LocalUiVisit provides visit) {
        Box(Modifier.drawWithContent {
            drawContent()
            if (visit?.firstPending == true && view.isShown && size.width > 0 && size.height > 0) visit.firstDraw()
        }, propagateMinConstraints = true) { content() }
    }
}

@Composable
internal fun Modifier.performanceContent(
    result: PerformanceResult? = PerformanceResult.OK,
    dataTrace: DiagnosticTrace? = null,
): Modifier {
    val visit = LocalUiVisit.current ?: return this
    if (result == null) return this
    val view = LocalView.current
    return drawWithContent {
        drawContent()
        if (visit.contentPending && view.isShown && size.width > 0 && size.height > 0) visit.contentDraw(result, dataTrace)
    }
}

@Composable
internal fun Modifier.performanceTab(
    screen: PerformanceScreen,
    result: PerformanceResult?,
    key: Any = screen,
): Modifier {
    val visit = rememberUiVisit(screen, key) ?: return this
    val view = LocalView.current
    return drawWithContent {
        drawContent()
        if ((visit.firstPending || visit.contentPending && result != null) &&
            view.isShown && size.width > 0 && size.height > 0) {
            visit.firstDraw()
            result?.let { visit.contentDraw(it) }
        }
    }
}
