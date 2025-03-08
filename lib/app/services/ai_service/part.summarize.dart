part of 'ai_service.dart';

/// AI摘要功能扩展
///
/// 提供文章内容的摘要、标签提取等功能
extension PartSummarize on AiService {
  /// 生成单行摘要
  ///
  /// 将文本内容浓缩为一句话摘要
  /// [text] 需要摘要的文本内容
  Future<String> summarizeOneLine(String text) async {
    // 验证AI服务可用性
    if (!SettingService.i.aiEnabled()) {
      logger.i("[AI摘要] AI服务未启用，跳过单行摘要");
      return text;
    }

    logger.i("[AI摘要] 生成单行摘要中...");

    // 准备摘要提示
    final role = _renderTemplate(PluginService.i.getSummarizeOneLineRole(), {'text': text});

    final prompt = _renderTemplate(PluginService.i.getSummarizeOneLinePrompt(), {'text': text});

    // 发送请求
    final response = await _sendRequest(role, prompt);

    // 处理响应
    final result = response?.choices.first.message.content ?? '';
    if (result.isEmpty) {
      logger.w("[AI摘要] 单行摘要生成失败");
      return text;
    }

    logger.i("[AI摘要] 单行摘要生成完成");
    return result;
  }

  /// 生成完整摘要和标签
  ///
  /// 分析文本内容，生成详细摘要和相关标签
  /// [text] 需要分析的文本内容
  Future<(String summary, List<String> tags)> summarize(String text) async {
    // 验证AI服务可用性
    if (!SettingService.i.aiEnabled()) {
      logger.i("[AI摘要] AI服务未启用，跳过完整摘要生成");
      return (text, const <String>[]);
    }

    logger.i("[AI摘要] 生成完整摘要和标签中...");

    // 根据文本长度选择不同的摘要提示
    final isLongText = text.length > 300;
    final systemPrompt = isLongText ? _longSummarizeSystemPrompt : _shortSummarizeSystemPrompt;

    // 发送JSON格式的请求
    final response = await _sendRequest(systemPrompt, text, responseFormat: const ResponseFormat.jsonObject());

    // 处理响应
    final content = response?.choices.first.message.content ?? '';
    if (content.isEmpty) {
      logger.w("[AI摘要] 完整摘要响应为空");
      return (text, const <String>[]);
    }

    // 解析JSON响应
    final AiSummaryResult result;
    try {
      result = AiSummaryResult.fromJson(jsonDecode(content));
    } catch (e) {
      logger.e("[AI摘要] JSON解析失败: $e");
      logger.e("[AI摘要] 原始内容: $content");
      return (text, const <String>[]);
    }

    // 格式化摘要结果
    final String summary = isLongText ? _formatLongSummary(result) : result.summary.trim();

    final List<String> tags = result.tags;

    logger.i("[AI摘要] 摘要生成完成: ${getSubstring(summary)}");
    logger.i("[AI摘要] 标签生成完成: $tags");

    return (summary, tags);
  }

  /// 格式化长文本摘要
  String _formatLongSummary(AiSummaryResult result) {
    return _renderTemplate(PluginService.i.getLongSummaryResult(), {
      'summary': result.summary,
      'keyContents': _formatNumberedList(result.keyContents),
      'cases': _formatNumberedList(result.cases),
    }).trim();
  }

  /// 格式化编号列表
  String _formatNumberedList(List<String> items) {
    if (items.isEmpty) return '';

    return items
        .asMap()
        .entries
        .map((entry) {
          return '${entry.key + 1}. ${entry.value}';
        })
        .join('\n');
  }

  /// 获取短文本摘要系统提示
  String get _shortSummarizeSystemPrompt =>
      _renderTemplate(PluginService.i.getShortSummaryRole(), {'commonTags': _commonTags.join(',')});

  /// 获取长文本摘要系统提示
  String get _longSummarizeSystemPrompt =>
      _renderTemplate(PluginService.i.getLongSummaryRole(), {'commonTags': _commonTags.join(',')});

  /// 获取常用标签列表
  List<String> get _commonTags => PluginService.i.getCommonTags().split(',');
}

/// AI摘要结果模型
///
/// 用于解析AI返回的JSON格式摘要数据
class AiSummaryResult {
  /// 摘要内容
  final String summary;

  /// 关键内容点列表
  final List<String> keyContents;

  /// 案例列表
  final List<String> cases;

  /// 标签列表
  final List<String> tags;

  /// 构造函数
  AiSummaryResult({required this.summary, required this.keyContents, required this.cases, required this.tags});

  /// 从JSON构造
  factory AiSummaryResult.fromJson(Map<String, dynamic> json) {
    return AiSummaryResult(
      summary: json['summary'] as String,
      keyContents: List<String>.from(json['key_contents'] ?? []),
      cases: List<String>.from(json['cases'] ?? []),
      tags: List<String>.from(json['tags']),
    );
  }
}
