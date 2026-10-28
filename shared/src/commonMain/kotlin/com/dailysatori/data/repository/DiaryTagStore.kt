package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import com.dailysatori.service.diary.*
import com.dailysatori.service.migration.DiaryTagMigration
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock

/** The only writer of current tag assignments and append-only decisions. */
internal class DiaryTagStore(private val db:DailySatoriDatabase) {
    private val q get()=db.dailySatoriQueries

    fun signal()=combine(q.selectDiaryTagAssignments().asFlow(),q.selectDiaryTagDefinitions().asFlow(),
        q.selectDiaryTagAliases().asFlow(),q.selectDiaryTagCatalog().asFlow()) { _,_,_,_ -> Unit }

    fun tagsByDiary():Map<Long,List<String>> = q.selectDiaryTagAssignments().executeAsList()
        .filter { it.state=="active" }.groupBy { it.diary_id }.mapValues { (_,rows) -> rows.map { it.name } }

    fun tags(id:Long)=q.selectDiaryTagAssignmentsForDiary(id).executeAsList().filter { it.state=="active" }.map { it.name }

    fun diaryIdsMatching(name:String,vocabulary:DiaryTagVocabulary)=q.selectDiaryTagAssignments().executeAsList()
        .filter { vocabulary.canonical(it.name)==name }.map { it.diary_id }.toSet()

    private fun source(id:Long):Pair<String,Long?> {
        val diary=q.selectDiaryById(id).executeAsOneOrNull()
        val content=if(diary?.parent_diary_id==null) renderDiaryThreadContent(q.selectDiaryThreadEntries(id).executeAsList()) else ""
        return diaryTagFingerprint(content) to q.selectDiaryThreadRevision(id).executeAsOneOrNull()?.revision
    }

    fun state(id:Long):DiaryTagState {
        val (fingerprint,revision)=source(id)
        val processing=q.selectDiaryTagProcessing(id).executeAsOneOrNull()
        val rows=q.selectDiaryTagAssignmentsForDiary(id).executeAsList()
        return DiaryTagState(rows.filter { it.state=="active" && it.origin=="automatic" }.map { it.name },
            rows.filter { it.state=="suppressed" && revision!=null && it.source_revision==revision }.map { it.name },
            fingerprint,fingerprint.takeIf { revision!=null && processing?.processed_revision==revision }.orEmpty(),
            processing?.edit_revision ?: 0)
    }

    fun editor(id:Long,content:String,rows:List<com.dailysatori.shared.db.SelectDiaryTagEditorState>):DiaryTagEditorSnapshot {
        val fingerprint=diaryTagFingerprint(content)
        val first=rows.firstOrNull()
        val revision=first?.body_revision
        val automatic=rows.filter { it.state=="active" && it.origin=="automatic" }.mapNotNull { it.name }
        val suppressed=rows.filter { it.state=="suppressed" && revision!=null && it.source_revision==revision }.mapNotNull { it.name }
        val state=DiaryTagState(automatic,suppressed,fingerprint,
            fingerprint.takeIf { revision!=null && first?.processed_revision==revision }.orEmpty(),first?.edit_revision ?: 0)
        return DiaryTagEditorSnapshot(id,rows.filter { it.state=="active" }.mapNotNull { it.name }.joinToString(",").ifBlank { null },state)
    }

    fun vocabulary():DiaryTagVocabulary {
        val catalog=q.selectDiaryTagCatalog().executeAsOneOrNull() ?: return DiaryTagVocabulary()
        return DiaryTagVocabulary(q.selectDiaryTagDefinitions().executeAsList().filter { it.vocabulary_position!=null }
            .sortedBy { it.vocabulary_position }.map { it.name },
            q.selectDiaryTagAliases().executeAsList().associate { it.alias to it.name },
            Json.decodeFromString(catalog.suggestions_json),Json.decodeFromString(catalog.pending_names_json))
    }

    private fun ensureCatalog() {
        if(q.selectDiaryTagCatalog().executeAsOneOrNull()==null) {
            q.ensureDiaryTagCatalog()
            putVocabulary(DiaryTagVocabulary())
        }
    }

    fun writeVocabulary(value:DiaryTagVocabulary)=q.transaction { ensureCatalog(); putVocabulary(value) }

