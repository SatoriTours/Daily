package com.dailysatori.service.migration

import app.cash.sqldelight.db.*
import com.dailysatori.shared.db.DailySatoriDatabase

/** Host-owned contract shared by migration and the assistant deny-by-default inventory. */
internal object DatabaseSchemaInventory {
    val columns: Map<String, Set<String>> = """
ai_config:id,provider,api_address,api_token,model_name,is_default,created_at,updated_at
article:id,title,ai_title,ai_content,ai_markdown_content,original_markdown_content,url,is_favorite,source_type,comment,status,cover_image,cover_image_url,pub_date,created_at,updated_at
article_fts:title,ai_title,ai_content
article_fts_content:docid,c0title,c1ai_title,c2ai_content
article_fts_docsize:docid,size
article_fts_segdir:level,idx,start_block,leaves_end_block,end_block,root
article_fts_segments:blockid,block
article_fts_stat:id,value
article_tag:article_id,tag_id
assistant_action:id,turn_id,kind,parameters_json,fingerprint,status,target_id,task_id,error_code
assistant_index_segment:source_id,source_version,segment_index,content,ai_state
assistant_memory_exclusion:source_type,source_id,created_at
assistant_reference:turn_id,source_type,source_id,title
assistant_session:id,created_at,summary,summary_sources_json,summary_version
assistant_turn:id,session_id,client_message_id,task_id,received_at,time_zone_id,response_message_id,requires_resume,action_plan_json,context_json
async_task:id,type,status,payload_json,checkpoint_json,result_json,progress_current,progress_total,progress_message,attempt_count,max_attempts,priority,unique_key,batch_id,run_after_ms,lease_owner,lease_until_ms,started_at,finished_at,last_error_code,last_error_message,created_at,updated_at
async_task_batch:id,name,status,total_count,created_at,updated_at
book:id,title,author,category,cover_image,introduction,has_update,created_at,updated_at
book_fts:title,author
book_fts_content:docid,c0title,c1author
book_fts_docsize:docid,size
book_fts_segdir:level,idx,start_block,leaves_end_block,end_block,root
book_fts_segments:blockid,block
book_fts_stat:id,value
book_viewpoint:id,book_id,title,content,example,status,error_message,outline_json,source_notes,created_at,updated_at
book_viewpoint_ai_message:id,session_id,role,content,status,error_message,created_at
book_viewpoint_ai_session:id,viewpoint_id,title,summary,summary_status,summary_error,created_at,updated_at,last_opened_at,summarized_at
book_viewpoint_fts:title,content,example
book_viewpoint_fts_content:docid,c0title,c1content,c2example
book_viewpoint_fts_docsize:docid,size
book_viewpoint_fts_segdir:level,idx,start_block,leaves_end_block,end_block,root
book_viewpoint_fts_segments:blockid,block
book_viewpoint_fts_stat:id,value
bookkeeping_entry:id,encrypted_payload,created_at,updated_at
chat_conversation:id,session_id,role,content,search_results,steps,created_at
diary:id,content,mood,created_at,updated_at,parent_diary_id
diary_record:id,content,tags,mood,images,created_at,updated_at,parent_diary_id
diary_attachment:id,diary_id,kind,local_path,position,remote_url,display_name,mime_type,size_bytes,duration_ms,transcript,transcript_status,knowledge_status,error_message,created_at,updated_at
diary_fts:content,tags
diary_fts_content:docid,c0content,c1tags
diary_fts_docsize:docid,size
diary_fts_segdir:level,idx,start_block,leaves_end_block,end_block,root
diary_fts_segments:blockid,block
diary_fts_stat:id,value
diary_month_summary:id,month_key,summary,diary_count,latest_diary_updated_at,status,error_message,generated_at,created_at,updated_at
diary_tag_definition:id,name,vocabulary_position
diary_tag_alias:alias,tag_id
diary_tag_assignment:diary_id,tag_id,origin,state,position,source_revision
diary_tag_history:id,diary_id,tag_id,operation_id,decision,origin,position,source_revision,source_fingerprint,edit_revision,occurred_at
diary_tag_processing:root_diary_id,processed_revision,edit_revision
diary_tag_catalog_state:id,format_version,pending_names_json,suggestions_json,merge_undo_json,baseline_json
diary_thread_revision:root_diary_id,revision,updated_at
diary_thread_summary:diary_id,source_revision,summary_revision,summary,status,error_message,generated_at,updated_at
external_favorite_item:id,source_id,provider,external_id,canonical_url,title,text,author_name,source_created_at,favorited_at,normalized_json,debug_json,content_hash,ai_input_hash,article_id,sync_status,import_status,ai_status,last_error_code,last_error_message,first_seen_at,last_seen_at,created_at,updated_at
external_favorite_source:id,provider,display_name,account_id,account_name,enabled,sync_interval_minutes,last_sync_started_at,last_sync_completed_at,last_success_at,last_sync_window_started_at,last_items_seen_count,last_pages_seen_count,last_error,last_error_code,last_error_message,status,last_sync_mode,rate_limit_reset_at,auth_json,config_json,capabilities_json,created_at,updated_at
idea_topic:id,title,description,provenance_summary,conclusions,next_action,status,merged_into_topic_id,context_revision,created_at,updated_at
idea_topic_event:id,topic_id,original_topic_id,kind,payload_json,payload_version,created_at
idea_source_snapshot:id,format_version,payload_json,sha256,created_at
idea_event_snapshot:event_id,role,ordinal,snapshot_id
idea_topic_message:id,session_id,role,content,status,error,created_at
idea_topic_session:id,topic_id,original_topic_id,title,summary,summary_through_message_id,summary_covered_message_ids,summary_status,created_at,updated_at
idea_topic_source:id,topic_id,original_topic_id,source_type,source_record_id,snapshot_id,captured_at,created_at,updated_at
image:id,url,path,article_id,created_at,updated_at
mcp_server:id,name,server_url,api_key,enabled,provider,template_id,template_type,config_json,created_at,updated_at
memory_entry:id,content_origin,assistant_kind,assistant_state,evidence,source_version,type,source_type,source_id,title,content,tags,created_at,updated_at
memory_entry_fts:title,content
memory_entry_fts_content:docid,c0title,c1content
memory_entry_fts_docsize:docid,size
memory_entry_fts_segdir:level,idx,start_block,leaves_end_block,end_block,root
memory_entry_fts_segments:blockid,block
memory_entry_fts_stat:id,value
opportunity_checkpoint:identity,position,fingerprint
opportunity_item:id,identity,source_id,position,created_at,saved,ignored,reminder_id,saved_at,analysis_json
opportunity_membership:kind,position,identity,source_id
opportunity_source:id,payload_json
opportunity_state:id,focus,last_attempt_at,last_attempt_context,last_error,dismissed_error_task_id
phone_message:id,encrypted_payload,received_at
reminder:id,content,notes,status,start_date,end_date,first_reminder_time,active_day_rule,recurrence_rule,time_zone_id,policy_snapshot_id,version,state_date,dismissal_count,last_notified_at,last_dismissed_at,completed_at,next_occurrence_at,deadline_at,created_at,updated_at
reminder_ai_batch:id,parent_batch_id,original_input,normalized_key,time_zone_id,local_date,status,task_id,attempt_count,max_attempts,last_attempt_at,error_summary,terminal_notification_at,created_at,updated_at
reminder_ai_draft:id,batch_id,source_index,source_text,draft_json,override_json,selected,state,reminder_id,confirmed_at,revision,created_at,updated_at
reminder_ai_draft_legacy:id,batch_id,source_index,source_text,draft_json,override_json,selected,discarded,confirmation_state,reminder_id,confirmed,confirmed_at,created_at,updated_at
reminder_event:id,reminder_id,event_type,scheduled_at,actual_at,metadata_json
reminder_profile:id,name,kind,profile_json,created_at,updated_at
reminder_policy_snapshot:id,format_version,payload_json,sha256,created_at
remote_article_sync_item:id,remote_source_id,remote_article_id,article_id,url,source_date,source_content_hash,processed_content_hash,processing_state,processing_error,processing_version,favorited_at,first_seen_at,last_seen_at,ai_feed_admission
remote_feed_sync_state:remote_source_id,entry_url,paging_mode,baseline_ready,checkpoint_ids,target_entry_ids,confirmed_anchor_url,confirmed_at,generation,gap_open,scan_start_url,scan_start_fingerprint,cursor_url,last_page_url,last_page_fingerprint,scan_pages,staged_bytes,closure_reached,blocked,blocked_reason,updated_at
remote_feed_staged_article:id,remote_source_id,generation,remote_article_id,page_url,sequence,stale,title,url,summary,content,published_at,bytes,created_at,body_kind,summary_language
remote_feed_scan_node:remote_source_id,generation,document_url,fingerprint,created_at
remote_feed_history_state:remote_source_id,entry_url,paging_mode,strategy,status,control_epoch,start_url,cursor_url,last_page_url,last_page_fingerprint,imported_count,target_count,pages_read,requests,last_error,blocked_reason,started_at,completed_at,updated_at
article_ai_source:article_id,body_hash,body_kind,upstream_summary,cache_hits
article_ai_result:body_hash,kind,version,target,markdown,partial
article_ai_job:id,article_id,body_hash,mode,version,target,priority,state,code,not_before,requests,cache_hits
article_ai_attempt:id,job_id,started_at,outcome,input_tokens,output_tokens,cached_input_tokens
remote_news_source:id,name,base_url,api_token,enabled,history_policy,created_at,updated_at
session:id,session_id,is_authenticated,username,last_accessed_at,created_at,updated_at
setting:id,key,value,created_at,updated_at
skill_config:id,name,description,gateway_url,api_token,skill_version,enabled,builtin,provider,template_id,tool_schema_json,created_at,updated_at
sms_reminder_source:id,encrypted_source,received_at,time_zone_id,status,draft_json,reminder_id,notified
tag:id,name,icon,created_at,updated_at
unified_news_source:id,summary_id,ref_key,source_type,source_id,source_filename,source_url,title,summary,source_time
unified_news_summary:id,summary_date,window_key,window_start_ms,window_end_ms,title,content,status,error_message,source_warnings,generated_at,created_at,updated_at
weekly_summary:id,week_start_date,week_end_date,content,article_count,diary_count,viewpoint_count,article_ids,diary_ids,viewpoint_ids,app_ideas,status,created_at,updated_at
    """.trimIndent().lines().associate { line ->
        line.substringBefore(':') to line.substringAfter(':').split(',').toSet()
    }

