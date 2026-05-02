package com.dailysatori.ui.feature.article

import android.content.Context
import android.content.Intent
import android.net.Uri

internal fun openArticleUrl(context: Context, url: String?) {
    val articleUrl = url?.trim().orEmpty()
    if (articleUrl.isBlank()) return

    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(articleUrl)))
}