    private fun putVocabulary(value:DiaryTagVocabulary) {
        val aliases=DiaryTagMigration.flattenAliases(value.aliases)
        q.clearDiaryTagVocabularyPositions()
        (value.names+aliases.values).distinct().forEach(q::ensureDiaryTagDefinition)
        value.names.forEachIndexed { index,name -> q.setDiaryTagVocabularyPosition(index.toLong(),name) }
        q.clearDiaryTagAliases()
        aliases.forEach { (alias,name) -> q.insertDiaryTagAlias(alias,name) }
        q.setDiaryTagVocabulary(Json.encodeToString(value.pendingNames),Json.encodeToString(value.suggestions))
    }

    fun undo()=q.selectDiaryTagCatalog().executeAsOneOrNull()?.merge_undo_json
    fun baseline()=q.selectDiaryTagCatalog().executeAsOneOrNull()?.baseline_json
    fun writeUndo(value:String?)=q.transaction { ensureCatalog(); q.setDiaryTagUndo(value) }
    fun writeBaseline(value:String?)=q.transaction { ensureCatalog(); q.setDiaryTagBaseline(value) }

    fun save(id:Long,tags:List<String>,state:DiaryTagState)=q.transaction {
        ensureCatalog()
        val old=q.selectDiaryTagAssignmentsForDiary(id).executeAsList()
        val (fingerprint,bodyRevision)=source(id)
        val editRevision=maxOf(state.revision,(q.selectDiaryTagProcessing(id).executeAsOneOrNull()?.edit_revision ?: 0)+1)
        val suppressed=state.suppressed.filterNot { it in tags }.distinct()
        val decisions=tags.mapIndexed { index,name -> Decision(name,"active",
            if(name in state.automatic) "automatic" else "manual",index.toLong(),bodyRevision) }+
            suppressed.mapIndexed { index,name -> Decision(name,"suppressed","automatic",index.toLong(),bodyRevision) }
        q.deleteDiaryTagAssignments(id)
        decisions.forEach { next ->
            q.ensureDiaryTagDefinition(next.name)
            val previous=old.find { it.name==next.name && it.origin==next.origin && it.state==next.state }
            val sourceRevision=if(next.origin=="manual") null else if(
                next.state=="active" && state.processedFingerprint!=fingerprint && previous!=null
            ) previous.source_revision else next.sourceRevision
            q.insertDiaryTagAssignment(id,next.origin,next.state,next.position,sourceRevision,next.name)
            decision(id,next.copy(sourceRevision=sourceRevision),fingerprint,bodyRevision,editRevision)
        }
        old.filter { row -> decisions.none { it.name==row.name } }.forEach {
            decision(id,Decision(it.name,"removed",it.origin,it.position,it.source_revision),fingerprint,bodyRevision,editRevision)
        }
        q.upsertDiaryTagProcessing(id,bodyRevision.takeIf { state.processedFingerprint==fingerprint },editRevision)
    }

    /** Fresh import preserves original diary timestamps and does not manufacture an edit event. */
    fun initializeImported(id:Long,names:List<String>)=q.transaction {
        check(q.selectDiaryTagAssignmentsForDiary(id).executeAsList().isEmpty() &&
            q.selectDiaryTagProcessing(id).executeAsOneOrNull()==null)
        if(names.isEmpty()) return@transaction
        ensureCatalog()
        names.distinct().forEachIndexed { index,name ->
            q.ensureDiaryTagDefinition(name)
            q.insertDiaryTagAssignment(id,"manual","active",index.toLong(),null,name)
            q.insertDiaryTagDecision(id,"initialize:$id","active","manual",index.toLong(),null,null,0,null,name)
        }
        q.upsertDiaryTagProcessing(id,null,0)
    }

    fun deleteDomain(id:Long)=q.transaction {
        q.deleteDiaryTagAssignments(id)
        q.deleteDiaryTagHistory(id)
        q.deleteDiaryTagProcessing(id)
    }

    private fun decision(id:Long,value:Decision,fingerprint:String,bodyRevision:Long?,editRevision:Long) {
        q.insertDiaryTagDecision(id,"edit:$id:$editRevision",value.state,value.origin,value.position,value.sourceRevision,
            fingerprint.takeIf { value.origin=="automatic" && value.sourceRevision==bodyRevision },editRevision,
            Clock.System.now().toEpochMilliseconds(),value.name)
    }

    private data class Decision(val name:String,val state:String,val origin:String,val position:Long,val sourceRevision:Long?)
}