    fun validate(driver: SqlDriver, version: Long = com.dailysatori.config.DatabaseConfig.currentSchemaVersion) {
        validateColumns(driver, version)
        val missing = missingObjects(driver, version).keys
        check(missing.isEmpty()) { "数据库索引或触发器不完整: ${missing.sorted().joinToString()}" }
        if(version>=44) AssistantRelationsMigration.validateRelations(driver)
        check(strings(driver, "PRAGMA foreign_key_check").isEmpty()) { "数据库关联完整性检查失败" }
    }

    internal fun validateColumns(driver: SqlDriver, version: Long, includeProjection: Boolean = true) {
        columns.filterKeys { (version>=49 || it !in setOf("remote_feed_staged_article","remote_feed_scan_node")) &&
            (version>=55 || !it.startsWith("opportunity_")) &&
            (version>=51 || it!="remote_feed_history_state") &&
            (version>=53 || !it.startsWith("article_ai_")) &&
            (version>=47 || it!="remote_feed_sync_state") &&
            (version>=46 || it!="reminder_ai_draft_legacy") &&
            (version>=45 || it!="diary_record") && (version>=41 || !it.startsWith("diary_tag_")) &&
            (version>=42 || it!="reminder_policy_snapshot") &&
            (version>=43 || it !in setOf("idea_source_snapshot","idea_event_snapshot")) &&
            (includeProjection || it!="diary_record") }.forEach { (table, all) ->
            val found = strings(driver, "SELECT name FROM pragma_table_info('$table')").toSet()
            val expected=when {
                // Schema 47 and 48 shipped earlier shapes of the sync position table; V49 rebuilds it.
                version<48 && table=="remote_feed_sync_state" -> setOf(
                    "remote_source_id", "entry_url", "paging_mode", "pending", "older_url",
                    "frontier_article_id", "entry_older_url", "updated_at",
                )
                version<49 && table=="remote_feed_sync_state" -> setOf(
                    "remote_source_id", "entry_url", "paging_mode", "baseline_ready", "baseline_anchor_url",
                    "baseline_marker_ids", "entry_older_url", "scan_start_url", "cursor_url",
                    "processed_older_url", "pending", "updated_at",
                )
                version<50 && table=="remote_feed_sync_state" -> setOf(
                    "remote_source_id", "entry_url", "paging_mode", "baseline_ready", "checkpoint_ids",
                    "confirmed_anchor_url", "confirmed_at", "generation", "gap_open", "scan_start_url",
                    "scan_start_fingerprint", "cursor_url", "last_page_url", "last_page_fingerprint",
                    "scan_pages", "staged_bytes", "closure_reached", "blocked", "blocked_reason", "updated_at",
                )
                version<51 && table=="remote_news_source" -> all-"history_policy"
                version<52 && table=="remote_feed_history_state" -> all-"control_epoch"
                version<53 && table=="remote_feed_staged_article" -> all-setOf("body_kind","summary_language")
                version<54 && table=="remote_article_sync_item" -> all-"ai_feed_admission"
                version<45 && table=="diary" -> all+setOf("tags","images")
                version<44 && table=="memory_entry" -> all-"content_origin"
                version<43 && table=="idea_topic_source" && "snapshot_id" !in found -> all-"snapshot_id"+"snapshot_json"
                version<43 && table=="idea_topic_event" -> all-"payload_version"
                version<40 && table=="diary_attachment" -> all-setOf("position","remote_url")
                version<42 && table=="reminder" && "policy_snapshot_id" !in found -> all-"policy_snapshot_id"+"profile_json"
                version<42 && table=="reminder_ai_draft" && "state" !in found -> all-setOf("state","revision")+setOf("confirmed","discarded","confirmation_state")
                else -> all
            }
            check(found.containsAll(expected)) { "数据库结构不完整: $table" }
            check(version<45 || table!="diary" || found.intersect(setOf("tags","images")).isEmpty()) { "日记退役字段仍存在" }
        }
    }

