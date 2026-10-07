package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.dailysatori.service.diary.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Diary-only vocabulary and provenance live in the existing, backed-up settings table. */
class DiaryTagRepository(
    private val db: DailySatoriDatabase,
    private val threads: DiaryThreadRepository,
) {
    private val settings = SettingRepository(db)
    private val q get() = db.dailySatoriQueries
    private val json = Json { ignoreUnknownKeys = true }

    fun enabled(): Boolean = settings.get(ENABLED_KEY)?.substringBefore(':') != "false"

    fun setEnabled(enabled: Boolean) = q.transaction {
        val revision = settings.get(ENABLED_KEY)?.substringAfter(':')?.toLongOrNull() ?: 0
        settings.upsert(ENABLED_KEY, "$enabled:${revision + 1}")
    }

    fun vocabulary(): DiaryTagVocabulary {
        val stored = read<DiaryTagVocabulary>(VOCABULARY_KEY) ?: DiaryTagVocabulary()
        return vocabularyFor(stored, tagIndex())
    }

    private fun vocabularyFor(stored: DiaryTagVocabulary, entries: List<DiaryTagIndexEntry>): DiaryTagVocabulary {
        val names = entries.flatMap { parseDiaryTags(it.tags) }
        val canonical = (stored.names + names).map(stored::canonical).distinct().sorted()
        return stored.copy(names = canonical, pendingNames = stored.pendingNames.map(stored::canonical).distinct().filterNot { it in canonical })
    }

    fun observeVocabulary(): Flow<DiaryTagVocabulary> = observeCatalog().map { it.vocabulary }.distinctUntilChanged()

    fun observeCatalog(): Flow<DiaryTagCatalog> = flow {
        var previous = emptyMap<Long, DiaryTagIndexEntry>()
        var previousAliases: Map<String, String>? = null
        var counts = emptyMap<String, Int>()
        val index = q.selectDiaryTagIndex().asFlow().mapToList(Dispatchers.IO)
            .map { rows -> rows.map { DiaryTagIndexEntry(it.id, it.tags, it.has_content == 1L) } }.distinctUntilChanged()
        combine(index, observeValue(VOCABULARY_KEY), observeValue(ENABLED_KEY), observeValue(UNDO_KEY)) { entries, raw, policy, undo ->
            CatalogInput(entries, decoded<DiaryTagVocabulary>(raw) ?: DiaryTagVocabulary(), policy, undo != null)
        }.collect { input ->
            val vocabulary = vocabularyFor(input.vocabulary, input.entries)
            val current = input.entries.associateBy { it.id }
            if (previousAliases != vocabulary.aliases) { previous = emptyMap(); counts = emptyMap() }
            counts = updateDiaryTagCounts(previous, current, counts, vocabulary)
            emit(catalogFor(input.entries, vocabulary, counts, input.policy, input.canUndo))
            previous = current
            previousAliases = vocabulary.aliases
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    fun currentCatalog(): DiaryTagCatalog = q.transactionWithResult {
        val entries = tagIndex()
        val vocabulary = vocabularyFor(read<DiaryTagVocabulary>(VOCABULARY_KEY) ?: DiaryTagVocabulary(), entries)
        val counts = updateDiaryTagCounts(emptyMap(), entries.associateBy { it.id }, emptyMap(), vocabulary)
        catalogFor(entries, vocabulary, counts, settings.get(ENABLED_KEY), canUndoMerge())
    }

    private fun catalogFor(entries: List<DiaryTagIndexEntry>, vocabulary: DiaryTagVocabulary, counts: Map<String, Int>,
        policy: String?, canUndo: Boolean): DiaryTagCatalog = DiaryTagCatalog(vocabulary, counts,
        entries.filter { it.hasContent && parseDiaryTags(it.tags).isEmpty() }.map { it.id },
        policy?.substringBefore(':') != "false", canUndo)

    private fun tagIndex(): List<DiaryTagIndexEntry> = q.selectDiaryTagIndex().executeAsList()
        .map { DiaryTagIndexEntry(it.id, it.tags, it.has_content == 1L) }

    private fun observeValue(key: String): Flow<String?> = q.selectSettingByKey(key).asFlow()
        .mapToOneOrNull(Dispatchers.IO).map { it?.value_ }.distinctUntilChanged()

    fun observeEditor(id: Long): Flow<DiaryTagEditorSnapshot> = combine(
        q.selectDiaryThreadEntries(id).asFlow().mapToList(Dispatchers.IO), observeValue(stateKey(id)),
    ) { entries, raw ->
        DiaryTagEditorSnapshot(id, entries.firstOrNull()?.tags, stateFor(decoded<DiaryTagState>(raw), renderDiaryThreadContent(entries)))
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    fun observeState(id: Long): Flow<DiaryTagState> = observeEditor(id).map { it.state }.distinctUntilChanged()

    fun canonical(name: String): String = vocabulary().canonical(name)

    fun tags(id: Long): List<String> = parseDiaryTags(q.selectDiaryById(id).executeAsOneOrNull()?.tags)

    fun state(id: Long): DiaryTagState {
        val stored = read<DiaryTagState>(stateKey(id)) ?: DiaryTagState()
        return stateFor(stored, threads.getSource(id)?.content.orEmpty())
    }

    private fun stateFor(storedValue: DiaryTagState?, content: String): DiaryTagState {
        val stored = storedValue ?: DiaryTagState()
        val fingerprint = diaryTagFingerprint(content)
        return if (stored.contentFingerprint == fingerprint) stored else stored.copy(
            contentFingerprint = fingerprint, suppressed = emptyList(),
        )
    }

    fun prepare(id: Long, force: Boolean = false): DiaryTagSnapshot? = q.transactionWithResult {
        val diary = q.selectDiaryById(id).executeAsOneOrNull() ?: return@transactionWithResult null
        if (diary.parent_diary_id != null) return@transactionWithResult null
        val source = threads.getSource(id) ?: return@transactionWithResult null
        val state = stateFor(read<DiaryTagState>(stateKey(id)), source.content)
        if (source.content.isBlank() || (!force && (!enabled() || state.processedFingerprint == state.contentFingerprint))) {
            return@transactionWithResult null
        }
        val manual = parseDiaryTags(diary.tags).filterNot { it in state.automatic }
        if (manual.size >= DIARY_AUTO_TAG_LIMIT) return@transactionWithResult null
        DiaryTagSnapshot(id, source.content, diary.tags, state, settings.get(ENABLED_KEY), source.revision)
    }

    fun apply(snapshot: DiaryTagSnapshot, generated: List<String>): Boolean = q.transactionWithResult {
        val diary = q.selectDiaryById(snapshot.diaryId).executeAsOneOrNull() ?: return@transactionWithResult false
        val source = threads.getSource(snapshot.diaryId) ?: return@transactionWithResult false
        val current = stateFor(read<DiaryTagState>(stateKey(snapshot.diaryId)), source.content)
        if (source.content != snapshot.content || diary.tags != snapshot.tags || current.revision != snapshot.state.revision ||
            settings.get(ENABLED_KEY) != snapshot.policy) return@transactionWithResult false
        if (snapshot.threadRevision != null && source.revision != snapshot.threadRevision) return@transactionWithResult false
        val vocabulary = vocabulary()
        val manual = parseDiaryTags(diary.tags).filterNot { it in current.automatic }.map(vocabulary::canonical).distinct()
        val automatic = generated.mapNotNull(::cleanDiaryTag).map(vocabulary::canonical).distinct()
            .filterNot { it in manual || it in current.suppressed.map(vocabulary::canonical) }
            .take((DIARY_AUTO_TAG_LIMIT - manual.size).coerceAtLeast(0))
        writeTags(snapshot.diaryId, manual + automatic)
        write(stateKey(snapshot.diaryId), current.copy(automatic = automatic,
            processedFingerprint = current.contentFingerprint, revision = current.revision + 1))
        write(VOCABULARY_KEY, vocabulary.copy(names = (vocabulary.names + automatic).distinct()))
        true
    }

    fun edit(id: Long, desired: List<String>, pinned: Set<String> = emptySet()) = q.transaction {
        if (q.selectDiaryById(id).executeAsOneOrNull() == null) return@transaction
        val vocabulary = vocabulary()
        val canonical = normalizedSelection(id, desired, vocabulary)
        val current = state(id)
        val automatic = current.automatic.map(vocabulary::canonical)
        val suppressed = (current.suppressed + automatic.filterNot { it in canonical }).distinct()
        writeTags(id, canonical)
        write(stateKey(id), current.copy(automatic = automatic.filter { it in canonical && it !in pinned },
            suppressed = suppressed, revision = current.revision + 1))
        write(VOCABULARY_KEY, vocabulary.copy(names = (vocabulary.names + canonical).distinct()))
    }

    fun saveEditedDiary(id: Long, content: String, desired: List<String>?, mood: String?, images: String?,
        draft: DiaryTagDraft? = null) = q.transaction {
        val diary = q.selectDiaryById(id).executeAsOneOrNull() ?: return@transaction
        // Record removals against the old body; a body change deliberately resets suppression.
        if (desired != null) {
            if (draft == null) edit(id, desired) else {
                val vocabulary = vocabulary()
                val canonical = normalizedSelection(id, desired, vocabulary)
                writeTags(id, canonical)
                write(VOCABULARY_KEY, vocabulary.copy(names = (vocabulary.names + canonical).distinct()))
            }
        }
        val tags = q.selectDiaryById(id).executeAsOne().tags
        q.updateDiary(content, tags, mood, images, kotlinx.datetime.Clock.System.now().toEpochMilliseconds(), diary.id)
        if (draft != null) saveDraft(id, draft)
    }

    fun saveDraft(id: Long, draft: DiaryTagDraft) = q.transaction {
        if (!draft.edited) return@transaction
        val vocabulary = vocabulary()
        val current = state(id)
        val automatic = draft.automatic.map(vocabulary::canonical).filter { it in tags(id) }.distinct()
        val blocked = (draft.suppressed.takeIf { draft.contentFingerprint == current.contentFingerprint }.orEmpty() + draft.removed)
            .map(vocabulary::canonical).distinct()
        write(stateKey(id), current.copy(automatic = automatic, suppressed = (current.suppressed + blocked).distinct(),
            processedFingerprint = draft.generatedFingerprint?.takeIf { it == current.contentFingerprint }
                ?: current.processedFingerprint, revision = current.revision + 1))
        if (draft.generatedFingerprint == current.contentFingerprint) suggestPending(draft.pendingTags)
    }

    /** 基线指纹覆盖整个日记串原文；续写变化也会触发主日记重新打标。 */
    fun changedDiaryIds(enqueue: (List<Long>) -> Unit = {}): List<Long> = q.transactionWithResult {
        val before = read<Map<Long, String>>(BASELINE_KEY)
        val current = q.selectDiaryRoots().executeAsList()
            .associate { it.id to diaryTagFingerprint(threads.getSource(it.id)?.content.orEmpty()) }
        val changed = if (before == null) emptyList() else current.filter { (id, hash) -> before[id] != hash }.keys.toList()
        enqueue(changed)
        write(BASELINE_KEY, current)
        changed
    }

    fun suggest(merges: List<DiaryTagMerge>) = q.transaction {
        val vocabulary = vocabulary()
        val additions = merges.filter { vocabulary.canonical(it.from) != vocabulary.canonical(it.to) }
        write(VOCABULARY_KEY, vocabulary.copy(suggestions = (vocabulary.suggestions + additions).distinct().takeLast(50)))
    }

    fun dismissSuggestion(merge: DiaryTagMerge) = q.transaction {
        val vocabulary = vocabulary()
        write(VOCABULARY_KEY, vocabulary.copy(suggestions = vocabulary.suggestions - merge))
    }

    fun suggestPending(names: List<String>) = q.transaction {
        val vocabulary = vocabulary()
        val candidates = names.mapNotNull(::cleanDiaryTag).map(vocabulary::canonical).filterNot { it in vocabulary.names }
        write(VOCABULARY_KEY, vocabulary.copy(pendingNames = (vocabulary.pendingNames + candidates).distinct().takeLast(50)))
    }

    fun approvePending(name: String) = q.transaction {
        val vocabulary = vocabulary()
        if (name in vocabulary.pendingNames) write(VOCABULARY_KEY, vocabulary.copy(
            names = (vocabulary.names + name).distinct(), pendingNames = vocabulary.pendingNames - name))
    }

    fun dismissPending(name: String) = q.transaction {
        val vocabulary = vocabulary()
        write(VOCABULARY_KEY, vocabulary.copy(pendingNames = vocabulary.pendingNames - name))
    }

    fun mergeImpact(from: String, to: String): Int {
        if (canonical(from) == canonical(to)) return 0
        val vocabulary = vocabulary()
        return tagIndex().count { matchesDiaryTag(it.tags, from, vocabulary) }
    }

    fun merge(from: String, to: String) = q.transaction {
        val before = vocabulary()
        val source = before.canonical(requireNotNull(cleanDiaryTag(from)))
        val target = before.canonical(requireNotNull(cleanDiaryTag(to)))
        require(source != target)
        val aliases = before.aliases.mapValues { (_, value) -> if (value == source) target else value } + (source to target)
        val after = before.copy(names = (before.names - source + target).distinct(), aliases = aliases,
            suggestions = before.suggestions.filterNot { (aliases[it.from] ?: it.from) == (aliases[it.to] ?: it.to) })
        val entries = tagIndex().filter { matchesDiaryTag(it.tags, source, before) }.map { diary ->
            val oldState = state(diary.id)
            val newTags = parseDiaryTags(diary.tags).map(after::canonical).distinct()
            val manual = parseDiaryTags(diary.tags).filterNot { it in oldState.automatic }.map(after::canonical)
            val newState = oldState.copy(automatic = oldState.automatic.map(after::canonical).distinct().filterNot { it in manual },
                suppressed = oldState.suppressed.map(after::canonical).distinct(), revision = oldState.revision + 1)
            writeTags(diary.id, newTags)
            write(stateKey(diary.id), newState)
            MergeEntry(diary.id, diary.tags, oldState, newTags.joinToString(",").ifBlank { null }, newState)
        }
        write(VOCABULARY_KEY, after)
        write(UNDO_KEY, MergeUndo(before, after, entries))
    }

    fun canUndoMerge(): Boolean = settings.get(UNDO_KEY) != null

    fun undoMerge(): Boolean = q.transactionWithResult {
        val undo = read<MergeUndo>(UNDO_KEY) ?: return@transactionWithResult false
        val stored = read<DiaryTagVocabulary>(VOCABULARY_KEY)
            ?: return@transactionWithResult false
        val changedAliases = (undo.before.aliases.keys + undo.after.aliases.keys)
            .filter { undo.before.aliases[it] != undo.after.aliases[it] }
        if (changedAliases.any { stored.aliases[it] != undo.after.aliases[it] } || undo.entries.any { entry ->
                q.selectDiaryById(entry.id).executeAsOneOrNull()?.tags != entry.afterTags || state(entry.id) != entry.afterState
            }) return@transactionWithResult false
        undo.entries.forEach { entry ->
            writeTags(entry.id, parseDiaryTags(entry.beforeTags))
            write(stateKey(entry.id), entry.beforeState.copy(revision = entry.afterState.revision + 1))
        }
        val aliases = stored.aliases.toMutableMap()
        changedAliases.forEach { key ->
            val original = undo.before.aliases[key]
            if (original == null) aliases.remove(key) else aliases[key] = original
        }
        val restored = stored.copy(aliases = aliases)
        val used = tagIndex().flatMap { parseDiaryTags(it.tags).map(restored::canonical) }.toSet()
        val introduced = undo.after.names - undo.before.names.toSet()
        val names = (stored.names.filterNot { it in introduced && it !in used } + undo.before.names)
            .map(restored::canonical).distinct()
        val suggestions = (stored.suggestions + (undo.before.suggestions - undo.after.suggestions.toSet())).distinct()
            .filter { restored.canonical(it.from) != restored.canonical(it.to) }
        write(VOCABULARY_KEY, restored.copy(names = names, suggestions = suggestions))
        settings.delete(UNDO_KEY)
        true
    }

    fun deleteState(id: Long) = settings.delete(stateKey(id))

    private fun writeTags(id: Long, tags: List<String>) = q.updateDiaryTags(tags.joinToString(",").ifBlank { null }, id)

    private fun normalizedSelection(id: Long, desired: List<String>, vocabulary: DiaryTagVocabulary): List<String> {
        val existing = tags(id)
        return desired.mapNotNull { name -> name.takeIf { it in existing } ?: cleanDiaryTag(name) }
            .map(vocabulary::canonical).distinct()
    }

    private inline fun <reified T> read(key: String): T? = decoded(settings.get(key))

    private inline fun <reified T> decoded(value: String?): T? = value?.let {
        runCatching { json.decodeFromString<T>(it) }.getOrNull()
    }

    private inline fun <reified T> write(key: String, value: T) = settings.upsert(key, Json.encodeToString(value))

    private fun stateKey(id: Long) = "diary_tag_state_v1_$id"

    private companion object {
        const val VOCABULARY_KEY = "diary_tag_vocabulary_v1"
        const val ENABLED_KEY = "diary_auto_tags"
        const val UNDO_KEY = "diary_tag_merge_undo_v1"
        const val BASELINE_KEY = "diary_tag_content_baseline_v1"
    }
}

@Serializable
private data class MergeEntry(val id: Long, val beforeTags: String?, val beforeState: DiaryTagState,
    val afterTags: String?, val afterState: DiaryTagState)

@Serializable
private data class MergeUndo(val before: DiaryTagVocabulary, val after: DiaryTagVocabulary, val entries: List<MergeEntry>)

private data class CatalogInput(val entries: List<DiaryTagIndexEntry>, val vocabulary: DiaryTagVocabulary,
    val policy: String?, val canUndo: Boolean)
