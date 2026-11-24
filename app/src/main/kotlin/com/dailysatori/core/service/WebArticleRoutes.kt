package com.dailysatori.core.service

import com.dailysatori.core.worker.ArticleProcessingScheduler
import com.dailysatori.data.repository.ArticleAiRepository
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.service.parser.WebpageParserService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.java.KoinJavaComponent.get

internal fun Route.setupArticleRoutes() {
    route("/articles") {
        get {
            val repo = get<ArticleRepository>(ArticleRepository::class.java)
            val page = (call.request.queryParameters["page"]?.toLongOrNull() ?: 1L).coerceIn(1L, 1_000_000L)
            val limit = 20L
            val offset = (page - 1) * limit
            val favorites = call.request.queryParameters["favorites"] == "true"
            // Favorites page in SQL instead of pulling the whole table into memory.
            val articles = if (favorites) {
                repo.getFavoritesPaginated(limit, offset).first()
            } else {
                repo.getPaginated(limit, offset).first()
            }
            val total = if (favorites) repo.countFavorites() else repo.count()
            call.respondText(respondOk(buildJsonObject {
                put("items", JsonArray(
                    articles.map { a -> buildJsonObject {
                        put("id", JsonPrimitive(a.id))
                        put("title", JsonPrimitive(a.title ?: ""))
                        put("url", JsonPrimitive(a.url ?: ""))
                        put("coverImage", JsonPrimitive(articleCoverUrl(a.cover_image, a.cover_image_url)))
                        put("aiTitle", JsonPrimitive(a.ai_title ?: ""))
                        put("aiContent", JsonPrimitive(a.ai_content ?: ""))
                        put("isFavorite", JsonPrimitive((a.is_favorite ?: 0) > 0))
                        put("status", JsonPrimitive(a.status ?: ""))
                        put("createdAt", JsonPrimitive(a.created_at))
                        put("updatedAt", JsonPrimitive(a.updated_at))
                        put("version", JsonPrimitive(webVersion(a)))
                    }}
                ))
                put("pagination", buildJsonObject {
                    put("page", JsonPrimitive(page))
                    put("pageSize", JsonPrimitive(limit))
                    put("totalItems", JsonPrimitive(total))
                    put("totalPages", JsonPrimitive((total + limit - 1) / limit))
                })
            }), ContentType.Application.Json)
        }

        get("/search") {
            val repo = get<ArticleRepository>(ArticleRepository::class.java)
            val query = call.request.queryParameters["q"] ?: ""
            val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceIn(1, 1_000_000)
            val matches = repo.searchSync(query).filter { call.request.queryParameters["favorites"] != "true" || (it.is_favorite ?: 0L) > 0L }
            val results = matches.drop((page - 1) * 20).take(20)
            call.respondText(respondOk(buildJsonObject {
                put("items", JsonArray(
                    results.map { a -> buildJsonObject {
                        put("id", JsonPrimitive(a.id))
                        put("title", JsonPrimitive(a.title ?: ""))
                        put("url", JsonPrimitive(a.url ?: ""))
                        put("coverImage", JsonPrimitive(articleCoverUrl(a.cover_image, a.cover_image_url)))
                        put("aiTitle", JsonPrimitive(a.ai_title ?: ""))
                        put("aiContent", JsonPrimitive(a.ai_content ?: ""))
                        put("isFavorite", JsonPrimitive((a.is_favorite ?: 0) > 0))
                        put("status", JsonPrimitive(a.status ?: ""))
                        put("createdAt", JsonPrimitive(a.created_at))
                        put("updatedAt", JsonPrimitive(a.updated_at))
                        put("version", JsonPrimitive(webVersion(a)))
                    }}
                ))
                put("pagination", webPagination(page, matches.size))
            }), ContentType.Application.Json)
        }

        get("/{id}") {
            val repo = get<ArticleRepository>(ArticleRepository::class.java)
            val id = call.parameters["id"]?.toLongOrNull() ?: 0L
            val a = repo.getById(id)
            if (a != null) {
                val aiView = articleAiView(a)
                val aiSummary = aiView?.summary?.takeIf { it.isNotBlank() }
                val aiTranslation = aiView?.translation?.takeIf { it.isNotBlank() }
                val remoteNews = a.source_type == ArticleRepository.SOURCE_REMOTE_NEWS
                call.respondText(respondOk(buildJsonObject {
                    put("id", JsonPrimitive(a.id))
                    put("title", JsonPrimitive(a.title ?: ""))
                    put("url", JsonPrimitive(a.url ?: ""))
                    // Remote news keeps the smart-reading result authoritative while staying backward compatible.
                    put("aiContent", JsonPrimitive(if (remoteNews) aiSummary ?: a.ai_content.orEmpty() else a.ai_content.orEmpty()))
                    put("aiMarkdownContent",
                        JsonPrimitive(if (remoteNews) aiTranslation ?: a.ai_markdown_content.orEmpty() else a.ai_markdown_content.orEmpty()))
                    put("articleAiSummary", JsonPrimitive(aiSummary.orEmpty()))
                    put("articleAiTranslation", JsonPrimitive(aiTranslation.orEmpty()))
                    put("originalMarkdownContent", JsonPrimitive(a.original_markdown_content ?: ""))
                    put("sourceType", JsonPrimitive(a.source_type))
                    put("aiTitle", JsonPrimitive(a.ai_title ?: ""))
                    put("coverImage", JsonPrimitive(articleCoverUrl(a.cover_image, a.cover_image_url)))
                    put("isFavorite", JsonPrimitive((a.is_favorite ?: 0) > 0))
                    put("comment", JsonPrimitive(a.comment ?: ""))
                    put("status", JsonPrimitive(a.status ?: ""))
                    put("createdAt", JsonPrimitive(a.created_at))
                    put("updatedAt", JsonPrimitive(a.updated_at))
                        put("version", JsonPrimitive(webVersion(a)))
                }), ContentType.Application.Json)
            } else {
                call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
            }
        }

        post {
            try {
                val body = call.receive<JsonObject>()
                val url = body.stringValue("url").trim()
                if (url.isBlank()) {
                    call.respondText(respondFail("URL is required"), ContentType.Application.Json, HttpStatusCode.BadRequest)
                    return@post
                }
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val existing = repo.getByUrl(url)
                if (existing != null) {
                    call.respondText(respondOk(buildJsonObject {
                        put("id", JsonPrimitive(existing.id))
                        put("queued", JsonPrimitive(false))
                        put("existing", JsonPrimitive(true))
                    }), ContentType.Application.Json)
                    return@post
                }
                get<ArticleProcessingScheduler>(ArticleProcessingScheduler::class.java).enqueueSave(url)
                call.respondText(respondOk(buildJsonObject {
                    put("queued", JsonPrimitive(true))
                    put("existing", JsonPrimitive(false))
                }), ContentType.Application.Json, HttpStatusCode.Accepted)
            } catch (e: Exception) {
                call.respondText(respondFail(e.message ?: "Invalid request"), ContentType.Application.Json, HttpStatusCode.BadRequest)
            }
        }

        put("/{id}") {
            try {
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val article = repo.getById(id)
                if (article == null) {
                    call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                    return@put
                }
                val body = call.receive<JsonObject>()
                if (!call.checkEditVersion(body, webVersion(article))) return@put
                val saved = repo.updateDetailsIfUnchanged(
                    article, body.optionalStringValue("title", article.title),
                    body.optionalStringValue("comment", article.comment),
                )
                if (!saved) { call.editConflict(); return@put }
                call.respondText(respondOk(articleSummary(repo.getById(id)!!)), ContentType.Application.Json)
            } catch (e: Exception) {
                call.respondText(respondFail(e.message ?: "Invalid request"), ContentType.Application.Json, HttpStatusCode.BadRequest)
            }
        }

        post("/{id}/favorite") {
            val repo = get<ArticleRepository>(ArticleRepository::class.java)
            val id = call.parameters["id"]?.toLongOrNull() ?: 0L
            if (repo.getById(id) == null) {
                call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                return@post
            }
            repo.toggleFavorite(id)
            call.respondText(respondOk(articleSummary(repo.getById(id)!!)), ContentType.Application.Json)
        }

        post("/{id}/reprocess") {
            val repo = get<ArticleRepository>(ArticleRepository::class.java)
            val id = call.parameters["id"]?.toLongOrNull() ?: 0L
            if (repo.getById(id) == null) {
                call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                return@post
            }
            get<WebpageParserService>(WebpageParserService::class.java).reprocessArticle(id)
            call.respondText(respondOk(buildJsonObject { put("queued", JsonPrimitive(true)) }), ContentType.Application.Json)
        }

        delete("/{id}") {
            val repo = get<ArticleRepository>(ArticleRepository::class.java)
            val id = call.parameters["id"]?.toLongOrNull() ?: 0L
            repo.delete(id)
            call.respondText(respondOk(), ContentType.Application.Json)
        }
    }
}

private fun articleCoverUrl(localPath: String?, remoteUrl: String?): String =
    fileUrl(localPath?.takeIf { it.isNotBlank() } ?: remoteUrl)

/** Smart-reading results are optional: an unregistered repository must not break the article API. */
private fun articleAiView(article: com.dailysatori.shared.db.Article): com.dailysatori.data.repository.ArticleAiView? =
    runCatching { get<ArticleAiRepository>(ArticleAiRepository::class.java).view(article) }.getOrNull()

private fun articleSummary(article: com.dailysatori.shared.db.Article): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(article.id))
    put("version", JsonPrimitive(webVersion(article)))
    put("title", JsonPrimitive(article.title.orEmpty()))
    put("isFavorite", JsonPrimitive((article.is_favorite ?: 0L) > 0L))
    put("comment", JsonPrimitive(article.comment.orEmpty()))
    put("status", JsonPrimitive(article.status.orEmpty()))
}
