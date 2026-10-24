package com.dailysatori.service.assistant.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

/** The only model-facing database surface. SQL fragments below come exclusively from host-owned definitions. */
class AssistantDataGateway(private val driver: SqlDriver) {
    private val redactor = AssistantCredentialRedactor(driver)

    fun redact(text: String): String = redactor.snapshot()(text)
    fun streamRedactor(): AssistantStreamRedactor = redactor.stream()

    fun catalog(): JsonObject = buildJsonObject {
        put("ok", true); put("read_only", true); put("max_limit", MAX_LIMIT); put("read_fragment_chars", READ_SIZE)
        put("sources", buildJsonArray { assistantSources.values.forEach { source -> add(buildJsonObject {
            put("source", source.name)
            put("fields", buildJsonArray { source.fields.forEach { field -> add(buildJsonObject {
                put("name", field); put("type", if (field in source.numeric) "number" else "string")
                put("searchable", field in source.searchable)
            }) } })
        }) } })
    }

    suspend fun query(request: AssistantQuery): JsonObject = safeRead {
        val source = source(request.source)
        val fields = request.fields.ifEmpty { source.fields.toList() }.distinct()
        require(fields.isNotEmpty() && fields.all { it in source.fields })
        val order = request.orderBy ?: source.order
        require(order in source.fields)
        val limit = request.limit.coerceIn(1, MAX_LIMIT)
        require(request.offset in 0..MAX_OFFSET)
        val args = mutableListOf<Argument>()
        val condition = conditions(source, request.filters, args)
        val sql = "SELECT ${fields.joinToString(",") { selectField(source, it) }} FROM ${source.table} r " +
            "WHERE $condition ORDER BY ${source.expression(order)} ${if (request.descending) "DESC" else "ASC"}, ${source.expression(source.id)} ASC LIMIT ? OFFSET ?"
        args += NumberArgument((limit + 1).toLong()); args += NumberArgument(request.offset)
        val scrub = redactor.snapshot()
        val candidates = rows(sql, args) { cursor -> buildJsonObject {
            fields.forEachIndexed { i, field -> put(field, value(cursor, i, field in source.numeric, scrub)) }
        } }
        val selected = boundedRows(candidates.take(limit))
        page(selected, request.offset, candidates.size > selected.size, truncated = selected.any { row ->
            row.values.any { it is JsonPrimitive && it.isString && it.content.length >= PREVIEW_SIZE }
        })
    }

    suspend fun aggregate(request: AssistantAggregate): JsonObject = safeRead {
        val source = source(request.source)
        require(request.groupBy.size <= 4 && request.groupBy.all { it in source.fields && it != source.content })
        require(request.measures.isNotEmpty() && request.measures.size <= 6)
        val groups = request.groupBy.distinct()
        val measures = request.measures.distinct().map { measure ->
            if (measure == "count") "COUNT(*)" else {
                val parts = measure.split(':')
                require(parts.size == 2 && parts[0] in setOf("sum", "avg", "min", "max") && parts[1] in source.numeric)
                "${parts[0].uppercase()}(${source.expression(parts[1])})"
            }
        }
        val args = mutableListOf<Argument>()
        val condition = conditions(source, request.filters, args)
        val selections = groups.map { selectField(source, it) } + measures
        val groupSql = if (groups.isEmpty()) "" else " GROUP BY ${groups.joinToString(",") { source.expression(it) }}"
        val sql = "SELECT ${selections.joinToString(",")} FROM ${source.table} r WHERE $condition$groupSql LIMIT ${MAX_LIMIT + 1}"
        val scrub = redactor.snapshot()
        val candidates = rows(sql, args) { cursor -> buildJsonObject {
            groups.forEachIndexed { i, field -> put(field, value(cursor, i, field in source.numeric, scrub)) }
            request.measures.distinct().forEachIndexed { i, measure ->
                put(measure, if (measure == "count") cursor.getLong(groups.size + i)?.let(::JsonPrimitive) ?: JsonNull
                    else cursor.getDouble(groups.size + i)?.let(::JsonPrimitive) ?: JsonNull)
            }
        } }
        page(boundedRows(candidates.take(MAX_LIMIT)), 0, candidates.size > MAX_LIMIT)
    }

