package com.dailysatori.service.ideatopic

import kotlinx.serialization.json.*

/** Read only the fields being retired; keep all other JSON literals and opaque snapshot bytes. */
internal object IdeaSnapshotJson {
    data class Split(val payload:String,val role:String?,val snapshots:List<String>)
    data class Member(val key:String,val start:Int,val valueStart:Int,val end:Int)
    fun split(kind:String,payload:String):Split = when(kind) {
        IdeaEventKinds.Captured -> {
            val member=members(payload).singleOrNull { it.key=="snapshot" } ?: error("点子捕获快照缺失")
            val raw=payload.substring(member.valueStart,member.end)
            key(raw)
            Split(remove(payload,member),"captured_source",listOf(raw))
        }
        IdeaEventKinds.Merged -> {
            val member=members(payload).singleOrNull { it.key=="duplicateSourceSnapshots" }
            val values=member?.let { elements(payload.substring(it.valueStart,it.end)) }.orEmpty()
            values.forEach(::key)
            Split(if(values.isEmpty()) payload else remove(payload,checkNotNull(member)),"merged_duplicate",values)
        }
        else -> Split(payload,null,emptyList())
    }
    fun withoutEmptyDuplicates(raw:String):String {
        val m=members(raw).single { it.key=="duplicateSourceSnapshots" }
        check(elements(raw.substring(m.valueStart,m.end)).isEmpty())
        return remove(raw,m)
    }
    fun key(raw:String):IdeaSourceKey {
        val fields=members(raw)
        val key=fields.singleOrNull { it.key=="key" } ?: error("点子来源key缺失")
        val keyRaw=raw.substring(key.valueStart,key.end)
        members(keyRaw)
        val obj=Json.parseToJsonElement(raw).jsonObject
        fun string(element:JsonElement?)=checkNotNull((element as? JsonPrimitive)?.takeIf { it.isString }?.content)
        string(obj["originalTitle"]);string(obj["originalContent"])
        val k=Json.parseToJsonElement(keyRaw).jsonObject
        return IdeaSourceKey(string(k["type"]),string(k["recordId"]))
    }
    fun hydrate(kind:String,payload:String,values:List<String>):String {
        if(kind==IdeaEventKinds.Captured) {
            check(values.size==1)
            return add(payload,"snapshot",values.single())
        }
        if(kind==IdeaEventKinds.Merged && values.isNotEmpty())
            return add(payload,"duplicateSourceSnapshots",values.joinToString(",","[","]"))
        check(values.isEmpty())
        return payload
    }
    fun members(raw:String):List<Member> {
        check(Json.parseToJsonElement(raw) is JsonObject)
        val result=mutableListOf<Member>()
        var i=space(raw,raw.indexOf('{')+1)
        while(raw[i]!='}') {
            val start=i;val keyEnd=end(raw,i)
            val key=Json.parseToJsonElement(raw.substring(i,keyEnd)).jsonPrimitive.content
            i=space(raw,keyEnd);check(raw[i]==':');i=space(raw,i+1)
            val valueStart=i;i=end(raw,i)
            result+=Member(key,start,valueStart,i)
            i=space(raw,i)
            if(raw[i]=='}') break
            check(raw[i]==',');i=space(raw,i+1)
        }
        check(result.map { it.key }.distinct().size==result.size) { "点子JSON字段重复" }
        return result
    }
    private fun elements(raw:String):List<String> {
        check(Json.parseToJsonElement(raw) is JsonArray)
        val result=mutableListOf<String>();var i=space(raw,raw.indexOf('[')+1)
        while(raw[i]!=']') {
            val stop=end(raw,i);result+=raw.substring(i,stop);i=space(raw,stop)
            if(raw[i]==']') break
            check(raw[i]==',');i=space(raw,i+1)
        }
        return result
    }
    private fun remove(raw:String,m:Member):String {
        val all=members(raw);val index=all.indexOf(m)
        val start=if(index==all.lastIndex && index>0) all[index-1].end else m.start
        val stop=if(index<all.lastIndex) all[index+1].start else m.end
        return raw.removeRange(start,stop)
    }
    private fun add(raw:String,name:String,value:String):String {
        val fields=members(raw);check(fields.none { it.key==name })
        val i=raw.lastIndexOf('}')
        return raw.substring(0,i)+(if(fields.isEmpty()) "" else ",")+"\"$name\":$value"+raw.substring(i)
    }
    private fun space(s:String,start:Int):Int { var i=start;while(i<s.length && s[i].isWhitespace()) i++;return i }
    private fun end(s:String,start:Int):Int {
        var i=start;var depth=0;var quoted=false;var escaped=false
        while(i<s.length) {
            val c=s[i]
            if(quoted) {
                if(escaped) escaped=false else if(c=='\\') escaped=true else if(c=='"') {
                    quoted=false;if(depth==0) return i+1
                }
            } else when(c) {
                '"' -> quoted=true
                '{','[' -> depth++
                '}',']' -> { if(depth==0) return i;depth--;if(depth==0) return i+1 }
                ',' -> if(depth==0) return i
                else -> if(c.isWhitespace() && depth==0) return i
            }
            i++
        }
        return i
    }
}
