package com.dailysatori.service.ideatopic

import com.dailysatori.shared.db.SelectIdeaTopicEventsForRead

/** Each SELECT captures an event and all of its references together, including during deletion. */
internal fun readIdeaEvents(rows:List<SelectIdeaTopicEventsForRead>):List<IdeaTopicEvent> =
    rows.groupBy { it.id }.values.map { group ->
        val event=group.first()
        val refs=group.filter { it.snapshot_role!=null }
        val payload=when(event.payload_version) {
            1L -> { check(refs.isEmpty());event.payload_json }
            2L -> {
                val role=when(event.kind) {
                    IdeaEventKinds.Captured->"captured_source"
                    IdeaEventKinds.Merged->"merged_duplicate"
                    else->error("未知点子v2事件")
                }
                check(refs.all { it.snapshot_role==role } && refs.map { it.ordinal }==refs.indices.map { it.toLong() })
                IdeaSnapshotJson.hydrate(event.kind,event.payload_json,refs.map { checkNotNull(it.snapshot_json) })
            }
            else->error("不支持的点子事件版本")
        }
        IdeaTopicEvent(event.id,event.topic_id,event.original_topic_id,event.kind,payload,event.created_at)
    }
