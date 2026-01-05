import 'dart:convert';

import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:openai_dart/openai_dart.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/ai_config_service.dart';
import 'package:jinja/jinja.dart';
import 'package:daily_satori/app/utils/string_utils.dart';
import 'package:daily_satori/app/config/app_config.dart';

part 'part.translate.dart';
part 'part.summarize.dart';
part 'part.html_to_markdown.dart';

/// AI服务类
///
/// 负责处理AI相关功能，包括文本翻译和内容摘要
class AiService {
  AiService._privateConstructor();
  static final AiService _instance = AiService._privateConstructor();
  static AiService get i => _instance;

  /// 默认温度值（控制输出随机性，已迁移至 AIConfig）
  static double get _defaultTemperature => AIConfig.defaultTemperature;

  /// 文本最大处理长度（已迁移至 AIConfig）
  static int get _maxContentLength => AIConfig.maxContentLength;

  /// 初始化AI服务
  Future<void> init() async {}

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
        apiKey = SettingRepository.i.getSetting(SettingService.openAITokenKey);
        baseUrl = SettingRepository.i.getSetting(SettingService.openAIAddressKey);
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

  /// 获取特定功能的模型名称
  String _getModelNameForFunction(int functionType) {
    // 尝试从AI配置服务获取特定功能的模型名称
    final modelName = AIConfigService.i.getModelNameForFunction(functionType);
    return modelName;
  }

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

    return isValid;
  }

  /// 渲染模板
  ///
  /// 使用 Jinja2 语法将变量注入模板
  /// [template] 模板字符串
  /// [context] 变量上下文
  String _renderTemplate(String template, Map<String, String> context) {
    try {
      final env = Environment();
      final tmpl = env.fromString(template);
      return tmpl.render(context);
    } catch (e) {
      logger.e("[AI服务] 模板渲染失败: $e");
      logger.e("[AI服务] 模板: $template");
      logger.e("[AI服务] 上下文: $context");
      return template;
    }
  }

  /// 获取AI完成结果
  ///
  /// [prompt] 提示文本
  /// [functionType] 功能类型，默认为0（通用配置）
  Future<String> getCompletion(String prompt, {int functionType = 0}) async {
    if (prompt.isEmpty) return '';

    // 验证AI服务可用性
    if (!isAiEnabled(functionType)) {
      logger.i("[AI服务] 功能类型 $functionType 的AI服务未启用，跳过请求");
      return '';
    }

    logger.i("[AI服务] 发送AI请求中...");

    // 系统角色设置
    const role = "你是一个帮助用户回答问题的AI助手。请严格按照用户的要求提供信息。如果要求JSON格式输出，请确保返回有效的JSON数据。";

    // 发送请求
    final response = await _sendRequest(role, prompt, functionType: functionType);

    // 处理响应
    final result = response?.choices.first.message.content ?? '';
    if (result.isEmpty) {
      logger.w("[AI服务] 请求失败，返回空结果");
      return '';
    }

    logger.i("[AI服务] 请求完成");
    return result;
  }
}
