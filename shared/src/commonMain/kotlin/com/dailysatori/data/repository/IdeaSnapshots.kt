package com.dailysatori.data.repository

import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.service.ideatopic.*
import kotlinx.datetime.Clock

internal class IdeaSnapshots(private val db:DailySatoriDatabase,private val digest:(String)->String=::sha256Hex) {
    private val q get()=db.dailySatoriQueries
    fun intern(raw:String,format:Long=1,createdAt:Long=Clock.System.now().toEpochMilliseconds()):String=q.transactionWithResult {
        val hash=digest(raw)
        q.selectIdeaSnapshotsByHash(format,hash).executeAsList().firstOrNull { it.payload_json==raw }?.let {
            return@transactionWithResult it.id
        }
        val prefix="idea:$format:$hash"
        val id=(0..q.countIdeaSnapshots().executeAsOne()+1).asSequence().map {
            if(it==0L) prefix else "$prefix:$it"
        }.first { q.selectIdeaSnapshot(it).executeAsOneOrNull()==null }
        q.insertIdeaSnapshot(id,format,raw,hash,createdAt)
        id
    }
    fun payload(id:String)=q.selectIdeaSnapshot(id).executeAsOne().payload_json
    fun normalizeEvent(id:String) {
        val event=q.selectIdeaTopicEventById(id).executeAsOne()
        if(event.payload_version!=1L) return
        val split=IdeaSnapshotJson.split(event.kind,event.payload_json)
        if(split.role==null) return
        val ids=split.snapshots.map { intern(it,createdAt=event.created_at) }
        setEvent(id,split.payload,split.role,ids)
    }
    fun setEvent(id:String,payload:String,role:String,ids:List<String>) {
        ids.forEachIndexed { ordinal,snapshot -> q.insertIdeaEventSnapshot(id,role,ordinal.toLong(),snapshot) }
        q.setIdeaEventPayload(payload,2,id)
    }
    fun eventPayload(id:String):String {
        val event=q.selectIdeaTopicEventById(id).executeAsOne()
        if(event.payload_version==1L) return event.payload_json
        check(event.payload_version==2L) { "不支持的点子事件版本" }
        val refs=q.selectIdeaEventSnapshots(id).executeAsList()
        val role=when(event.kind) { IdeaEventKinds.Captured->"captured_source";IdeaEventKinds.Merged->"merged_duplicate";else->error("未知点子v2事件") }
        check(refs.map { it.role }.all { it==role } && refs.map { it.ordinal }==refs.indices.map { it.toLong() })
        return IdeaSnapshotJson.hydrate(event.kind,event.payload_json,refs.map { payload(it.snapshot_id) })
    }
}