    suspend fun search(query: String, sources: List<String> = emptyList(), limit: Int = 20, offset: Long = 0): JsonObject = safeRead {
        require(query.isNotBlank() && query.length <= 200 && offset in 0..1000)
        val selected = if (sources.isEmpty()) assistantSources.values.filter { it.searchable.isNotEmpty() }
            else sources.distinct().map(::source).filter { it.searchable.isNotEmpty() }
        val scrub = redactor.snapshot()
        // Avoid returning record-existence evidence when probing configured secrets or sensitive URL material.
        if (scrub(query) != query) return@safeRead page(emptyList(), offset, false)
        val terms = query.trim().split(Regex("\\s+")).take(6)
        val size = limit.coerceIn(1, MAX_LIMIT)
        val merged = selected.flatMap { source -> searchSource(source, terms, offset + size + 1, scrub) }
            .sortedWith(compareByDescending<SearchRow> { it.time }.thenBy { it.source }.thenBy { it.id })
        val candidates = merged.drop(offset.toInt()).take(size + 1)
        page(candidates.take(size).map { row -> buildJsonObject {
            put("source", row.source); put("id", row.id); put("snippet", row.text); put("offset", row.offset)
        } }, offset, candidates.size > size, true)
    }

    suspend fun read(source: String, id: String, offset: Int = 0): JsonObject = safeRead {
        val spec = source(source)
        require(id.isNotBlank() && id.length <= 128 && offset in 0..MAX_OFFSET)
        val field = spec.content ?: spec.fields.firstOrNull { it !in spec.numeric } ?: spec.id
        val expression = spec.expression(field)
        val sql = "SELECT CAST($expression AS TEXT) FROM ${spec.table} r " +
            "WHERE CAST(${spec.expression(spec.id)} AS TEXT) = ? AND ${exclusions(spec)} LIMIT 1"
        val found = rows(sql, listOf(TextArgument(id))) { it.getString(0).orEmpty() }
            .singleOrNull() ?: return@safeRead failure("not_found")
        // Sanitize before cropping: credentials/URLs can cross arbitrary preview or page boundaries.
        // Only one local record is materialized; the model still receives at most READ_SIZE characters.
        val safe = redactor.snapshot()(found)
        val hasMore = offset.toLong() + READ_SIZE < safe.length
        buildJsonObject {
            put("ok", true); put("source", source); put("id", id); put("field", field)
            put("content", safe.drop(offset).take(READ_SIZE)); put("offset", offset)
            put("has_more", hasMore); put("truncated", hasMore)
            put("next_offset", if (hasMore) JsonPrimitive(offset + READ_SIZE) else JsonNull)
        }
    }

    private fun searchSource(spec: AssistantSource, terms: List<String>, fetch: Long, scrub: (String) -> String): List<SearchRow> {
        val args = mutableListOf<Argument>()
        val filters = terms.joinToString(" AND ") { term ->
            spec.searchable.joinToString(" OR ", "(", ")") { field ->
                args += TextArgument(like(term))
                "${spec.expression(field)} LIKE ? ESCAPE '\\'"
            }
        }
        val preview = spec.expression(spec.content ?: spec.searchable.first())
        val sql = "SELECT CAST(${spec.expression(spec.id)} AS TEXT), $preview, ${spec.expression(spec.order)} " +
            "FROM ${spec.table} r WHERE ${exclusions(spec)} AND $filters ORDER BY ${spec.expression(spec.order)} DESC, ${spec.expression(spec.id)} ASC LIMIT ?"
        args += NumberArgument(fetch)
        return rows(sql, args) {
            val text = scrub(it.getString(1).orEmpty())
            val match = terms.map { term -> text.indexOf(term, ignoreCase=true) }.filter { it >= 0 }.minOrNull() ?: 0
            val start = (match - 200).coerceAtLeast(0)
            SearchRow(spec.name, requireNotNull(it.getString(0)), text.drop(start).take(PREVIEW_SIZE), it.getLong(2) ?: 0, start)
        }
    }

    private fun conditions(spec: AssistantSource, filters: List<AssistantFilter>, args: MutableList<Argument>): String {
        require(filters.size <= 16)
        val constraints = filters.map { filter ->
            val expression = spec.expression(filter.field)
            val operator = when (filter.operator) { "eq" -> "="; "contains" -> "LIKE"; "gte" -> ">="; "lte" -> "<="; else -> error("Invalid operator") }
            require(filter.value !is JsonNull && filter.value.content.length <= 6000)
            if (filter.operator == "contains") {
                require(filter.field !in spec.numeric)
                args += TextArgument(like(filter.value.content))
                "$expression LIKE ? ESCAPE '\\'"
            } else {
                args += if (filter.field in spec.numeric) {
                    val number = requireNotNull(filter.value.content.toDoubleOrNull())
                    require(number.isFinite())
                    DecimalArgument(number)
                } else TextArgument(filter.value.content)
                "$expression $operator ?"
            }
        }
        return (listOf(exclusions(spec)) + constraints).joinToString(" AND ") { "($it)" }
    }

