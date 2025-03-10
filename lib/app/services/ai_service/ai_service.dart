import 'dart:convert';

import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:openai_dart/openai_dart.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/global.dart';
import 'package:template_expressions/template_expressions.dart';

part 'part.translate.dart';
part 'part.summarize.dart';

/// AI服务类
///
/// 负责处理AI相关功能，包括文本翻译和内容摘要
class AiService {
  // MARK: - 单例实现
  AiService._privateConstructor();
  static final AiService _instance = AiService._privateConstructor();
  static AiService get i => _instance;

  // MARK: - 私有属性

  /// 当前使用的AI模型
  ChatCompletionModel _model = ChatCompletionModel.modelId('gpt-4o-mini');

  /// 默认温度值（控制输出随机性）
  static const double _defaultTemperature = 0.5;

  /// 文本最大处理长度
  static const int _maxContentLength = 5000;

  // MARK: - 初始化方法

  /// 初始化AI服务
  Future<void> init() async {
    logger.i("[AI服务] 初始化");
    _updateModelFromSettings();
  }

  // MARK: - 核心方法

  /// 发送AI请求
  ///
  /// [role] 系统角色提示
  /// [content] 用户输入内容
  /// [responseFormat] 响应格式，默认为文本
  Future<CreateChatCompletionResponse?> _sendRequest(
    String role,
    String content, {
    ResponseFormat responseFormat = const ResponseFormat.text(),
  }) async {
    // 验证输入
    if (content.isEmpty || content.length <= 5) {
      logger.i("[AI服务] 内容过短，跳过请求");
      return null;
    }

    // 创建客户端
    await _createClient();

    try {
      // 限制内容长度
      final trimmedContent = getSubstring(content, length: _maxContentLength);

      // 发送请求
      final response = await _client!.createChatCompletion(
        request: CreateChatCompletionRequest(
          model: _model,
          messages: [
            ChatCompletionMessage.system(content: role.trim()),
            ChatCompletionMessage.user(content: ChatCompletionUserMessageContent.string(trimmedContent.trim())),
          ],
          temperature: _defaultTemperature,
          responseFormat: responseFormat,
        ),
      );

      return response;
    } catch (e) {
      logger.e("[AI服务] 请求失败: ${_client!.baseUrl} - $e");
      return null;
    }
  }

  /// 创建OpenAI客户端
  ///
  /// 返回配置好的客户端实例，或在配置无效时返回null
  OpenAIClient? _client;

  Future<void> _createClient() async {
    try {
      // 读取配置信息
      final apiKey = SettingRepository.getSetting(SettingService.openAITokenKey);
      final baseUrl = SettingRepository.getSetting(SettingService.openAIAddressKey);

      _client ??= OpenAIClient(apiKey: apiKey, baseUrl: baseUrl);

      logger.i("[AI服务] 客户端已创建");
    } catch (e) {
      logger.e("[AI服务] 创建客户端失败: $e");
    }
  }

  /// 从设置更新AI模型
  void _updateModelFromSettings() {
    final modelName = _getModelName();
    _model = ChatCompletionModel.modelId(modelName);
  }

  /// 获取模型名称
  String _getModelName() {
    final modelName = SettingRepository.getSetting(SettingService.aiModelKey);
    return modelName.isEmpty ? 'deepseek-v3' : modelName;
  }

  // MARK: - 工具方法

  /// 渲染模板
  ///
  /// 使用Mustache语法将变量注入模板
  /// [template] 模板字符串
  /// [context] 变量上下文
  String _renderTemplate(String template, Map<String, String> context) {
    try {
      return Template(syntax: [MustacheExpressionSyntax()], value: template).process(context: context);
    } catch (e) {
      logger.e("[AI服务] 模板渲染失败: $e");
      logger.e("[AI服务] 模板: $template");
      logger.e("[AI服务] 上下文: $context");
      return template;
    }
  }
}
