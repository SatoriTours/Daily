part of 'ai_service.dart';

extension PartSummarize on AiService {
  Future<String> summarizeOneLine(String text) async {
    if (!SettingService.i.aiEnabled()) return text;
    // logger.i("[AI总结]: ${getSubstring(text)}");
    final res = await _sendRequest(
      _renderTemplate(
        PluginService.i.getSummarizeOneLineRole(),
        {'text': text},
      ),
      _renderTemplate(
        PluginService.i.getSummarizeOneLinePrompt(),
        {'text': text},
      ),
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
      summary = json.summary.trim();
    } else {
      summary = _renderTemplate(
        PluginService.i.getLongSummaryResult(),
        {
          'summary': json.summary,
          'keyContents': formatNumberedList(json.keyContents),
          'cases': formatNumberedList(json.cases),
        },
      ).trim();
    }

    List<String> tags = json.tags;

    logger.i("[AI总结] AI分析后是: summary => ${getSubstring(summary)}, tags => $tags");

    return (summary, tags);
  }

  String formatNumberedList(List<String> items) {
    if (items.isEmpty) return '';

    return items.asMap().entries.map((entry) {
      return '${entry.key + 1}. ${entry.value}';
    }).join('\n');
  }

  static get _shortSummarizeSystemPrompt => _renderTemplate(
        PluginService.i.getShortSummaryRole(),
        {'commonTags': _commonTags.join(',')},
      );

  static get _longSummarizeSystemPrompt => _renderTemplate(
        PluginService.i.getLongSummaryRole(),
        {'commonTags': _commonTags.join(',')},
      );

  static get _commonTags => PluginService.i.getCommonTags().split(',');
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

String _renderTemplate(String template, Map<String, String> context) {
  try {
    return Template(syntax: [MustacheExpressionSyntax()], value: template).process(context: context);
  } catch (e) {
    logger.i("[模板渲染] 渲染失败: $e, template => ||$template||, context => ||$context||");
    return template;
  }
}
