package com.dailysatori.service.reminder

import com.dailysatori.service.ai.unwrapAiJsonResponse

internal fun unwrapReminderAiJson(response: String): String = unwrapAiJsonResponse(response)
