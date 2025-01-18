part of 'ai_service.dart';

extension PartSummarize on AiService {
  Future<String> summarizeOneLine(String text) async {
    if (!SettingService.i.aiEnabled()) return text;
    // logger.i("[AI总结]: ${getSubstring(text)}");
    final res = await _sendRequest(
      '你是一个文章读者, 总结一个能表达文章核心内容并且能吸引别人阅读的标题,保持原文的意思，注意:不使用"文章提到"或类似的表达方式，一定不要添加个人观点, 标题不要加入引号等特殊字符',
      ' 一句话总结一下内容：```$text``` ',
    );
    return res?.choices.first.message.content ?? '';
  }

  Future<(String summary, List<String> tags)> summarize(String text) async {
    if (!SettingService.i.aiEnabled()) return (text, const <String>[]);

    final res = await _sendRequest(
      text.length > 300 ? _longSummarizeSystemPrompt : _shortSummarizeSystemPrompt,
      text,
      responseFormat: const ResponseFormat.jsonObject(),
    );

    String content = res?.choices.first.message.content ?? '';
    final AiSummaryResult json;
    try {
      json = AiSummaryResult.fromJson(jsonDecode(content));
    } catch (e) {
      logger.e("[AI总结] JSON解析失败: $e, content => $content");
      return (text, const <String>[]);
    }

    String summary = '';
    if (text.length <= 300) {
      summary = json.summary;
    } else {
      summary = '''
概述:

${json.summary}

关键内容:

${formatNumberedList(json.keyContents)}

关键案例:

${formatNumberedList(json.cases)}
    ''';
    }

    List<String> tags = json.tags;

    logger.i("[AI总结] AI分析后是: summary => ${getSubstring(summary)}");

    return (summary, tags);
  }

  String formatNumberedList(List<String> items) {
    if (items.isEmpty) return '';

    return items.asMap().entries.map((entry) {
      return '${entry.key + 1}. ${entry.value}';
    }).join('\n');
  }

  static final String _shortSummarizeSystemPrompt = '''
用户将给出一段文章, 你将根据文章的内容, 按照如下要求并使用json格式输出。

总结要求：
1. 输出内容为纯文本,不包含任何markdown或其他排版格式.
2. 不要以"文章主要介绍"或类似的表达方式，直接输出内容就可以.
3. summary不超100字以内.
6. tags 从 ${_commonTags.join(',')} 中选择最合适的标签, 最多3个。
7. 所有的内容使用中文输出.

EXAMPLE JSON OUTPUT:
{
    "summary": "文章的核心内容",
    "tags": ["标签1"]
}
''';

  static final String _longSummarizeSystemPrompt = '''
用户将给出一段文章, 你将根据文章的内容, 按照如下要求并使用json格式输出。

总结要求：
1. 输出内容为纯文本,不包含任何markdown或其他排版格式.
2. 不要以"文章主要介绍"或类似的表达方式，直接输出内容就可以.
3. summary不超100字以内.
4. key_content 是文章中最关键要表达的内容, 总结的详细一点,最多5个.
5. case 是文章最关键的案例或数据, 总结的详细一点,最多3个.
6. tags 从 ${_commonTags.join(',')} 中选择最合适的标签, 最多3个。
7. 所有的内容使用中文输出.

EXAMPLE JSON OUTPUT:
{
    "summary": "核心内容",
    "key_contents": ["关键内容1"],
    "cases": ["关键案例1"]
    "tags": ["标签1"]
}
''';

  static final List<String> _commonTags = ['软件', '硬件', '生活', '效率', '新闻', '工具', '成长', '设计', '健康', 'AI', '互联网', '云计算'];
}

class AiSummaryResult {
  final String summary;
  final List<String> keyContents;
  final List<String> cases;
  final List<String> tags;

  AiSummaryResult({
    required this.summary,
    required this.keyContents,
    required this.cases,
    required this.tags,
  });

  factory AiSummaryResult.fromJson(Map<String, dynamic> json) {
    return AiSummaryResult(
      summary: json['summary'] as String,
      keyContents: List<String>.from(json['key_contents'] ?? []),
      cases: List<String>.from(json['cases'] ?? []),
      tags: List<String>.from(json['tags']),
    );
  }
}