    private fun exclusions(spec: AssistantSource): String {
        val checks = mutableListOf("NOT EXISTS (SELECT 1 FROM assistant_memory_exclusion x WHERE x.source_type = '${spec.name}' AND x.source_id = CAST(${spec.expression(spec.id)} AS TEXT))")
        spec.parent?.let { (type, key) -> checks += "NOT EXISTS (SELECT 1 FROM assistant_memory_exclusion x WHERE x.source_type = '$type' AND x.source_id = CAST(r.$key AS TEXT))" }
        if (spec.name == "memory_entry") {
            checks += "r.assistant_state = 'active'"
            checks += "NOT EXISTS (SELECT 1 FROM assistant_memory_exclusion x WHERE x.source_type = r.source_type AND x.source_id = CAST(r.source_id AS TEXT))"
            // Legacy deletion paths may not have created a tombstone; orphaned derived memory is not evidence.
            listOf("chat" to "chat_conversation", "article" to "article", "diary" to "diary", "book" to "book").forEach { (type, table) ->
                checks += "(COALESCE(r.source_type,'') != '$type' OR EXISTS (SELECT 1 FROM $table origin WHERE origin.id = r.source_id))"
            }
        }
        if (spec.name in setOf("weekly_summary", "diary_month_summary")) {
            // These legacy summaries have no per-sentence provenance; conservative invalidation.
            checks += "NOT EXISTS (SELECT 1 FROM assistant_memory_exclusion WHERE source_type IN ('chat','diary','article','book','book_viewpoint'))"
        }
        if (spec.name == "idea_topic") {
            checks += """NOT EXISTS (SELECT 1 FROM idea_topic_source s WHERE s.topic_id=r.id AND (
                s.source_type NOT IN ('diary','news_opportunity') OR EXISTS (
                    SELECT 1 FROM assistant_memory_exclusion x WHERE x.source_type=s.source_type AND x.source_id=s.source_record_id
                ) OR (s.source_type='diary' AND NOT EXISTS (SELECT 1 FROM diary d WHERE CAST(d.id AS TEXT)=s.source_record_id))
            ))""".trimIndent()
        }
        if (spec.name == "async_task") checks += "r.type NOT IN ('sms_reminder_parse','phone_assistant','phone_message_parse')"
        return checks.joinToString(" AND ")
    }

    private fun selectField(spec: AssistantSource, field: String): String = spec.expression(field)

    private fun value(cursor: SqlCursor, index: Int, numeric: Boolean, scrub: (String) -> String): JsonElement =
        if (numeric) cursor.getLong(index)?.let(::JsonPrimitive) ?: JsonNull
        else cursor.getString(index)?.let { JsonPrimitive(scrub(it).take(PREVIEW_SIZE)) } ?: JsonNull

    private fun boundedRows(rows: List<JsonObject>): List<JsonObject> {
        var budget = 60_000
        return rows.takeWhile { row -> (row.toString().length <= budget).also { if (it) budget -= row.toString().length } }
    }

    private fun page(rows: List<JsonObject>, offset: Long, more: Boolean, truncated: Boolean = false): JsonObject = buildJsonObject {
        put("ok", true); put("rows", JsonArray(rows)); put("has_more", more); put("truncated", truncated)
        put("next_offset", if (more) JsonPrimitive(offset + rows.size) else JsonNull)
    }

    private fun source(name: String): AssistantSource = requireNotNull(assistantSources[name])
    private fun like(text: String): String = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    private fun failure(code: String): JsonObject = buildJsonObject { put("ok", false); put("error", code) }
    private inline fun safeRead(block: () -> JsonObject): JsonObject = try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure("invalid_or_unavailable_read") }

    private fun <T> rows(sql: String, args: List<Argument>, map: (SqlCursor) -> T): List<T> = driver.executeQuery(null, sql, { cursor ->
        val result = mutableListOf<T>()
        while (cursor.next().value) result += map(cursor)
        QueryResult.Value(result)
    }, args.size) { args.forEachIndexed { index, arg -> arg.bind(this, index) } }.value

    private sealed interface Argument { fun bind(statement: SqlPreparedStatement, index: Int) }
    private data class TextArgument(val value: String) : Argument { override fun bind(statement: SqlPreparedStatement, index: Int) = statement.bindString(index, value) }
    private data class NumberArgument(val value: Long) : Argument { override fun bind(statement: SqlPreparedStatement, index: Int) = statement.bindLong(index, value) }
    private data class DecimalArgument(val value: Double) : Argument { override fun bind(statement: SqlPreparedStatement, index: Int) = statement.bindDouble(index, value) }
    private data class SearchRow(val source: String, val id: String, val text: String, val time: Long, val offset: Int)

    private companion object { const val MAX_LIMIT = 100; const val MAX_OFFSET = 1_000_000L; const val READ_SIZE = 6000; const val PREVIEW_SIZE = 1000 }
}
