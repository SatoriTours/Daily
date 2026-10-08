package com.dailysatori.service.diary

import com.dailysatori.service.externalfavorites.sha256Hex
import kotlinx.serialization.Serializable

const val DIARY_AUTO_TAG_LIMIT = 3

@Serializable
data class DiaryTagMerge(val from: String, val to: String)

@Serializable
data class DiaryTagVocabulary(
    val names: List<String> = emptyList(),
    val aliases: Map<String, String> = mapOf("感想" to "感悟", "健身锻炼" to "健身", "职业抉择" to "职业选择"),
    val suggestions: List<DiaryTagMerge> = emptyList(),
    val pendingNames: List<String> = emptyList(),
) {
    fun canonical(name: String): String = aliases[name] ?: name
}

@Serializable
data class DiaryTagState(
    val automatic: List<String> = emptyList(),
    val suppressed: List<String> = emptyList(),
    val contentFingerprint: String = "",
    val processedFingerprint: String = "",
    val revision: Long = 0,
)

data class DiaryTagSnapshot(
    val diaryId: Long,
    val content: String,
    val tags: String?,
    val state: DiaryTagState,
    val policy: String?,
    /** 日记串原文版本；为 null 表示未验证版本（兼容旧调用）。 */
    val threadRevision: Long? = null,
)

data class DiaryTagResult(val tags: List<String>, val merges: List<DiaryTagMerge> = emptyList(),
    val pendingTags: List<String> = emptyList())

data class DiaryTagDraft(
    val tags: List<String>,
    val automatic: List<String> = emptyList(),
    val suppressed: List<String> = emptyList(),
    val contentFingerprint: String = "",
    val generatedFingerprint: String? = null,
    val edited: Boolean = false,
    val removed: List<String> = emptyList(),
    val pendingTags: List<String> = emptyList(),
) {
    fun remove(name: String): DiaryTagDraft = copy(tags = tags - name, automatic = automatic - name,
        suppressed = (suppressed + name).distinct(), removed = (removed + name).distinct(), edited = true)

    fun add(name: String, replace: String? = null): DiaryTagDraft {
        val cleaned = cleanDiaryTag(name) ?: return this
        val base = if (replace != null) remove(replace) else this
        val position = tags.indexOf(replace).takeIf { it >= 0 } ?: base.tags.size
        val nextTags = base.tags.toMutableList().apply { add(position.coerceAtMost(size), cleaned) }.distinct()
        val manual = nextTags.filterNot { it in base.automatic && it != cleaned }
        val automatic = (base.automatic - cleaned).take((DIARY_AUTO_TAG_LIMIT - manual.size).coerceAtLeast(0))
        return base.copy(tags = nextTags.filter { it in manual || it in automatic }, automatic = automatic,
            suppressed = base.suppressed - cleaned, removed = base.removed - cleaned,
            pendingTags = base.pendingTags - cleaned, edited = true)
    }

    fun pin(name: String): DiaryTagDraft = copy(tags = listOf(name) + (tags - name),
        automatic = automatic - name, edited = true)

    fun withGenerated(content: String, generated: List<String>): DiaryTagDraft {
        val fingerprint = diaryTagFingerprint(content)
        val blocked = (suppressed.takeIf { contentFingerprint == fingerprint }.orEmpty() + removed).distinct()
        val manual = tags.filterNot { it in automatic }
        val next = generated.filterNot { it in manual || it in blocked }.distinct()
            .take((DIARY_AUTO_TAG_LIMIT - manual.size).coerceAtLeast(0))
        return copy(tags = manual + next, automatic = next, suppressed = blocked,
            contentFingerprint = fingerprint, generatedFingerprint = fingerprint, edited = true)
    }

    fun withGenerated(content: String, result: DiaryTagResult): DiaryTagDraft =
        withGenerated(content, result.tags).copy(pendingTags = result.pendingTags)

    companion object {
        fun from(value: String?, state: DiaryTagState?, content: String, vocabulary: DiaryTagVocabulary): DiaryTagDraft {
            val original = parseDiaryTags(value)
            val automatic = state?.automatic.orEmpty()
            val manual = original.filterNot { it in automatic }.map(vocabulary::canonical)
            val tags = original.map(vocabulary::canonical).distinct()
            return DiaryTagDraft(tags, automatic.map(vocabulary::canonical).distinct().filter { it in tags && it !in manual },
                state?.suppressed.orEmpty().map(vocabulary::canonical), diaryTagFingerprint(content))
        }
    }
}

data class DiaryTagHistory(val current: DiaryTagDraft, private val previous: List<DiaryTagDraft> = emptyList()) {
    val canUndo: Boolean get() = previous.isNotEmpty()

    fun change(next: DiaryTagDraft): DiaryTagHistory = if (next == current) this
        else DiaryTagHistory(next, (previous + current).takeLast(20))

    fun undo(): DiaryTagHistory = previous.lastOrNull()?.let { DiaryTagHistory(it, previous.dropLast(1)) } ?: this
}

fun diaryTagFingerprint(content: String): String = sha256Hex(content)

fun parseDiaryTags(value: String?): List<String> = value.orEmpty().split(',', '，', '\n')
    .map { it.trim().removePrefix("#").trim() }.filter { it.isNotBlank() && it != "null" }.distinct()

fun cleanDiaryTag(value: String): String? = value.trim().removePrefix("#").trim()
    .takeIf { it.isNotEmpty() && it != "null" && it.length <= 24 && it.none { c -> c.isISOControl() || c in ",，;；" } }

fun matchesDiaryTag(tags: String?, selected: String, vocabulary: DiaryTagVocabulary): Boolean =
    parseDiaryTags(tags).any { vocabulary.canonical(it) == vocabulary.canonical(selected) }
