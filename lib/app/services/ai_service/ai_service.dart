import 'dart:convert';

import 'package:daily_satori/app/config/app_config.dart';
import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/ai_config_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:daily_satori/app/services/service_base.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/utils/string_utils.dart';
import 'package:jinja/jinja.dart';
import 'package:openai_dart/openai_dart.dart';

/// AI 服务
class AiService extends AppService {
  AiService._();
  static final AiService i = AiService._();

  @override
  final ServicePriority priority = ServicePriority.normal;

  @override
  Future<void> init() async {}

  /// 检查 AI 是否启用
  bool isEnabled([AIFunctionType type = AIFunctionType.general]) =>
      AIConfigService.i.getApiAddressForFunction(type).isNotEmpty &&
      AIConfigService.i.getApiTokenForFunction(type).isNotEmpty;

  /// 获取完成的 AI 回复
  Future<String> complete(
    String prompt, {
    String? systemPrompt,
    AIFunctionType type = AIFunctionType.general,
    ResponseFormat format = const ResponseFormat.text(),
  }) async {
    if (prompt.isEmpty) return '';
    if (!isEnabled(type)) return '';

    final client = await _client(type);
    if (client == null) return '';

    final model = ChatCompletionModel.modelId(
      AIConfigService.i.getModelNameForFunction(type),
    );

    try {
      final response = await client.createChatCompletion(
        request: CreateChatCompletionRequest(
          model: model,
          messages: [
            if (systemPrompt != null)
              ChatCompletionMessage.system(content: systemPrompt.trim()),
            ChatCompletionMessage.user(
              content: ChatCompletionUserMessageContent.string(
                StringUtils.getSubstring(prompt, length: AIConfig.maxContentLength).trim(),
              ),
            ),
          ],
          temperature: AIConfig.defaultTemperature,
          responseFormat: format,
        ),
      );
      return response.choices.first.message.content ?? '';
    } catch (e) {
      logger.e('[AI] 请求失败: $e');
      return '';
    }
  }

  /// 翻译文本为中文
  Future<String> translate(String text, [AIFunctionType type = AIFunctionType.articleAnalysis]) async {
    if (text.isEmpty) return text;
    if (!isEnabled(type)) return text;

    final role = _template(PluginService.i.translateRole, {});
    return await complete(
      text,
      systemPrompt: role,
      type: type,
    );
  }

  /// 生成单行摘要
  Future<String> summarizeOneLine(String content, [AIFunctionType type = AIFunctionType.general]) async {
    if (content.isEmpty) return '';
    if (!isEnabled(type)) return content;

    final text = StringUtils.getSubstring(content, length: 500);
    final role = _template(PluginService.i.summarizeOneLineRole, {'text': text});
    final prompt = _template(PluginService.i.summarizeOneLinePrompt, {'text': text});

    return await complete(prompt, systemPrompt: role, type: type);
  }

  /// 生成完整摘要和标签
  Future<(String summary, List<String> tags)> summarize(String text, [AIFunctionType type = AIFunctionType.general]) async {
    if (text.isEmpty) return ('', <String>[]);
    if (!isEnabled(type)) return ('', <String>[]);

    final isLong = text.length > 300;
    final prompt = _template(
      isLong ? PluginService.i.longSummaryRole : PluginService.i.shortSummaryRole,
      {'commonTags': PluginService.i.commonTags.split(',').join(',')},
    );

    final content = await complete(
      text,
      systemPrompt: prompt,
      type: type,
      format: const ResponseFormat.jsonObject(),
    );

    if (content.isEmpty) return ('', <String>[]);

    try {
      final result = AiSummaryResult.fromJson(jsonDecode(content));
      final summary = isLong ? _formatLongSummary(result) : result.summary.trim();
      logger.i('[AI] 摘要完成: ${StringUtils.singleLine(StringUtils.getSubstring(summary))}');
      return (summary, result.tags.cast<String>());
    } catch (e) {
      logger.e('[AI] 摘要解析失败: $e');
      return ('', <String>[]);
    }
  }

