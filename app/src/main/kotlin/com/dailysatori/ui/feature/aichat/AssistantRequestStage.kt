package com.dailysatori.ui.feature.aichat

import com.dailysatori.R

internal fun assistantRequestStageResource(stage: String): Int? = when (stage) {
    "queued" -> R.string.assistant_request_queued
    "requesting" -> R.string.assistant_request_waiting
    "receiving" -> R.string.assistant_request_receiving
    "received" -> R.string.assistant_request_processing
    "retrying" -> R.string.assistant_request_retrying
    else -> null
}
