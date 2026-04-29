package com.dailysatori.service.import

import app.cash.sqldelight.db.QueryResult
import co.touchlab.kermit.Logger
import com.dailysatori.platform.FileManager
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class ImportService(
    private val db: DailySatoriDatabase,
    private val driver: app.cash.sqldelight.db.SqlDriver,
    private val fileManager: FileManager,
) {
    private val log = Logger.withTag("Import")
    private val json = Json { ignoreUnknownKeys = true }

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting
    private val _progress = MutableStateFlow(0.0)
    val progress: StateFlow<Double> = _progress

    private val idMaps = mutableMapOf<String, MutableMap<Long, Long>>()

    data class ImportResult(
        val settings: Int = 0,
        val aiConfigs: Int = 0,
        val tags: Int = 0,
        val articles: Int = 0,
        val articleTags: Int = 0,
        val images: Int = 0,
        val diaries: Int = 0,
        val books: Int = 0,
        val bookViewpoints: Int = 0,
        val weeklySummaries: Int = 0,
        val sessions: Int = 0,
        val imageFilesCopied: Int = 0,
    )

    suspend fun importFromZip(zipPath: String): ImportResult = withContext(Dispatchers.IO) {
        _isImporting.value = true
        _progress.value = 0.0
        idMaps.clear()

        try {
            val tempDir = "${fileManager.getCacheDir()}/import_${nowMs()}"
            fileManager.extractZip(zipPath, tempDir)
            _progress.value = 0.05

            val result = ImportResult(
                settings = importSettings(tempDir),
                aiConfigs = importAiConfigs(tempDir),
                tags = importTags(tempDir),
                articles = importArticles(tempDir),
                articleTags = importArticleTags(tempDir),
                images = importImages(tempDir),
                diaries = importDiaries(tempDir),
                books = importBooks(tempDir),
                bookViewpoints = importBookViewpoints(tempDir),
                weeklySummaries = importWeeklySummaries(tempDir),
                sessions = importSessions(tempDir),
                imageFilesCopied = copyImageFiles(tempDir),
            )

            cleanup(tempDir)
            _progress.value = 1.0
            log.i { "Import completed: $result" }
            result
        } catch (e: Exception) {
            log.e(e) { "Import failed" }
            throw e
        } finally {
            _isImporting.value = false
        }
    }

    private fun readJsonArray(dir: String, fileName: String): JsonArray? {
        val path = "$dir/$fileName"
        if (!fileManager.exists(path)) {
            log.w { "File not found: $fileName" }
            return null
        }
        val bytes = fileManager.readFile(path)
        val text = bytes.decodeToString()
        return json.parseToJsonElement(text).jsonArray
    }

    private fun insertAndGetNewId(entityType: String, oldId: Long, insertBlock: () -> Unit): Long {
        insertBlock()
        var newId = 0L
        try {
            driver.executeQuery<Long>(0, "SELECT last_insert_rowid()", { cursor ->
                if (cursor.next().value) {
                    newId = cursor.getLong(0) ?: 0L
                }
                QueryResult.Value(newId)
            }, 0)
        } catch (e: Exception) {
            log.e(e) { "Failed to get last insert id for $entityType" }
        }
        if (newId == 0L) {
            log.w { "Could not determine new ID for $entityType (oldId=$oldId)" }
        }
        idMaps.getOrPut(entityType) { mutableMapOf() }[oldId] = newId
        return newId
    }

    private fun getNewId(entityType: String, oldId: Long): Long? = idMaps[entityType]?.get(oldId)

    private fun JsonObject.getString(key: String): String? =
        (this[key] as? JsonPrimitive)?.content

    private fun JsonObject.getLong(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.getEpochMs(key: String): Long? {
        val str = getString(key) ?: return null
        return try {
            Instant.parse(str).toEpochMilliseconds()
        } catch (_: Exception) {
            str.toLongOrNull()
        }
    }

    private fun importSettings(dir: String): Int {
        val arr = readJsonArray(dir, "settings.json") ?: return 0
        var count = 0
        arr.forEach { element ->
            val obj = element.jsonObject
            val key = obj.getString("key") ?: return@forEach
            val value = obj.getString("value")
            val createdAt = obj.getEpochMs("created_at") ?: nowMs()
            val updatedAt = obj.getEpochMs("updated_at") ?: nowMs()
            db.dailySatoriQueries.upsertSetting(key, value, createdAt, updatedAt)
            count++
        }
        _progress.value = 0.1
        return count
    }

    private fun importAiConfigs(dir: String): Int {
        val arr = readJsonArray(dir, "ai_configs.json") ?: return 0
        var count = 0
        arr.forEach { element ->
            val obj = element.jsonObject
            val oldId = obj.getLong("id") ?: return@forEach
            insertAndGetNewId("ai_config", oldId) {
                db.dailySatoriQueries.insertAiConfig(
                    obj.getString("name") ?: "",
                    obj.getString("api_address") ?: "",
                    obj.getString("api_token") ?: "",
                    obj.getString("model_name") ?: "",
                    obj.getLong("function_type") ?: 0,
                    obj.getLong("inherit_from_general") ?: 0,
                    obj.getLong("is_default") ?: 0,
                    obj.getEpochMs("created_at") ?: nowMs(),
                    obj.getEpochMs("updated_at") ?: nowMs(),
                )
            }
            count++
        }
        _progress.value = 0.15
        return count
    }

    private fun importTags(dir: String): Int {
        val arr = readJsonArray(dir, "tags.json") ?: return 0
        var count = 0
        arr.forEach { element ->
            val obj = element.jsonObject
            val oldId = obj.getLong("id") ?: return@forEach
            insertAndGetNewId("tag", oldId) {
                db.dailySatoriQueries.insertTag(
                    obj.getString("name") ?: "",
                    obj.getString("icon"),
                    obj.getEpochMs("created_at") ?: nowMs(),
                    obj.getEpochMs("updated_at") ?: nowMs(),
                )
            }
            count++
        }
        _progress.value = 0.2
        return count
    }

    private fun importArticles(dir: String): Int {
        val arr = readJsonArray(dir, "articles.json")
        if (arr == null) {
            log.w { "articles.json not found in import" }
            return 0
        }
        var count = 0
        arr.forEach { element ->
            val obj = element.jsonObject
            val oldId = obj.getLong("id") ?: return@forEach
            insertAndGetNewId("article", oldId) {
                db.dailySatoriQueries.insertArticle(
                    obj.getString("title"),
                    obj.getString("ai_title"),
                    obj.getString("content"),
                    obj.getString("ai_content"),
                    obj.getString("html_content"),
                    obj.getString("ai_markdown_content"),
                    obj.getString("url"),
                    obj.getLong("is_favorite") ?: 0,
                    obj.getString("comment"),
                    obj.getString("status") ?: "pending",
                    obj.getString("cover_image"),
                    obj.getString("cover_image_url"),
                    obj.getEpochMs("pub_date"),
                    obj.getEpochMs("created_at") ?: nowMs(),
                    obj.getEpochMs("updated_at") ?: nowMs(),
                )
            }
            count++
        }
        log.d { "Imported $count articles" }
        _progress.value = 0.35
        return count
    }

    private fun importArticleTags(dir: String): Int {
        val arr = readJsonArray(dir, "article_tags.json") ?: return 0
        var count = 0
        arr.forEach { element ->
            val obj = element.jsonObject
            val oldArticleId = obj.getLong("article_id") ?: return@forEach
            val oldTagId = obj.getLong("tag_id") ?: return@forEach
            val newArticleId = getNewId("article", oldArticleId) ?: return@forEach
            val newTagId = getNewId("tag", oldTagId) ?: return@forEach
            db.dailySatoriQueries.insertArticleTag(newArticleId, newTagId)
            count++
        }
        _progress.value = 0.4
        return count
    }

    private fun importImages(dir: String): Int {
        val arr = readJsonArray(dir, "images.json") ?: return 0
        var count = 0
        arr.forEach { element ->
            val obj = element.jsonObject
            val oldArticleId = obj.getLong("article_id") ?: return@forEach
            val newArticleId = getNewId("article", oldArticleId) ?: return@forEach
            insertAndGetNewId("image", obj.getLong("id") ?: return@forEach) {
                db.dailySatoriQueries.insertImage(
                    obj.getString("url"),
                    obj.getString("path"),
                    newArticleId,
                    obj.getEpochMs("created_at") ?: nowMs(),
                    obj.getEpochMs("updated_at") ?: nowMs(),
                )
            }
            count++
        }
        _progress.value = 0.5
        return count
    }

    private fun importDiaries(dir: String): Int {
        val arr = readJsonArray(dir, "diaries.json") ?: return 0
        var count = 0
        arr.forEach { element ->
            val obj = element.jsonObject
            val oldId = obj.getLong("id") ?: return@forEach
            val imagesStr = obj.getString("images")
            val remappedImages = remapDiaryImages(imagesStr)
            insertAndGetNewId("diary", oldId) {
                db.dailySatoriQueries.insertDiary(
                    obj.getString("content") ?: "",
                    obj.getString("tags"),
                    obj.getString("mood"),
                    remappedImages,
                    obj.getEpochMs("created_at") ?: nowMs(),
                    obj.getEpochMs("updated_at") ?: nowMs(),
                )
            }
            count++
        }
        _progress.value = 0.6
        return count
    }

    private fun remapDiaryImages(images: String?): String? {
        if (images.isNullOrBlank()) return images
        return images.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(",") { remapImagePath(it) }
    }

    private fun remapImagePath(path: String): String {
        val fileName = path.substringAfterLast("/")
        return "diary_images/$fileName"
    }

    private fun importBooks(dir: String): Int {
        val arr = readJsonArray(dir, "books.json") ?: return 0
        var count = 0
        arr.forEach { element ->
            val obj = element.jsonObject
            val oldId = obj.getLong("id") ?: return@forEach
            insertAndGetNewId("book", oldId) {
                db.dailySatoriQueries.insertBook(
                    obj.getString("title") ?: "",
                    obj.getString("author") ?: "",
                    obj.getString("category") ?: "",
                    obj.getString("cover_image") ?: "",
                    obj.getString("introduction") ?: "",
                    obj.getLong("has_update") ?: 0,
                    obj.getEpochMs("created_at") ?: nowMs(),
                    obj.getEpochMs("updated_at") ?: nowMs(),
                )
            }
            count++
        }
        _progress.value = 0.7
        return count
    }

    private fun importBookViewpoints(dir: String): Int {
        val arr = readJsonArray(dir, "book_viewpoints.json") ?: return 0
        var count = 0
        arr.forEach { element ->
            val obj = element.jsonObject
            val oldBookId = obj.getLong("book_id") ?: return@forEach
            val newBookId = getNewId("book", oldBookId) ?: return@forEach
            insertAndGetNewId("book_viewpoint", obj.getLong("id") ?: return@forEach) {
                db.dailySatoriQueries.insertViewpoint(
                    newBookId,
                    obj.getString("title") ?: "",
                    obj.getString("content") ?: "",
                    obj.getString("example") ?: "",
                    obj.getEpochMs("created_at") ?: nowMs(),
                    obj.getEpochMs("updated_at") ?: nowMs(),
                )
            }
            count++
        }
        _progress.value = 0.8
        return count
    }

    private fun importWeeklySummaries(dir: String): Int {
        val arr = readJsonArray(dir, "weekly_summaries.json") ?: return 0
        var count = 0
        arr.forEach { element ->
            val obj = element.jsonObject
            val oldId = obj.getLong("id") ?: return@forEach
            insertAndGetNewId("weekly_summary", oldId) {
                db.dailySatoriQueries.insertWeeklySummary(
                    obj.getEpochMs("week_start_date") ?: nowMs(),
                    obj.getEpochMs("week_end_date") ?: nowMs(),
                    obj.getString("content") ?: "",
                    obj.getLong("article_count") ?: 0,
                    obj.getLong("diary_count") ?: 0,
                    obj.getLong("viewpoint_count") ?: 0,
                    obj.getString("article_ids"),
                    obj.getString("diary_ids"),
                    obj.getString("viewpoint_ids"),
                    obj.getString("app_ideas"),
                    obj.getString("status") ?: "pending",
                    obj.getEpochMs("created_at") ?: nowMs(),
                    obj.getEpochMs("updated_at") ?: nowMs(),
                )
            }
            count++
        }
        _progress.value = 0.9
        return count
    }

    private fun importSessions(dir: String): Int {
        val arr = readJsonArray(dir, "sessions.json") ?: return 0
        var count = 0
        arr.forEach { element ->
            val obj = element.jsonObject
            val sessionId = obj.getString("session_id") ?: return@forEach
            db.dailySatoriQueries.insertSession(
                sessionId,
                obj.getLong("is_authenticated") ?: 0,
                obj.getString("username"),
                obj.getEpochMs("last_accessed_at") ?: nowMs(),
                obj.getEpochMs("created_at") ?: nowMs(),
                obj.getEpochMs("updated_at") ?: nowMs(),
            )
            count++
        }
        _progress.value = 0.95
        return count
    }

    private fun copyImageFiles(tempDir: String): Int {
        val imagesDir = "$tempDir/images"
        val diaryImagesDir = "$tempDir/diary_images"
        var count = 0

        if (fileManager.exists(imagesDir)) {
            val targetDir = fileManager.getImagesDir()
            fileManager.listFiles(imagesDir).forEach { src ->
                val name = src.substringAfterLast("/")
                fileManager.copyFile(src, "$targetDir/$name")
                count++
            }
        }

        if (fileManager.exists(diaryImagesDir)) {
            val targetDir = fileManager.getDiaryImagesDir()
            fileManager.listFiles(diaryImagesDir).forEach { src ->
                val name = src.substringAfterLast("/")
                fileManager.copyFile(src, "$targetDir/$name")
                count++
            }
        }

        return count
    }

    private fun cleanup(tempDir: String) {
        try {
            fileManager.listFiles(tempDir).forEach { fileManager.deleteFile(it) }
            fileManager.deleteFile(tempDir)
        } catch (e: Exception) {
            log.w(e) { "Failed to cleanup temp dir: $tempDir" }
        }
    }

    private fun nowMs(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}
