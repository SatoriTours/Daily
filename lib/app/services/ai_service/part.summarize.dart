part of 'ai_service.dart';

extension PartSummarize on AiService {
  Future<String> summarizeOneLine(
    String content, {
    AIFunctionType type = AIFunctionType.general,
  }) async {
    if (content.isEmpty) return '';

    if (!isAiEnabled(type)) {
      logger.i('[AI摘要] 功能类型 ${type.displayName} 的AI服务未启用，跳过单行摘要生成');
      return content;
    }

    logger.i('[AI摘要] 生成单行摘要中...');

    final trimmedContent = StringUtils.getSubstring(content, length: 500);

    final role = _renderTemplate(PluginService.i.getSummarizeOneLineRole(), {
      'text': trimmedContent,
    });

    final prompt = _renderTemplate(
      PluginService.i.getSummarizeOneLinePrompt(),
      {'text': trimmedContent},
    );

    final response = await _sendRequest(role, prompt, functionType: type);

    final result = response?.choices.first.message.content ?? '';
    if (result.isEmpty) {
      logger.w('[AI摘要] 单行摘要生成失败');
      return content;
    }

    logger.i('[AI摘要] 单行摘要生成完成');
    return result;
  }

  Future<(String, List<String>)> summarize(
    String text, {
    AIFunctionType type = AIFunctionType.general,
  }) async {
    if (text.isEmpty) return ('', const <String>[]);

    if (!isAiEnabled(type)) {
      logger.i('[AI摘要] 功能类型 ${type.displayName} 的AI服务未启用，跳过摘要生成');
      return ('', const <String>[]);
    }

    logger.i('[AI摘要] 生成完整摘要和标签中... ${StringUtils.getSubstring(text)}');

    final isLongText = text.length > 300;
    final systemPrompt = isLongText ? _longSummarizeSystemPrompt : _shortSummarizeSystemPrompt;

    final response = await _sendRequest(
      systemPrompt,
      text,
      functionType: type,
      responseFormat: const ResponseFormat.jsonObject(),
    );

    final content = response?.choices.first.message.content ?? '';
    if (content.isEmpty) {
      logger.w('[AI摘要] 完整摘要响应为空');
      return ('', const <String>[]);
    }

    final AiSummaryResult result;
    try {
      result = AiSummaryResult.fromJson(jsonDecode(content));
    } catch (e) {
      logger.e('[AI摘要] JSON解析失败: $e');
      logger.e('[AI摘要] 原始内容: $content');
      return ('', const <String>[]);
    }

    final summary = isLongText ? _formatLongSummary(result) : result.summary.trim();
    final tags = result.tags;

    logger.i('[AI摘要] 摘要生成完成: ${StringUtils.singleLine(StringUtils.getSubstring(summary))}');
    logger.i('[AI摘要] 标签生成完成: ${StringUtils.singleLine(StringUtils.getSubstring(tags.join(', ')))}');

    return (summary, tags);
  }

  String _formatLongSummary(AiSummaryResult result) => _renderTemplate(
    PluginService.i.getLongSummaryResult(),
    {
      'title': result.title.isNotEmpty ? result.title : '文章分析',
      'summary': result.summary,
      'keyPoints': result.keyPoints.join('\n'),
    },
  ).trim();

  String get _shortSummarizeSystemPrompt => _renderTemplate(
    PluginService.i.getShortSummaryRole(),
    {'commonTags': _commonTags.join(',')},
  );

  String get _longSummarizeSystemPrompt => _renderTemplate(
    PluginService.i.getLongSummaryRole(),
    {'commonTags': _commonTags.join(',')},
  );

  List<String> get _commonTags => PluginService.i.getCommonTags().split(',');
}

class AiSummaryResult {
  final String title;
  final List<String> keyPoints;
  final List<String> tags;
  final String summary;

  AiSummaryResult({
    this.title = '',
    required this.keyPoints,
    required this.tags,
    this.summary = '',
  });

  factory AiSummaryResult.fromJson(Map<String, dynamic> json) => AiSummaryResult(
        title: (json['title'] ?? '').toString(),
        keyPoints: List<String>.from(json['key_points'] ?? json['keyPoints'] ?? []),
        tags: List<String>.from(json['tags'] ?? []),
        summary: (json['summary'] ?? '').toString(),
      );
}
