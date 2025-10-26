part of 'ai_service.dart';

/// AI摘要功能扩展
///
/// 提供文章内容的摘要、标签提取等功能
extension PartSummarize on AiService {
  /// 生成一行摘要
  ///
  /// 以一句话总结文本内容，常用于生成简短标题
  /// [content] 需要总结的内容
  /// [functionType] 功能类型，默认为0（通用配置）
  Future<String> summarizeOneLine(String content, {int functionType = 0}) async {
    if (content.isEmpty) return '';

    // 验证AI服务可用性
    if (!isAiEnabled(functionType)) {
      logger.i("[AI摘要] 功能类型 $functionType 的AI服务未启用，跳过单行摘要生成");
      return content;
    }

    logger.i("[AI摘要] 生成单行摘要中...");

    // 限制内容长度
    final trimmedContent = StringUtils.getSubstring(content, length: 500);

    // 准备摘要提示
    final role = _renderTemplate(PluginService.i.getSummarizeOneLineRole(), {'text': trimmedContent});

    final prompt = _renderTemplate(PluginService.i.getSummarizeOneLinePrompt(), {'text': trimmedContent});

    // 发送请求，使用指定的功能类型
    final response = await _sendRequest(role, prompt, functionType: functionType);

    // 处理响应
    final result = response?.choices.first.message.content ?? '';
    if (result.isEmpty) {
      logger.w("[AI摘要] 单行摘要生成失败");
      return content;
    }

    logger.i("[AI摘要] 单行摘要生成完成");
    return result;
  }

  /// 生成完整摘要和标签
  ///
  /// 分析文本内容，生成详细摘要和相关标签
  /// [text] 需要分析的文本内容
  /// [functionType] 功能类型，默认为0（通用配置）
  Future<(String, List<String>)> summarize(String text, {int functionType = 0}) async {
    if (text.isEmpty) return ('', const <String>[]);

    // 检查AI是否启用
    if (!isAiEnabled(functionType)) {
      logger.i("[AI摘要] 功能类型 $functionType 的AI服务未启用，跳过摘要生成");
      return ('', const <String>[]);
    }

    logger.i("[AI摘要] 生成完整摘要和标签中... ${StringUtils.getSubstring(text)}");

    // 根据文本长度选择不同的摘要提示
    final isLongText = text.length > 300;
    final systemPrompt = isLongText ? _longSummarizeSystemPrompt : _shortSummarizeSystemPrompt;

    // 发送JSON格式的请求，使用指定的功能类型
    final response = await _sendRequest(
      systemPrompt,
      text,
      functionType: functionType,
      responseFormat: const ResponseFormat.jsonObject(),
    );

    // 处理响应
    final content = response?.choices.first.message.content ?? '';
    if (content.isEmpty) {
      logger.w("[AI摘要] 完整摘要响应为空");
      return ('', const <String>[]);
    }

    // 解析JSON响应
    final AiSummaryResult result;
    try {
      result = AiSummaryResult.fromJson(jsonDecode(content));
    } catch (e) {
      logger.e("[AI摘要] JSON解析失败: $e");
      logger.e("[AI摘要] 原始内容: $content");
      return ('', const <String>[]);
    }

    // 格式化摘要结果
    final String summary = isLongText ? _formatLongSummary(result) : result.summary.trim();

    final List<String> tags = result.tags;

    logger.i("[AI摘要] 摘要生成完成: ${StringUtils.singleLine(StringUtils.getSubstring(summary))}");
    logger.i("[AI摘要] 标签生成完成: ${StringUtils.singleLine(StringUtils.getSubstring(tags.join(', ')))}");

    return (summary, tags);
  }

  /// 格式化长文本摘要
  String _formatLongSummary(AiSummaryResult result) {
    return _renderTemplate(PluginService.i.getLongSummaryResult(), {
      'title': result.title.isNotEmpty ? result.title : '文章分析',
      'summary': result.summary,
      'keyPoints': result.keyPoints.join('\n'),
    }).trim();
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
  /// 文章标题
  final String title;

  /// 关键要点列表
  final List<String> keyPoints;

  /// 标签列表
  final List<String> tags;

  /// 简要摘要（用于短文本）
  final String summary;

  /// 构造函数
  AiSummaryResult({this.title = '', required this.keyPoints, required this.tags, this.summary = ''});

  /// 从JSON构造
  factory AiSummaryResult.fromJson(Map<String, dynamic> json) {
    return AiSummaryResult(
      title: (json['title'] ?? '').toString(),
      keyPoints: List<String>.from(json['key_points'] ?? json['keyPoints'] ?? []),
      tags: List<String>.from(json['tags'] ?? []),
      summary: (json['summary'] ?? '').toString(),
    );
  }
}
