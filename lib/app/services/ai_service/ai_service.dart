import 'dart:convert';

import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:openai_dart/openai_dart.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/ai_config_service.dart';
import 'package:daily_satori/global.dart';
import 'package:template_expressions/template_expressions.dart';
import 'package:daily_satori/app/utils/string_utils.dart';

part 'part.translate.dart';
part 'part.summarize.dart';
part 'part.html_to_markdown.dart';

/// AI服务类
///
/// 负责处理AI相关功能，包括文本翻译和内容摘要
class AiService {
  // MARK: - 单例实现
  AiService._privateConstructor();
  static final AiService _instance = AiService._privateConstructor();
  static AiService get i => _instance;

  // MARK: - 私有属性

  /// 当前使用的AI模型名称
  String _defaultModelName = 'gpt-4o-mini';

  /// 默认温度值（控制输出随机性）
  static const double _defaultTemperature = 0.5;

  /// 文本最大处理长度
  static const int _maxContentLength = 50000;

  // MARK: - 初始化方法

  /// 初始化AI服务
  Future<void> init() async {
    logger.i("[AI服务] 初始化");
    _updateDefaultModel();
    logger.i('AI服务初始化完成');
  }

  // MARK: - 核心方法

  /// 发送AI请求
  ///
  /// [role] 系统角色提示
  /// [content] 用户输入内容
  /// [functionType] 功能类型，用于选择不同的AI配置
  /// [responseFormat] 响应格式，默认为文本
  Future<CreateChatCompletionResponse?> _sendRequest(
    String role,
    String content, {
    int functionType = 0,
    ResponseFormat responseFormat = const ResponseFormat.text(),
  }) async {
    // 验证输入
    if (content.isEmpty || content.length <= 5) {
      logger.i("[AI服务] 内容过短，跳过请求");
      return null;
    }

    // 创建客户端
    final client = await _createClientForFunction(functionType);
    if (client == null) {
      logger.e("[AI服务] 无法创建AI客户端，请检查配置");
      return null;
    }

    try {
      // 限制内容长度
      final trimmedContent = StringUtils.getSubstring(content, length: _maxContentLength);

      // 获取模型
      final modelName = _getModelNameForFunction(functionType);
      final model = ChatCompletionModel.modelId(modelName);

      // 发送请求
      final response = await client.createChatCompletion(
        request: CreateChatCompletionRequest(
          model: model,
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
      logger.e("[AI服务] 请求失败: ${client.baseUrl} - $e");
      rethrow;
    }
  }

  /// 为特定功能类型创建OpenAI客户端
  ///
  /// [functionType] 功能类型
  /// 返回配置好的客户端实例，或在配置无效时返回null
  Future<OpenAIClient?> _createClientForFunction(int functionType) async {
    try {
      String apiKey;
      String baseUrl;

      // 尝试从AI配置服务获取特定功能的配置
      final apiAddress = AIConfigService.i.getApiAddressForFunction(functionType);
      final apiToken = AIConfigService.i.getApiTokenForFunction(functionType);

      // 如果特定功能配置为空，则使用通用设置
      if (apiAddress.isEmpty || apiToken.isEmpty) {
        apiKey = SettingRepository.getSetting(SettingService.openAITokenKey);
        baseUrl = SettingRepository.getSetting(SettingService.openAIAddressKey);
      } else {
        apiKey = apiToken;
        baseUrl = apiAddress;
      }

      // 创建客户端
      final client = OpenAIClient(apiKey: apiKey, baseUrl: baseUrl);
      logger.i("[AI服务] 为功能类型 $functionType 创建了客户端");

      return client;
    } catch (e) {
      logger.e("[AI服务] 创建客户端失败: $e");
      return null;
    }
  }

  /// 从设置更新默认AI模型
  void _updateDefaultModel() {
    final modelName = SettingRepository.getSetting(SettingService.aiModelKey);
    _defaultModelName = modelName.isEmpty ? 'deepseek-v3' : modelName;
  }

  /// 获取特定功能的模型名称
  String _getModelNameForFunction(int functionType) {
    // 尝试从AI配置服务获取特定功能的模型名称
    final modelName = AIConfigService.i.getModelNameForFunction(functionType);

    // 如果特定功能没有配置模型名称，则使用默认模型
    if (modelName.isEmpty) {
      return _defaultModelName;
    }

    return modelName;
  }

  // MARK: - 工具方法

  /// 判断指定功能类型的AI服务是否启用
  ///
  /// [functionType] 功能类型
  /// 返回该功能类型的AI配置是否有效
  bool isAiEnabled(int functionType) {
    // 获取特定功能的配置
    final apiAddress = AIConfigService.i.getApiAddressForFunction(functionType);
    final apiToken = AIConfigService.i.getApiTokenForFunction(functionType);

    // 检查配置是否有效
    final isValid = apiAddress.isNotEmpty && apiToken.isNotEmpty;
    logger.d(
      "[AI服务] 功能类型 $functionType 的AI ${isValid ? '已启用' : '未启用'}, API地址=${apiAddress.isNotEmpty}, 令牌=${apiToken.isNotEmpty}",
    );

    return isValid;
  }

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
