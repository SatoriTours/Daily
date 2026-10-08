package com.dailysatori.ui.feature.ledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.bookkeeping.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.component.settings.SettingsScaffold
import com.dailysatori.ui.feature.bookkeeping.LedgerEditorDialog
import com.dailysatori.ui.theme.*
import kotlinx.datetime.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(onBack: () -> Unit, viewModel: LedgerViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val i18n: I18nService = koinInject()
    var pendingOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LedgerEntry?>(null) }
    val detail = editing ?: state.entry
    SettingsScaffold(title = i18n.t("ledger.title"), onBack = onBack) { modifier ->
        LazyColumn(modifier, contentPadding = PaddingValues(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            item { LedgerTabRow(state.tab, viewModel::selectTab) }
            item { LedgerPeriodBar(state, viewModel::selectPeriod, viewModel::shift) }
            item { LedgerSummaryCard(state) }
            if (state.pending.isNotEmpty()) item {
                PendingPill(state.pending.size) { pendingOpen = true }
            }
            if (state.failed) item {
                Text(i18n.t("ledger.failed"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (state.tab == LedgerTab.DETAIL) {
                if (state.buckets.all { it.entries.isEmpty() }) item {
                    Text(i18n.t("ledger.empty"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                state.buckets.forEach { bucket ->
                    item(key = "head:${bucket.start}") { BucketHeader(state, bucket) }
                    if (bucket.entries.isNotEmpty()) item(key = "rows:${bucket.start}") {
                        Surface(shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surface) {
                            Column {
                                bucket.entries.forEachIndexed { index, entry ->
                                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    LedgerRow(entry) { viewModel.openEntry(entry.id) }
                                }
                            }
                        }
                    }
                }
            } else {
                item { TrendCard(state) }
                item { CategoryCard(state) }
                if (state.merchants.isNotEmpty()) item { MerchantCard(state) }
            }
        }
    }
    if (pendingOpen) PendingSheet(state, viewModel, onDismiss = { pendingOpen = false }, onEdit = { pendingOpen = false; editing = it })
    detail?.let { entry ->
        LedgerDetailSheet(entry, state.busy,
            onDismiss = { editing = null; viewModel.closeEntry() },
            onCategory = { category, remember -> viewModel.setCategory(entry.id, category, remember) },
            onNote = { viewModel.setNote(entry.id, it) },
            onExcluded = { viewModel.setExcluded(entry.id, it) },
            onDelete = { viewModel.dismiss(entry.id, LedgerStatus.DELETED) })
    }
    editing?.let { entry ->
        LedgerEditorDialog(entry, state.busy, state.failed, onDismiss = { editing = null }) { amount, currency, kind, merchant ->
            viewModel.edit(entry.id, amount, currency, kind, merchant) { editing = null }
        }
    }
}

@Composable
private fun LedgerTabRow(tab: LedgerTab, onTab: (LedgerTab) -> Unit) {
    val i18n: I18nService = koinInject()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.s)).background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(Spacing.xxs), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        listOf(LedgerTab.DETAIL to "ledger.tab.detail", LedgerTab.ANALYSIS to "ledger.tab.analysis").forEach { (value, key) ->
            Surface(modifier = Modifier.weight(1f).selectable(value == tab, role = Role.Tab) { onTab(value) },
                shape = RoundedCornerShape(Radius.xs), color = if (value == tab) MaterialTheme.colorScheme.surface else Color.Transparent) {
                Text(i18n.t(key), Modifier.padding(vertical = Spacing.s), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (value == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LedgerPeriodBar(state: LedgerUiState, onPeriod: (LedgerPeriod) -> Unit, onShift: (Int) -> Unit) {
    val i18n: I18nService = koinInject()
    val periods = if (state.tab == LedgerTab.DETAIL) DETAIL_PERIODS else ANALYSIS_PERIODS
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clip(RoundedCornerShape(Radius.s)).background(MaterialTheme.colorScheme.surfaceContainer).padding(Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            periods.forEach { period ->
                Surface(modifier = Modifier.selectable(period == state.period, role = Role.Tab) { onPeriod(period) },
                    shape = RoundedCornerShape(Radius.xs), color = if (period == state.period) MaterialTheme.colorScheme.surface else Color.Transparent) {
                    Text(i18n.t("ledger.period.${period.name.lowercase()}"), Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (period == state.period) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { onShift(-1) }) { Text(i18n.t("ledger.previous_period")) }
        Text(periodLabel(state.period, state.anchor), style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { onShift(1) }, enabled = state.anchor < Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date) { Text(i18n.t("ledger.next_period")) }
    }
}

internal fun periodLabel(period: LedgerPeriod, anchor: LocalDate): String = when (period) {
    LedgerPeriod.MONTH -> "${anchor.year}-${anchor.monthNumber.toString().padStart(2, '0')}"
    LedgerPeriod.QUARTER -> "${anchor.year} Q${(anchor.monthNumber - 1) / 3 + 1}"
    LedgerPeriod.YEAR -> "${anchor.year}"
    else -> anchor.toString()
}

@Composable
private fun LedgerSummaryCard(state: LedgerUiState) {
    val i18n: I18nService = koinInject()
    val expense = state.expense()
    val change = when {
        state.previousExpense <= 0 -> ""
        else -> i18n.t("ledger.change", "${if (expense >= state.previousExpense) "+" else "-"}${
            kotlin.math.abs(expense - state.previousExpense) * 100 / state.previousExpense}%")
    }
    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i18n.t("ledger.scope.${state.period.name.lowercase()}"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (change.isNotEmpty()) Text(change, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(LedgerMoney.format(expense, "CNY"), style = MaterialTheme.typography.headlineSmall)
            state.totals.forEach { total ->
                Text(i18n.t("ledger.summary.line", total.currency, LedgerMoney.format(total.income, total.currency),
                    LedgerMoney.format(total.expense, total.currency), LedgerMoney.format(total.refund, total.currency)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PendingPill(count: Int, onClick: () -> Unit) {
    val i18n: I18nService = koinInject()
    Surface(onClick = onClick, shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()) {
        Text(i18n.t("ledger.pending", count), Modifier.padding(Spacing.m), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun BucketHeader(state: LedgerUiState, bucket: LedgerBucket) {
    val i18n: I18nService = koinInject()
    val expense = state.bucketExpense(bucket)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(bucketLabel(state.period, bucket), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(if (expense == 0L) i18n.t("ledger.no_records") else LedgerMoney.format(expense, "CNY"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun bucketLabel(period: LedgerPeriod, bucket: LedgerBucket): String = when (period) {
    LedgerPeriod.WEEK -> "${bucket.start} ~ ${bucket.end.monthNumber.toString().padStart(2, '0')}-${bucket.end.dayOfMonth.toString().padStart(2, '0')}"
    LedgerPeriod.MONTH -> "${bucket.start.year}-${bucket.start.monthNumber.toString().padStart(2, '0')}"
    else -> bucket.start.toString()
}

@Composable
private fun LedgerRow(entry: LedgerEntry, onClick: () -> Unit) {
    val i18n: I18nService = koinInject()
    val zone = TimeZone.currentSystemDefault()
    val time = Instant.fromEpochMilliseconds(entry.receivedAt).toLocalDateTime(zone)
    val meta = listOf(time.hour.toString().padStart(2, '0') + ":" + time.minute.toString().padStart(2, '0'),
        i18n.t("ledger.category.${entry.category.name.lowercase()}"),
        entry.accountTail.takeIf { it.isNotEmpty() }?.let { i18n.t("bookkeeping.account_tail", it) }.orEmpty(),
        entry.note).filter { it.isNotBlank() }.joinToString(" · ")
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Surface(shape = RoundedCornerShape(Radius.xs), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(i18n.t("ledger.category.short.${entry.category.name.lowercase()}"), Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(entry.merchant.ifBlank { i18n.t("ledger.unknown_merchant") }, style = MaterialTheme.typography.bodyMedium)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(signed(entry), style = MaterialTheme.typography.titleSmall,
            color = if (entry.kind == LedgerKind.INCOME || entry.kind == LedgerKind.REFUND) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface)
    }
}

private fun signed(entry: LedgerEntry): String {
    val amount = entry.amountMinor ?: return ""
    val text = LedgerMoney.format(amount, entry.currency)
    return when (entry.kind) {
        LedgerKind.INCOME, LedgerKind.REFUND -> "+$text"
        else -> "-$text"
    }
}

@Composable
private fun TrendCard(state: LedgerUiState) {
    val i18n: I18nService = koinInject()
    val values = state.trend.map { state.bucketExpense(it) }
    val max = values.maxOrNull() ?: 0L
    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i18n.t("ledger.trend"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(i18n.t("ledger.trend_unit.${state.period.name.lowercase()}"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                state.trend.forEachIndexed { index, bucket ->
                    val fraction = if (max == 0L) 0.02f else (state.bucketExpense(bucket).toFloat() / max).coerceIn(0.02f, 1f)
                    Box(Modifier.weight(1f).fillMaxHeight(fraction).clip(RoundedCornerShape(topStart = Radius.xs, topEnd = Radius.xs))
                        .background(if (index == state.trend.lastIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                val step = (state.trend.size / 6).coerceAtLeast(1)
                state.trend.forEachIndexed { index, bucket ->
                    Text(if (index % step == 0) axisLabel(state.period, bucket) else "", Modifier.weight(1f),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (max > 0) Text(i18n.t("ledger.trend_max", LedgerMoney.format(max, "CNY")), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun axisLabel(period: LedgerPeriod, bucket: LedgerBucket): String = when (period) {
    LedgerPeriod.YEAR -> bucket.start.monthNumber.toString()
    LedgerPeriod.QUARTER -> bucket.start.dayOfMonth.toString()
    LedgerPeriod.MONTH -> bucket.start.dayOfMonth.toString()
    else -> bucket.start.dayOfMonth.toString()
}

@Composable
private fun CategoryCard(state: LedgerUiState) {
    val i18n: I18nService = koinInject()
    val total = state.categories.sumOf { it.amount }
    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i18n.t("ledger.by_category"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(LedgerMoney.format(total, "CNY"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.categories.isEmpty()) Text(i18n.t("ledger.no_records"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                DonutChart(state.categories.map { it.amount }, total)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    state.categories.take(5).forEachIndexed { index, item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(IconSize.xs).clip(RoundedCornerShape(Radius.xxs)).background(donutColor(index)))
                            Spacer(Modifier.width(Spacing.s))
                            Text(i18n.t("ledger.category.${item.category.name.lowercase()}"), Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            Text(if (total == 0L) "0%" else "${item.amount * 100 / total}%", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(Spacing.s))
                            Text(LedgerMoney.format(item.amount, "CNY"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun donutColor(index: Int): Color = when (index) {
    0 -> MaterialTheme.colorScheme.primary
    1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
    2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
    else -> MaterialTheme.colorScheme.outlineVariant
}

@Composable
private fun DonutChart(amounts: List<Long>, total: Long) {
    val colors = List(5) { donutColor(it) }
    Canvas(Modifier.size(104.dp)) {
        val stroke = Stroke(width = size.minDimension * 0.18f)
        var start = -90f
        amounts.forEachIndexed { index, amount ->
            val sweep = if (total <= 0L) 0f else amount.toFloat() / total * 360f
            drawArc(color = colors[index % colors.size], startAngle = start, sweepAngle = sweep - 2f, useCenter = false,
                style = stroke, topLeft = androidx.compose.ui.geometry.Offset(stroke.width / 2, stroke.width / 2),
                size = Size(size.width - stroke.width, size.height - stroke.width))
            start += sweep
        }
    }
}

@Composable
private fun MerchantCard(state: LedgerUiState) {
    val i18n: I18nService = koinInject()
    val max = state.merchants.maxOfOrNull { it.amount } ?: 1L
    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(i18n.t("ledger.by_merchant"), style = MaterialTheme.typography.titleSmall)
            state.merchants.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text("${index + 1}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(Spacing.m))
                    Column(Modifier.weight(1f)) {
                        Text(item.merchant, style = MaterialTheme.typography.bodyMedium)
                        Text(i18n.t("ledger.merchant_count", item.count), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.width(48.dp).height(Spacing.xs).clip(RoundedCornerShape(Radius.xxs))
                        .background(MaterialTheme.colorScheme.surfaceContainer)) {
                        Box(Modifier.fillMaxWidth(item.amount.toFloat() / max).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary))
                    }
                    Text(LedgerMoney.format(item.amount, "CNY"), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingSheet(state: LedgerUiState, viewModel: LedgerViewModel, onDismiss: () -> Unit, onEdit: (LedgerEntry) -> Unit) {
    val i18n: I18nService = koinInject()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(i18n.t("ledger.pending_title"), style = MaterialTheme.typography.titleMedium)
            state.pending.forEach { entry ->
                Surface(shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(entry.merchant.ifBlank { i18n.t("ledger.unknown_merchant") }, style = MaterialTheme.typography.bodyMedium)
                        Text(i18n.t("bookkeeping.reason.${entry.reason}"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            TextButton(onClick = { onEdit(entry) }, enabled = !state.busy) { Text(i18n.t("bookkeeping.confirm")) }
                            TextButton(onClick = { viewModel.dismiss(entry.id, LedgerStatus.IGNORED) }, enabled = !state.busy) {
                                Text(i18n.t("bookkeeping.ignore"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LedgerDetailSheet(entry: LedgerEntry, busy: Boolean, onDismiss: () -> Unit,
    onCategory: (LedgerCategory, Boolean) -> Unit, onNote: (String) -> Unit, onExcluded: (Boolean) -> Unit, onDelete: () -> Unit) {
    val i18n: I18nService = koinInject()
    var note by remember(entry.id) { mutableStateOf(entry.note) }
    var remember_rule by remember(entry.id) { mutableStateOf(true) }
    var showOriginal by remember(entry.id) { mutableStateOf(false) }
    val zone = TimeZone.currentSystemDefault()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(signed(entry), style = MaterialTheme.typography.headlineSmall)
                Text("${i18n.t("bookkeeping.kind.${entry.kind.name.lowercase()}")} · ${entry.currency} · ${
                    Instant.fromEpochMilliseconds(entry.receivedAt).toLocalDateTime(zone).toString().replace('T', ' ').take(16)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(i18n.t("ledger.detail.category"), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                LedgerCategory.entries.forEach { category ->
                    val selected = category == entry.category
                    Surface(onClick = { onCategory(category, remember_rule) }, shape = RoundedCornerShape(Radius.circular),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                        Text(i18n.t("ledger.category.${category.name.lowercase()}"),
                            Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s), style = MaterialTheme.typography.bodySmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(remember_rule, onCheckedChange = { remember_rule = it }, enabled = !busy)
                Text(i18n.t("ledger.detail.remember"), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(note, onValueChange = { note = it.take(200) }, label = { Text(i18n.t("ledger.detail.note")) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextButton(onClick = { onNote(note) }, enabled = !busy && note != entry.note) { Text(i18n.t("bookkeeping.save")) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DetailRow(i18n.t("ledger.detail.merchant"), entry.merchant.ifBlank { "-" })
            if (entry.accountTail.isNotEmpty()) DetailRow(i18n.t("ledger.detail.account_tail"), entry.accountTail)
            DetailRow(i18n.t("ledger.detail.source"), entry.source)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i18n.t("ledger.detail.original"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { showOriginal = !showOriginal }) {
                    Text(i18n.t(if (showOriginal) "bookkeeping.hide_source" else "bookkeeping.show_source"))
                }
            }
            if (showOriginal) Text(entry.text, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(i18n.t("ledger.detail.excluded"), style = MaterialTheme.typography.bodyMedium)
                    Text(i18n.t("ledger.detail.excluded_hint"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(entry.excluded, onCheckedChange = onExcluded, enabled = !busy)
            }
            TextButton(onClick = onDelete, enabled = !busy) {
                Text(i18n.t("ledger.detail.delete"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
