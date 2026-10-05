package com.dailysatori.ui.feature.bookkeeping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.bookkeeping.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.theme.*
import org.koin.compose.koinInject

@Composable
internal fun BookkeepingSourcesDialog(state: BookkeepingUiState, onSelect: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    val i18n: I18nService = koinInject()
    var search by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(i18n.t("bookkeeping.select_sources")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(i18n.t("bookkeeping.sources_notice"), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(search, onValueChange = { search = it }, label = { Text(i18n.t("bookkeeping.search_sources")) }, singleLine = true)
            LazyColumn(Modifier.heightIn(max = Height.listItem * 6)) {
                items(state.sources.filter { it.label.contains(search, true) || it.packageName.contains(search, true) }, key = { it.packageName }) { source ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(source.label)
                            Text(source.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(source.packageName in state.selectedSources, onCheckedChange = { onSelect(source.packageName, it) }, enabled = !state.busy)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(i18n.t("bookkeeping.done")) } })
}

@Composable
internal fun LedgerEditorDialog(entry: LedgerEntry, busy: Boolean, failed: Boolean, onDismiss: () -> Unit,
    onSave: (String, String, LedgerKind, String) -> Unit) {
    val i18n: I18nService = koinInject()
    var amount by remember(entry.id) { mutableStateOf(entry.amountMinor?.let { LedgerMoney.format(it, entry.currency) }.orEmpty()) }
    var currency by remember(entry.id) { mutableStateOf(entry.currency) }
    var kind by remember(entry.id) { mutableStateOf(entry.kind) }
    var merchant by remember(entry.id) { mutableStateOf(entry.merchant) }
    val valid = LedgerMoney.parse(amount, currency) != null && kind != LedgerKind.UNKNOWN && merchant.length <= 80
    AlertDialog(onDismissRequest = onDismiss, title = { Text(i18n.t("bookkeeping.edit_entry")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            OutlinedTextField(amount, onValueChange = { amount = it }, label = { Text(i18n.t("bookkeeping.amount")) },
                isError = amount.isNotEmpty() && LedgerMoney.parse(amount, currency) == null, singleLine = true)
            LedgerDropdown(currency, LedgerMoney.currencies.toList(), onSelect = { currency = it }, label = i18n.t("bookkeeping.currency"))
            val kinds = LedgerKind.entries.filter { it != LedgerKind.UNKNOWN }
            LedgerDropdown(i18n.t("bookkeeping.kind.${kind.name.lowercase()}"), kinds.map { i18n.t("bookkeeping.kind.${it.name.lowercase()}") },
                onSelect = { label -> kind = kinds.first { i18n.t("bookkeeping.kind.${it.name.lowercase()}") == label } }, label = i18n.t("bookkeeping.type"))
            OutlinedTextField(merchant, onValueChange = { merchant = it }, label = { Text(i18n.t("bookkeeping.merchant")) },
                isError = merchant.length > 80, singleLine = true)
            if (entry.duplicateOf.isNotEmpty()) Text(i18n.t("bookkeeping.duplicate_confirmation"), style = MaterialTheme.typography.bodySmall)
            if (failed) Text(i18n.t("bookkeeping.operation_failed"), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = { onSave(amount, currency, kind, merchant) }, enabled = valid && !busy) { Text(i18n.t("bookkeeping.save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(i18n.t("bookkeeping.cancel")) } })
}

@Composable
private fun LedgerDropdown(value: String, options: List<String>, onSelect: (String) -> Unit, label: String) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("$label: $value") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}