    fun missingObjects(driver: SqlDriver, version: Long = com.dailysatori.config.DatabaseConfig.currentSchemaVersion): Map<String, String> {
        val actual = strings(driver, "SELECT type || ':' || name FROM sqlite_master").toSet()
        return objectStatements(driver).filterKeys { name -> name !in actual && !(
            (version<47 && name=="table:remote_feed_sync_state") ||
            (version<51 && name=="table:remote_feed_history_state") ||
            (version<53 && name.substringAfter(':').removePrefix("idx_").startsWith("article_ai_")) ||
            (version<55 && isOpportunityObject(name)) ||
            (version<49 && (name in setOf(
                "table:remote_feed_staged_article", "table:remote_feed_scan_node",
                "index:idx_remote_feed_staged_source",
            ))) ||
            (version<46 && name=="table:reminder_ai_draft_legacy") ||
            (version<45 && name=="view:diary_record") ||
            (version<44 && name.substringAfter(':') in setOf("assistant_turn_links_insert","assistant_turn_links_update","assistant_message_links_insert","assistant_message_links_update","assistant_task_history_restrict")) ||
            (version<43 && (name.substringAfter(':').startsWith("idea_snapshot_") || name.substringAfter(':').startsWith("idea_topic_merge_cycle_") ||
                name.substringAfter(':') in setOf("idea_source_snapshot","idea_event_snapshot","idea_source_record","idx_idea_snapshot_hash","idx_idea_event_snapshot_id","idx_idea_source_snapshot_id"))) ||
            (version<42 && (name.substringAfter(':').startsWith("reminder_policy_") ||
                name=="view:reminder_record" || name=="index:idx_reminder_policy_hash" || name=="index:idx_reminder_ai_draft_reminder")) ||
            (version<40 && name=="index:idx_diary_attachment_position") ||
                (version<41 && (name.substringAfter(':').startsWith("diary_tag_") ||
                    name=="index:idx_diary_tag_history_revision" || name=="view:diary_active_tags")))
        }
    }

