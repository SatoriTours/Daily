package com.dailysatori.core.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.EventListener
import coil3.Extras
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import com.dailysatori.service.diagnostics.*

internal val imagePerformanceOwner = Extras.Key<DiagnosticTrace?>(null)

/** Uses request extras, not data or memory cache key; changing visits doesn't change what is fetched. */
@Composable
internal fun performanceImageModel(model: Any?): Any? {
    val owner = LocalUiVisit.current?.trace ?: return model
    val context = LocalContext.current
    return remember(model, owner, context) {
        val builder = (model as? ImageRequest)?.newBuilder() ?: ImageRequest.Builder(context).data(model)
        builder.extras[imagePerformanceOwner] = owner
        builder.build()
    }
}

internal class ImagePerformanceListener(
    private val owner: DiagnosticTrace?,
    private val budget: PerformanceBudget,
) : EventListener() {
    private var began = DiagnosticLog.elapsed()
    private var finished = false
    private var decodeStart: Long? = null
    private var decodedMs: Long? = null

    override fun onStart(request: ImageRequest) { began = DiagnosticLog.elapsed() }

    override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) {
        decodeStart = DiagnosticLog.elapsed()
    }

    override fun decodeEnd(request: ImageRequest, decoder: Decoder, options: Options, result: DecodeResult?) {
        val start = decodeStart ?: return
        decodedMs = (decodedMs ?: 0) + (DiagnosticLog.elapsed() - start)
        decodeStart = null
    }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) = finish(PerformanceResult.OK, result.dataSource.name)
    override fun onError(request: ImageRequest, result: ErrorResult) = finish(PerformanceResult.FAILED)
    override fun onCancel(request: ImageRequest) = finish(PerformanceResult.CANCELLED)

    private fun finish(result: PerformanceResult, source: String? = null) {
        if (finished) return
        finished = true
        val end = DiagnosticLog.elapsed()
        if (!budget.allow(end, end - began, result != PerformanceResult.OK)) return
        val requestTrace = DiagnosticTrace(DiagnosticLog.newId())
        val trace = owner ?: requestTrace
        PerformanceSpan(PerformancePoint.IMAGE_LOAD, began, trace, clock = { end }).finish(result, source, requestTrace)
        val interruptedDecode = decodeStart
        val observed = decodedMs ?: if (interruptedDecode != null) 0L else return
        val elapsed = observed + (interruptedDecode?.let { end - it } ?: 0)
        PerformanceSpan(PerformancePoint.IMAGE_DECODE, end - elapsed, trace, clock = { end })
            .finish(if (interruptedDecode != null) result else PerformanceResult.OK, source, requestTrace)
    }
}
