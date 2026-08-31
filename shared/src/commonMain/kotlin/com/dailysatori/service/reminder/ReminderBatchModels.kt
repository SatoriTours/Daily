package com.dailysatori.service.reminder

data class ReminderInputFragment(val index: Int, val text: String)

data class ReminderBatchItem(
    val id: String,
    val sourceIndex: Int,
    val sourceText: String,
    val interpretation: ReminderInterpretation,
)

data class ReminderBatchInterpretation(
    val batchId: String,
    val normalizedInput: String,
    val items: List<ReminderBatchItem>,
    val failure: String? = null,
)