    fun createOpportunityObjects(driver: SqlDriver) = objectStatements(driver).filterKeys(::isOpportunityObject)
        .values.forEach { driver.execute(null, it, 0) }

    private fun isOpportunityObject(name: String) = name.substringAfter(':').removePrefix("idx_").startsWith("opportunity_")

    private fun objectStatements(driver: SqlDriver): Map<String, String> {
        val objects = linkedMapOf<String, String>()
        val pattern = Regex("CREATE\\s+(?:UNIQUE\\s+)?(TABLE|INDEX|TRIGGER|VIEW)\\s+(?:IF NOT EXISTS\\s+)?([a-zA-Z0-9_]+)", RegexOption.IGNORE_CASE)
        val recorder = object : SqlDriver by driver {
            override fun execute(identifier: Int?, sql: String, parameters: Int,
                binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> {
                pattern.find(sql)?.let { objects["${it.groupValues[1].lowercase()}:${it.groupValues[2]}"] = sql }
                return QueryResult.Value(0L)
            }
        }
        DailySatoriDatabase.Schema.create(recorder).value
        return objects
    }

    internal fun strings(driver: SqlDriver, sql: String): List<String> = driver.executeQuery(null, sql, { cursor ->
        val values = mutableListOf<String>()
        while (cursor.next().value) values += checkNotNull(cursor.getString(0))
        QueryResult.Value(values)
    }, 0).value
}
