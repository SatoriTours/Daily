package com.dailysatori.service.assistant.data

/** Host-only exhaustive schema inventory. Never included in model prompts or catalog output. */
enum class AssistantFieldPolicy { READABLE, REDACTED, LOCAL_ONLY, DENIED }

internal object AssistantSchemaPolicy {
    // Explicit snapshot: a schema addition fails the coverage test until it is reviewed here.
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
diary:id,content,tags,mood,images,created_at,updated_at,parent_diary_id
diary_attachment:id,diary_id,kind,local_path,display_name,mime_type,size_bytes,duration_ms,transcript,transcript_status,knowledge_status,error_message,created_at,updated_at
diary_fts:content,tags
diary_fts_content:docid,c0content,c1tags
diary_fts_docsize:docid,size
diary_fts_segdir:level,idx,start_block,leaves_end_block,end_block,root
diary_fts_segments:blockid,block
diary_fts_stat:id,value
diary_month_summary:id,month_key,summary,diary_count,latest_diary_updated_at,status,error_message,generated_at,created_at,updated_at
diary_thread_revision:root_diary_id,revision,updated_at
diary_thread_summary:diary_id,source_revision,summary_revision,summary,status,error_message,generated_at,updated_at
external_favorite_item:id,source_id,provider,external_id,canonical_url,title,text,author_name,source_created_at,favorited_at,normalized_json,debug_json,content_hash,ai_input_hash,article_id,sync_status,import_status,ai_status,last_error_code,last_error_message,first_seen_at,last_seen_at,created_at,updated_at
external_favorite_source:id,provider,display_name,account_id,account_name,enabled,sync_interval_minutes,last_sync_started_at,last_sync_completed_at,last_success_at,last_sync_window_started_at,last_items_seen_count,last_pages_seen_count,last_error,last_error_code,last_error_message,status,last_sync_mode,rate_limit_reset_at,auth_json,config_json,capabilities_json,created_at,updated_at
idea_topic:id,title,description,provenance_summary,conclusions,next_action,status,merged_into_topic_id,context_revision,created_at,updated_at
idea_topic_event:id,topic_id,original_topic_id,kind,payload_json,created_at
idea_topic_message:id,session_id,role,content,status,error,created_at
idea_topic_session:id,topic_id,original_topic_id,title,summary,summary_through_message_id,summary_covered_message_ids,summary_status,created_at,updated_at
idea_topic_source:id,topic_id,original_topic_id,source_type,source_record_id,snapshot_json,captured_at,created_at,updated_at
image:id,url,path,article_id,created_at,updated_at
mcp_server:id,name,server_url,api_key,enabled,provider,template_id,template_type,config_json,created_at,updated_at
memory_entry:id,assistant_kind,assistant_state,evidence,source_version,type,source_type,source_id,title,content,tags,created_at,updated_at
memory_entry_fts:title,content
memory_entry_fts_content:docid,c0title,c1content
memory_entry_fts_docsize:docid,size
memory_entry_fts_segdir:level,idx,start_block,leaves_end_block,end_block,root
memory_entry_fts_segments:blockid,block
memory_entry_fts_stat:id,value
phone_message:id,encrypted_payload,received_at
reminder:id,content,notes,status,start_date,end_date,first_reminder_time,active_day_rule,recurrence_rule,time_zone_id,profile_json,version,state_date,dismissal_count,last_notified_at,last_dismissed_at,completed_at,next_occurrence_at,deadline_at,created_at,updated_at
reminder_ai_batch:id,parent_batch_id,original_input,normalized_key,time_zone_id,local_date,status,task_id,attempt_count,max_attempts,last_attempt_at,error_summary,terminal_notification_at,created_at,updated_at
reminder_ai_draft:id,batch_id,source_index,source_text,draft_json,override_json,selected,discarded,confirmation_state,reminder_id,confirmed,confirmed_at,created_at,updated_at
reminder_event:id,reminder_id,event_type,scheduled_at,actual_at,metadata_json
reminder_profile:id,name,kind,profile_json,created_at,updated_at
remote_article_sync_item:id,remote_source_id,remote_article_id,article_id,url,source_date,source_content_hash,processed_content_hash,processing_state,processing_error,processing_version,favorited_at,first_seen_at,last_seen_at
remote_news_source:id,name,base_url,api_token,enabled,created_at,updated_at
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
    private val forbiddenTables = setOf("session", "sms_reminder_source", "phone_message", "bookkeeping_entry")
    private val credentials = setOf("api_token", "api_key", "auth_json", "encrypted_payload", "encrypted_source", "config_json", "tool_schema_json", "profile_json")
    fun classify(table: String, field: String): AssistantFieldPolicy {
        if (field !in columns[table].orEmpty() || table in forbiddenTables || field in credentials) return AssistantFieldPolicy.DENIED
        val source = assistantSources.values.firstOrNull { it.table == table } ?: return AssistantFieldPolicy.LOCAL_ONLY
        val projected = field in source.fields || source.expressions.values.any { Regex("\\br\\.$field\\b").containsMatchIn(it) }
        if (!projected) return AssistantFieldPolicy.LOCAL_ONLY
        return if (field in source.numeric) AssistantFieldPolicy.READABLE else AssistantFieldPolicy.REDACTED
    }
}
