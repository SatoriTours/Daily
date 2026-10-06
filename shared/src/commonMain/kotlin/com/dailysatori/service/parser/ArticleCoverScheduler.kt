package com.dailysatori.service.parser

fun interface ArticleCoverScheduler {
    fun enqueue(articleId: Long)
}
