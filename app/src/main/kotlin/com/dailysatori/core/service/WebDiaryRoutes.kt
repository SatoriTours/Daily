package com.dailysatori.core.service

import com.dailysatori.data.repository.DiaryAttachmentRepository
import com.dailysatori.data.repository.DiaryRepository
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

internal fun Route.setupDiaryRoutes() {
    route("/diary") {
        get {
            val repo = get<DiaryRepository>(DiaryRepository::class.java)
            val page = (call.request.queryParameters["page"]?.toLongOrNull() ?: 1L).coerceIn(1L, 1_000_000L)
            val limit = 20L
            val offset = (page - 1) * limit
            val diaries = repo.getPaginated(limit, offset).first()
            val total = repo.count()
            call.respondText(respondOk(buildJsonObject {
                put("items", JsonArray(
                    diaries.map { d -> buildJsonObject {
                        put("id", JsonPrimitive(d.id))
                        put("content", JsonPrimitive(d.content ?: ""))
                        put("mood", JsonPrimitive(d.mood ?: ""))
                        put("tags", JsonPrimitive(d.tags ?: ""))
                        put("images", diaryImagesToJson(d.images))
                        put("createdAt", JsonPrimitive(d.created_at))
                        put("updatedAt", JsonPrimitive(d.updated_at))
                        put("version", JsonPrimitive(webVersion(d)))
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
            val repo = get<DiaryRepository>(DiaryRepository::class.java)
            val query = call.request.queryParameters["q"] ?: ""
            val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceIn(1, 1_000_000)
            val matches = repo.searchSync(query)
            val results = matches.drop((page - 1) * 20).take(20)
            call.respondText(respondOk(buildJsonObject {
                put("items", JsonArray(
                    results.map { d -> buildJsonObject {
                        put("id", JsonPrimitive(d.id))
                        put("content", JsonPrimitive(d.content ?: ""))
                        put("mood", JsonPrimitive(d.mood ?: ""))
                        put("tags", JsonPrimitive(d.tags ?: ""))
                        put("images", diaryImagesToJson(d.images))
                        put("updatedAt", JsonPrimitive(d.updated_at))
                        put("version", JsonPrimitive(webVersion(d)))
                        put("createdAt", JsonPrimitive(d.created_at))
                    }}
                ))
                put("pagination", webPagination(page, matches.size))
            }), ContentType.Application.Json)
        }

        get("/{id}") {
            val repo = get<DiaryRepository>(DiaryRepository::class.java)
            val id = call.parameters["id"]?.toLongOrNull() ?: 0L
            val d = repo.getById(id)
            if (d != null) {
                val attachments = get<DiaryAttachmentRepository>(DiaryAttachmentRepository::class.java).getForDiary(id)
                call.respondText(respondOk(buildJsonObject {
                    put("id", JsonPrimitive(d.id))
                    put("content", JsonPrimitive(d.content ?: ""))
                    put("mood", JsonPrimitive(d.mood ?: ""))
                    put("tags", JsonPrimitive(d.tags ?: ""))
                    put("images", diaryImagesToJson(d.images))
                    put("attachments", JsonArray(attachments.map { attachment -> buildJsonObject {
                        put("id", JsonPrimitive(attachment.id))
                        put("kind", JsonPrimitive(attachment.kind))
                        put("name", JsonPrimitive(attachment.display_name.ifBlank { attachment.local_path.substringAfterLast('/') }))
                        put("url", JsonPrimitive(fileUrl(attachment.local_path)))
                        put("mimeType", JsonPrimitive(attachment.mime_type))
                        put("sizeBytes", JsonPrimitive(attachment.size_bytes))
                        put("durationMs", JsonPrimitive(attachment.duration_ms))
                        put("transcript", JsonPrimitive(attachment.transcript))
                        put("transcriptStatus", JsonPrimitive(attachment.transcript_status))
                    } }))
                    put("createdAt", JsonPrimitive(d.created_at))
                    put("updatedAt", JsonPrimitive(d.updated_at))
                        put("version", JsonPrimitive(webVersion(d)))
                }), ContentType.Application.Json)
            } else {
                call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
            }
        }

        post {
            try {
                val body = call.receive<JsonObject>()
                val content = (body["content"] as? JsonPrimitive)?.content ?: ""
                val mood = (body["mood"] as? JsonPrimitive)?.content
                val tags = (body["tags"] as? JsonPrimitive)?.content
                val repo = get<DiaryRepository>(DiaryRepository::class.java)
                if (content.isBlank()) {
                    call.respondText(respondFail("日记内容不能为空"), ContentType.Application.Json, HttpStatusCode.BadRequest)
                    return@post
                }
                val id = repo.create(content, tags, mood, null)
                call.respondText(respondOk(diaryEditResult(repo.getById(id)!!)), ContentType.Application.Json)
            } catch (e: Exception) {
                call.respondText(respondFail(e.message ?: "Invalid request"), ContentType.Application.Json, HttpStatusCode.BadRequest)
            }
        }

        put("/{id}") {
            try {
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                val body = call.receive<JsonObject>()
                val content = (body["content"] as? JsonPrimitive)?.content ?: ""
                val mood = (body["mood"] as? JsonPrimitive)?.content
                val tags = (body["tags"] as? JsonPrimitive)?.content
                val repo = get<DiaryRepository>(DiaryRepository::class.java)
                val existing = repo.getById(id)
                if (existing == null) {
                    call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                    return@put
                }
                if (content.isBlank()) {
                    call.respondText(respondFail("日记内容不能为空"), ContentType.Application.Json, HttpStatusCode.BadRequest)
                    return@put
                }
                if (!call.checkEditVersion(body, webVersion(existing))) return@put
                if (!repo.updateIfUnchanged(existing, content, tags, mood)) {
                    call.editConflict(); return@put
                }
                call.respondText(respondOk(diaryEditResult(repo.getById(id)!!)), ContentType.Application.Json)
            } catch (e: Exception) {
                call.respondText(respondFail(e.message ?: "Invalid request"), ContentType.Application.Json, HttpStatusCode.BadRequest)
            }
        }

        delete("/{id}") {
            val repo = get<DiaryRepository>(DiaryRepository::class.java)
            val id = call.parameters["id"]?.toLongOrNull() ?: 0L
            repo.delete(id)
            call.respondText(respondOk(), ContentType.Application.Json)
        }
    }
}

private fun diaryImagesToJson(images: String?): JsonArray {
    if (images.isNullOrBlank()) return JsonArray(emptyList())
    val list = com.dailysatori.service.diary.parseDiaryImagePaths(images).map { fileUrl(it) }
    return JsonArray(list.map { JsonPrimitive(it) })
}

private fun diaryEditResult(d: com.dailysatori.shared.db.Diary_record): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(d.id))
    put("content", JsonPrimitive(d.content.orEmpty()))
    put("tags", JsonPrimitive(d.tags.orEmpty()))
    put("mood", JsonPrimitive(d.mood.orEmpty()))
    put("createdAt", JsonPrimitive(d.created_at))
    put("updatedAt", JsonPrimitive(d.updated_at))
    put("version", JsonPrimitive(webVersion(d)))
}
