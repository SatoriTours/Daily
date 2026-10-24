package com.dailysatori.service.assistant

import ai.koog.agents.core.tools.*
import com.dailysatori.service.assistant.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

internal fun koogDataTools(
    data: AssistantDataGateway,
    onResult: (String, String, String) -> Unit = { _, _, _ -> },
    onRead: (String, String) -> Unit = { _, _ -> },
): ToolRegistry = ToolRegistry {
    fun <A : ToolArgs> readTool(serializer: KSerializer<A>, descriptor: ToolDescriptor, read: suspend (A) -> JsonObject) =
        ReadTool(serializer, descriptor, onResult, read)
    tool(readTool(ToolArgs.Empty.serializer(), ToolDescriptor("list_data_sources", "列出可读取的数据来源及安全字段")) { _: ToolArgs.Empty ->
        data.catalog()
    })
    tool(readTool(QueryArgs.serializer(), ToolDescriptor("query_data", "在安全字段上筛选、排序和分页；不接受SQL",
        requiredParameters = listOf(parameter("source")),
        optionalParameters = listOf(parameter("fields", ToolParameterType.List(ToolParameterType.String)),
            filtersParameter(), parameter("order_by"), parameter("descending", ToolParameterType.Boolean),
            parameter("limit", ToolParameterType.Integer), parameter("offset", ToolParameterType.Integer)))) { args ->
        data.query(AssistantQuery(args.source, args.fields, args.filters, args.orderBy, args.descending, args.limit, args.offset)).also { result ->
            result["rows"]?.jsonArray?.forEach { row -> row.jsonObject["id"]?.jsonPrimitive?.content?.let { onRead(args.source, it) } }
        }
    })
    tool(readTool(AggregateArgs.serializer(), ToolDescriptor("aggregate_data", "对安全记录计数或按字段分组",
        requiredParameters = listOf(parameter("source")),
        optionalParameters = listOf(filtersParameter(), parameter("group_by", ToolParameterType.List(ToolParameterType.String)),
            parameter("measures", ToolParameterType.List(ToolParameterType.String))))) { args ->
        data.aggregate(AssistantAggregate(args.source, args.filters, args.groupBy, args.measures))
    })
    tool(readTool(SearchArgs.serializer(), ToolDescriptor("search_records", "查找关键词，可改写关键词并限定来源",
        requiredParameters = listOf(parameter("query")),
        optionalParameters = listOf(parameter("sources", ToolParameterType.List(ToolParameterType.String)),
            parameter("limit", ToolParameterType.Integer), parameter("offset", ToolParameterType.Integer)))) { args ->
        data.search(args.query, args.sources, args.limit, args.offset).also { result ->
            result["rows"]?.jsonArray?.forEach { row -> onRead(row.jsonObject["source"]!!.jsonPrimitive.content, row.jsonObject["id"]!!.jsonPrimitive.content) }
        }
    })
    tool(readTool(ReadArgs.serializer(), ToolDescriptor("read_record", "按来源与字符串ID分段读取记录，每段最多6000字",
        requiredParameters = listOf(parameter("source"), parameter("id")),
        optionalParameters = listOf(parameter("offset", ToolParameterType.Integer)))) { args ->
        data.read(args.source, args.id, args.offset).also { if (it["ok"] == JsonPrimitive(true)) onRead(args.source, args.id) }
    })
}

private class ReadTool<A : ToolArgs>(
    override val argsSerializer: KSerializer<A>,
    override val descriptor: ToolDescriptor,
    private val onResult: (String, String, String) -> Unit,
    private val read: suspend (A) -> JsonObject,
) : SimpleTool<A>() {
    override suspend fun doExecute(args: A): String = try {
        read(args).toString().also { onResult(descriptor.name, Json.encodeToString(argsSerializer, args), it) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        """{"ok":false,"error":"invalid_or_unavailable_read"}"""
    }
}

private fun parameter(name: String, type: ToolParameterType = ToolParameterType.String) =
    ToolParameterDescriptor(name, name, type)

private fun filtersParameter() = parameter("filters", ToolParameterType.List(ToolParameterType.Object(
    properties = listOf(parameter("field"), parameter("operator", ToolParameterType.Enum(arrayOf("eq", "contains", "gte", "lte"))),
        ToolParameterDescriptor("value", "筛选标量；数值字段可传数字字符串", ToolParameterType.String)),
    requiredProperties = listOf("field", "operator", "value"), additionalProperties = false
)))

@Serializable
private data class QueryArgs(
    val source: String, val fields: List<String> = emptyList(), val filters: List<AssistantFilter> = emptyList(),
    val orderBy: String? = null, val descending: Boolean = false, val limit: Int = 20, val offset: Long = 0
) : ToolArgs

@Serializable
private data class AggregateArgs(
    val source: String, val filters: List<AssistantFilter> = emptyList(), val groupBy: List<String> = emptyList(),
    val measures: List<String> = listOf("count")
) : ToolArgs

@Serializable
private data class SearchArgs(
    val query: String, val sources: List<String> = emptyList(), val limit: Int = 20, val offset: Long = 0
) : ToolArgs

@Serializable
private data class ReadArgs(val source: String, val id: String, val offset: Int = 0) : ToolArgs
