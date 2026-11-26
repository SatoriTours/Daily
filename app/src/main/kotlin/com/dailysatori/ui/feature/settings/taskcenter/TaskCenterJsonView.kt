package com.dailysatori.ui.feature.settings.taskcenter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

internal const val TASK_CENTER_MAX_FORMAT_CHARS = 512_000

@OptIn(ExperimentalSerializationApi::class)
private val taskCenterPrettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

private val taskCenterJsonOffsetPattern = Regex("""offset (\d+)""", RegexOption.IGNORE_CASE)
private val taskCenterJsonNumberPattern = Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?""")

/**
 * How a captured field should be rendered. Only an explicit "[omitted]" marker counts as "not
 * captured"; a blank field is genuinely empty and must not be guessed into "not captured".
 */
internal enum class TaskCenterFieldState { Recorded, Empty, NotCollected }

internal fun taskCenterFieldState(raw: String): TaskCenterFieldState = when {
    raw.isBlank() -> TaskCenterFieldState.Empty
    raw.trim() == "[omitted]" -> TaskCenterFieldState.NotCollected
    else -> TaskCenterFieldState.Recorded
}

internal data class TaskCenterJsonDocument(
    val raw: String,
    val text: String,
    val formatted: Boolean,
    val errorMessage: String? = null,
    val errorOffset: Int? = null,
    val isJsonLike: Boolean = false,
)

/**
 * Pretty prints JSON payloads only. Plain logs, prose and loose numbers are returned verbatim
 * without a JSON error; only text that really looks like JSON is diagnosed, with the offset of
 * the real damage.
 */
internal fun taskCenterJsonDocument(
    raw: String,
    maxFormatChars: Int = TASK_CENTER_MAX_FORMAT_CHARS,
): TaskCenterJsonDocument {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return TaskCenterJsonDocument(raw, raw, formatted = false)
    if (raw.length > maxFormatChars) {
        return TaskCenterJsonDocument(
            raw = raw,
            text = raw,
            formatted = false,
            errorMessage = "oversized:${raw.length}",
        )
    }
    val jsonLike = trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")
    val jsonScalar = trimmed == "true" || trimmed == "false" || trimmed == "null" ||
        taskCenterJsonNumberPattern.matches(trimmed)
    if (!jsonLike && !jsonScalar) {
        return TaskCenterJsonDocument(raw, raw, formatted = false)
    }
    return runCatching {
        val pretty = taskCenterPrettyJson.encodeToString(Json.parseToJsonElement(trimmed))
        TaskCenterJsonDocument(
            raw = raw,
            text = pretty,
            formatted = pretty != raw,
            isJsonLike = jsonLike,
        )
    }.getOrElse { error ->
        if (!jsonLike) {
            TaskCenterJsonDocument(raw, raw, formatted = false)
        } else {
            val message = error.cause?.message ?: error.message ?: "invalid"
            val firstLine = message.lineSequence().firstOrNull { it.isNotBlank() } ?: "invalid"
            TaskCenterJsonDocument(
                raw = raw,
                text = raw,
                formatted = false,
                errorMessage = firstLine,
                errorOffset = taskCenterJsonOffsetPattern.find(firstLine)?.groupValues?.getOrNull(1)?.toIntOrNull(),
                isJsonLike = true,
            )
        }
    }
}

internal fun taskCenterFormatJson(body: String): String = taskCenterJsonDocument(body).text

internal data class TaskCenterJsonFold(val startLine: Int, val endLine: Int)

internal fun taskCenterJsonFolds(lines: List<String>): Map<Int, TaskCenterJsonFold> {
    val openers = ArrayDeque<Int>()
    val folds = LinkedHashMap<Int, TaskCenterJsonFold>()
    lines.forEachIndexed { index, line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEachIndexed
        when {
            trimmed.endsWith("{") || trimmed.endsWith("[") -> openers.addLast(index)
            trimmed.startsWith("}") || trimmed.startsWith("]") -> {
                val start = openers.removeLastOrNull() ?: return@forEachIndexed
                if (index > start) folds[start] = TaskCenterJsonFold(start, index)
            }
        }
    }
    return folds
}

internal data class TaskCenterJsonVisibleLine(
    val number: Int,
    val text: String,
    val foldEndLine: Int?,
    val hiddenLineCount: Int,
)

internal fun taskCenterJsonVisibleLines(
    lines: List<String>,
    collapsed: Set<Int>,
): List<TaskCenterJsonVisibleLine> {
    val folds = taskCenterJsonFolds(lines)
    val visible = ArrayList<TaskCenterJsonVisibleLine>(lines.size)
    var index = 0
    while (index < lines.size) {
        val fold = folds[index]
        if (fold != null && index in collapsed) {
            visible += TaskCenterJsonVisibleLine(index, lines[index], fold.endLine, fold.endLine - index)
            index = fold.endLine + 1
        } else {
            visible += TaskCenterJsonVisibleLine(index, lines[index], null, 0)
            index += 1
        }
    }
    return visible
}

internal enum class TaskCenterJsonTokenType { Key, Text, Number, Literal, Punctuation }

internal data class TaskCenterJsonToken(
    val type: TaskCenterJsonTokenType,
    val text: String,
    val start: Int,
)

internal fun taskCenterJsonTokens(line: String): List<TaskCenterJsonToken> {
    val tokens = ArrayList<TaskCenterJsonToken>()
    var index = 0
    while (index < line.length) {
        val char = line[index]
        when {
            char == '"' -> {
                val end = taskCenterJsonStringEnd(line, index)
                val raw = line.substring(index, end)
                val isKey = line.substring(end).trimStart().startsWith(":")
                tokens += TaskCenterJsonToken(
                    type = if (isKey) TaskCenterJsonTokenType.Key else TaskCenterJsonTokenType.Text,
                    text = raw,
                    start = index,
                )
                index = end
            }
            char.isDigit() || (char == '-' && index + 1 < line.length && line[index + 1].isDigit()) -> {
                val start = index
                index += 1
                while (index < line.length && (line[index].isDigit() || line[index] in ".eE+-")) index += 1
                tokens += TaskCenterJsonToken(TaskCenterJsonTokenType.Number, line.substring(start, index), start)
            }
            char.isLetter() -> {
                val start = index
                while (index < line.length && line[index].isLetter()) index += 1
                tokens += TaskCenterJsonToken(TaskCenterJsonTokenType.Literal, line.substring(start, index), start)
            }
            char in "{}[],:" -> {
                tokens += TaskCenterJsonToken(TaskCenterJsonTokenType.Punctuation, char.toString(), index)
                index += 1
            }
            else -> index += 1
        }
    }
    return tokens
}

private fun taskCenterJsonStringEnd(line: String, start: Int): Int {
    var index = start + 1
    while (index < line.length) {
        when {
            line[index] == '\\' -> index += 2
            line[index] == '"' -> return index + 1
            else -> index += 1
        }
    }
    return line.length
}

internal fun taskCenterJsonMatchLines(lines: List<String>, query: String): List<Int> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return lines.indices.filter { lines[it].contains(needle, ignoreCase = true) }
}

/**
 * Full payload viewer: pretty printed by default, line numbers, foldable objects, search, raw
 * toggle, full screen and copy. Blank and not-captured fields show their own honest text.
 */
@Composable
internal fun TaskCenterPayloadViewer(
    title: String,
    body: String,
    emptyText: String,
    uncollectedText: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 300.dp,
) {
    val copy = taskCenterCopy()
    val state = remember(body) { taskCenterFieldState(body) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        when (state) {
            TaskCenterFieldState.Empty -> TaskCenterViewerNotice(title, emptyText)
            TaskCenterFieldState.NotCollected -> TaskCenterViewerNotice(title, uncollectedText)
            TaskCenterFieldState.Recorded -> TaskCenterRecordedPayload(
                title = title,
                body = body,
                copy = copy,
                maxHeight = maxHeight,
            )
        }
    }
}

@Composable
private fun TaskCenterViewerNotice(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TaskCenterRecordedPayload(
    title: String,
    body: String,
    copy: TaskCenterCopy,
    maxHeight: Dp,
) {
    val clipboard = LocalClipboardManager.current
    var rawMode by remember(body) { mutableStateOf(false) }
    var searchOpen by remember(body) { mutableStateOf(false) }
    var query by remember(body) { mutableStateOf("") }
    var fullScreen by remember(body) { mutableStateOf(false) }
    val collapsed = remember(body) { mutableStateOf(setOf<Int>()) }
    val document by produceState(
        initialValue = TaskCenterJsonDocument(body, body, formatted = false),
        body,
    ) {
        value = withContext(Dispatchers.Default) { taskCenterJsonDocument(body) }
    }
    val displayText = if (rawMode || !document.formatted) document.raw else document.text
    val lines = remember(displayText) { bodyDisplayLines(displayText) }
    val visible = remember(lines, collapsed.value) { taskCenterJsonVisibleLines(lines, collapsed.value) }
    val matches = remember(lines, query) { taskCenterJsonMatchLines(lines, query) }
    val folds = remember(lines) { taskCenterJsonFolds(lines) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                "${title} · ${taskCenterSizeText(body.length, copy)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (rawMode || !document.formatted) copy.viewerRaw else copy.viewerFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            if (document.formatted) {
                TaskCenterViewerAction(if (rawMode) copy.viewerFormatted else copy.viewerRaw) { rawMode = !rawMode }
            }
            TaskCenterViewerAction(copy.viewerSearch) {
                searchOpen = !searchOpen
                if (!searchOpen) query = ""
            }
            if (folds.isNotEmpty()) {
                TaskCenterViewerAction(if (collapsed.value.isEmpty()) copy.viewerFoldAll else copy.viewerUnfoldAll) {
                    collapsed.value = if (collapsed.value.isEmpty()) folds.keys else emptySet()
                }
            }
            TaskCenterViewerAction(copy.viewerFullscreen) { fullScreen = true }
            TaskCenterViewerAction(copy.viewerCopy) { clipboard.setText(AnnotatedString(displayText)) }
        }
        if (searchOpen) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                placeholder = { Text(copy.viewerSearchHint, style = MaterialTheme.typography.bodySmall) },
                trailingIcon = {
                    Text(
                        if (query.isBlank()) "" else String.format(copy.viewerMatches, matches.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Spacing.s),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (document.errorMessage != null && document.isJsonLike) {
            TaskCenterViewerNote(
                if (document.errorOffset != null) {
                    String.format(copy.viewerInvalidWithOffset, document.errorMessage, document.errorOffset)
                } else {
                    String.format(copy.viewerInvalid, document.errorMessage)
                },
            )
        } else if (document.errorMessage != null) {
            TaskCenterViewerNote(String.format(copy.viewerOversized, body.length))
        }
        TaskCenterCodeLines(
            visible = visible,
            matches = matches.toSet(),
            collapsed = collapsed.value,
            folds = folds,
            copy = copy,
            onToggleFold = { start ->
                collapsed.value = if (start in collapsed.value) collapsed.value - start else collapsed.value + start
            },
            modifier = Modifier.heightIn(max = maxHeight),
        )
        if (fullScreen) {
            Dialog(onDismissRequest = { fullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(Spacing.m),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { fullScreen = false }) { Text(copy.close) }
                        }
                        Text(
                            "${taskCenterSizeText(body.length, copy)} · " +
                                (if (rawMode || !document.formatted) copy.viewerRawContent else copy.viewerFormattedJson) +
                                " · " + copy.viewerAutoWrap,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            TaskCenterCodeLines(
                                visible = visible,
                                matches = matches.toSet(),
                                collapsed = collapsed.value,
                                folds = folds,
                                copy = copy,
                                onToggleFold = { start ->
                                    collapsed.value = if (start in collapsed.value) collapsed.value - start else collapsed.value + start
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCenterViewerAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.xs))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.s, vertical = Spacing.xxs),
    )
}

@Composable
private fun TaskCenterViewerNote(text: String) {
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(com.dailysatori.ui.theme.BorderWidth.xs, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun TaskCenterCodeLines(
    visible: List<TaskCenterJsonVisibleLine>,
    matches: Set<Int>,
    collapsed: Set<Int>,
    folds: Map<Int, TaskCenterJsonFold>,
    copy: TaskCenterCopy,
    onToggleFold: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(matches) {
        matches.firstOrNull()?.let { line ->
            val target = visible.indexOfFirst { it.number == line }
            if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
        }
    }
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(com.dailysatori.ui.theme.BorderWidth.xs, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
            ) {
                items(visible, key = { it.number }) { line ->
                    val matched = line.number in matches
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (matched) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else androidx.compose.ui.graphics.Color.Transparent,
                            )
                            .padding(horizontal = Spacing.s),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Text(
                            (line.number + 1).toString().padStart(2, '0'),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.widthIn(min = 22.dp),
                        )
                        if (line.foldEndLine != null) {
                            Text(
                                String.format(copy.viewerFoldHint, line.hiddenLineCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.xxs))
                                    .clickable { onToggleFold(line.number) }
                                    .padding(horizontal = Spacing.xs),
                            )
                        } else if (line.number in folds) {
                            Text(
                                "⌄",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.xxs))
                                    .clickable { onToggleFold(line.number) }
                                    .padding(horizontal = Spacing.xxs),
                            )
                        } else {
                            Text(" ", style = MaterialTheme.typography.labelSmall, modifier = Modifier.widthIn(min = 10.dp))
                        }
                        Text(
                            text = taskCenterHighlightJson(line.text),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            softWrap = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun taskCenterHighlightJson(line: String): AnnotatedString = taskCenterJsonAnnotatedLine(
    line = line,
    keyColor = MaterialTheme.colorScheme.primary,
    textColor = MaterialTheme.colorScheme.tertiary,
    numberColor = MaterialTheme.colorScheme.secondary,
    literalColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * Builds the highlighted line without dropping a single character: formatting only adds colour,
 * it never rewrites the captured bytes.
 */
internal fun taskCenterJsonAnnotatedLine(
    line: String,
    keyColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    numberColor: androidx.compose.ui.graphics.Color,
    literalColor: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    val tokens = taskCenterJsonTokens(line)
    if (tokens.isEmpty()) return AnnotatedString(line)
    return buildAnnotatedString {
        var cursor = 0
        tokens.forEach { token ->
            if (token.start > cursor) append(line.substring(cursor, token.start))
            val color = when (token.type) {
                TaskCenterJsonTokenType.Key -> keyColor
                TaskCenterJsonTokenType.Text -> textColor
                TaskCenterJsonTokenType.Number -> numberColor
                TaskCenterJsonTokenType.Literal -> literalColor
                TaskCenterJsonTokenType.Punctuation -> androidx.compose.ui.graphics.Color.Unspecified
            }
            if (color == androidx.compose.ui.graphics.Color.Unspecified) {
                append(token.text)
            } else {
                withStyle(
                    SpanStyle(
                        color = color,
                        fontWeight = if (token.type == TaskCenterJsonTokenType.Key) FontWeight.Medium else null,
                    ),
                ) {
                    append(token.text)
                }
            }
            cursor = token.start + token.text.length
        }
        if (cursor < line.length) append(line.substring(cursor))
    }
}

internal fun taskCenterSizeText(chars: Int, copy: TaskCenterCopy): String =
    if (chars < 1024) {
        String.format(copy.viewerChars, chars)
    } else {
        String.format(copy.viewerKb, (chars / 1024.0 * 10).toInt() / 10.0)
    }

internal fun bodyDisplayLines(body: String): List<String> {
    val rawLines = body.lineSequence().toList().ifEmpty { listOf("") }
    return rawLines.flatMap { line ->
        if (line.length <= BODY_VIEWER_LINE_CHARS) listOf(line) else line.chunked(BODY_VIEWER_LINE_CHARS)
    }
}

internal const val BODY_VIEWER_LINE_CHARS = 4_000