  /// HTML 转换为 Markdown
  Future<String> htmlToMarkdown(String html, [AIFunctionType type = AIFunctionType.articleAnalysis]) async {
    if (html.isEmpty) return '';
    if (!isEnabled(type)) return html;

    final role = PluginService.i.htmlToMarkdownRole;
    final result = await complete(html, systemPrompt: role, type: type);
    return result.isEmpty ? html : _postProcessMarkdown(result);
  }

  // ========== 内部方法 ==========

  Future<OpenAIClient?> _client(AIFunctionType type) async {
    try {
      final address = AIConfigService.i.getApiAddressForFunction(type);
      final token = AIConfigService.i.getApiTokenForFunction(type);

      final apiKey = token.isNotEmpty ? token : SettingRepository.i.getSetting(SettingService.openAITokenKey);
      final baseUrl = address.isNotEmpty ? address : SettingRepository.i.getSetting(SettingService.openAIAddressKey);

      if (apiKey.isEmpty || baseUrl.isEmpty) return null;
      return OpenAIClient(apiKey: apiKey, baseUrl: baseUrl);
    } catch (e) {
      logger.e('[AI] 创建客户端失败: $e');
      return null;
    }
  }

  String _template(String template, Map<String, String> context) {
    try {
      return Environment().fromString(template).render(context);
    } catch (e) {
      logger.e('[AI] 模板渲染失败: $e');
      return template;
    }
  }

  String _formatLongSummary(AiSummaryResult result) => _template(
        PluginService.i.longSummaryResult,
        {
          'title': result.title.isNotEmpty ? result.title : '文章分析',
          'summary': result.summary,
          'keyPoints': result.keyPoints.join('\n'),
        },
      ).trim();

  String _postProcessMarkdown(String markdown) {
    if (markdown.isEmpty) return markdown;

    var result = markdown;
    result = _fixTitleFormat(result);
    result = result.replaceAll(RegExp(r'\n{3,}'), '\n\n');
    result = _optimizeListSpacing(result);
    result = _normalizePunctuation(result);
    return result;
  }

  String _fixTitleFormat(String text) {
    final lines = text.split('\n');
    if (lines.isEmpty) return text;

    final firstLine = lines.first;
    if (!firstLine.startsWith('# ') || firstLine.length >= 30) return text;

    final content = firstLine.substring(2).trim();
    const falseTitles = [
      '主要功能', '功能特点', '产品介绍', '使用说明', '注意事项',
      '基本功能', '核心功能', '功能列表', '功能概述', '目录',
      '内容简介', '特点', '优势', '使用方法', '安装步骤',
      '配置说明', '常见问题',
    ];

    final isFalseTitle = falseTitles.any(content.contains);
    final isRealTitle = content.contains(RegExp(r'[。！？]')) || content.length > 15;

    if (isFalseTitle && !isRealTitle) {
      lines[0] = content;
      return lines.join('\n');
    }
    return text;
  }

  String _optimizeListSpacing(String text) {
    final lines = text.split('\n');
    final result = <String>[];

    for (int i = 0; i < lines.length; i++) {
      final current = lines[i];
      final next = i + 1 < lines.length ? lines[i + 1] : '';
      final prev = i > 0 ? lines[i - 1] : '';

      final isNextList = _isListItem(next);
      final isPrevList = _isListItem(prev);

      if (current.trim().isEmpty && isPrevList && isNextList) continue;
      result.add(current);
    }
    return result.join('\n');
  }

  bool _isListItem(String line) {
    final trimmed = line.trim();
    return trimmed.startsWith('- ') ||
        trimmed.startsWith('* ') ||
        RegExp(r'^\s*\d+\.\s+').hasMatch(trimmed);
  }

  String _normalizePunctuation(String text) {
    return text
        .replaceAll(',', '，')
        .replaceAllMapped(RegExp(r'([^a-zA-Z0-9])\.(?=\s|$)'), (m) => '${m.group(1)}。')
        .replaceAll(':', '：')
        .replaceAll(';', '；')
        .replaceAll('?', '？')
        .replaceAll('!', '！');
  }
}

/// AI 摘要结果模型
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
