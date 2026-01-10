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
import 'package:daily_satori/app/services/service_base.dart';

part 'part.translate.dart';
part 'part.summarize.dart';
part 'part.html_to_markdown.dart';

class AiService extends AppService {
  AiService._();
  static final AiService _instance = AiService._();
  static AiService get i => _instance;

  static double get _defaultTemperature => AIConfig.defaultTemperature;
  static int get _maxContentLength => AIConfig.maxContentLength;

  @override
  Future<void> init() async {}

  Future<CreateChatCompletionResponse?> _sendRequest(
    String role,
    String content, {
    AIFunctionType functionType = AIFunctionType.general,
    ResponseFormat responseFormat = const ResponseFormat.text(),
  }) async {
    if (content.isEmpty || content.length <= 5) {
      logger.i('[AI服务] 内容过短，跳过请求');
      return null;
    }

    final client = await _createClientForFunction(functionType);
    if (client == null) {
      logger.e('[AI服务] 无法创建AI客户端，请检查配置');
      return null;
    }

    try {
      final trimmedContent = StringUtils.getSubstring(content, length: _maxContentLength);
      final modelName = _getModelNameForFunction(functionType);
      final model = ChatCompletionModel.modelId(modelName);

      final response = await client.createChatCompletion(
        request: CreateChatCompletionRequest(
          model: model,
          messages: [
            ChatCompletionMessage.system(content: role.trim()),
            ChatCompletionMessage.user(
              content: ChatCompletionUserMessageContent.string(trimmedContent.trim()),
            ),
          ],
          temperature: _defaultTemperature,
          responseFormat: responseFormat,
        ),
      );

      return response;
    } catch (e) {
      logger.e('[AI服务] 请求失败: ${client.baseUrl} - $e');
      rethrow;
    }
  }

  Future<OpenAIClient?> _createClientForFunction(AIFunctionType type) async {
    try {
      String apiKey;
      String baseUrl;

      final apiAddress = AIConfigService.i.getApiAddressForFunction(type);
      final apiToken = AIConfigService.i.getApiTokenForFunction(type);

      if (apiAddress.isEmpty || apiToken.isEmpty) {
        apiKey = SettingRepository.i.getSetting(SettingService.openAITokenKey);
        baseUrl = SettingRepository.i.getSetting(SettingService.openAIAddressKey);
      } else {
        apiKey = apiToken;
        baseUrl = apiAddress;
      }

      final client = OpenAIClient(apiKey: apiKey, baseUrl: baseUrl);
      logger.i('[AI服务] 为功能类型 ${type.displayName} 创建了客户端');

      return client;
    } catch (e) {
      logger.e('[AI服务] 创建客户端失败: $e');
      return null;
    }
  }

  String _getModelNameForFunction(AIFunctionType type) =>
      AIConfigService.i.getModelNameForFunction(type);

  bool isAiEnabled(AIFunctionType type) {
    final apiAddress = AIConfigService.i.getApiAddressForFunction(type);
    final apiToken = AIConfigService.i.getApiTokenForFunction(type);
    return apiAddress.isNotEmpty && apiToken.isNotEmpty;
  }

  String _renderTemplate(String template, Map<String, String> context) {
    try {
      final env = Environment();
      return env.fromString(template).render(context);
    } catch (e) {
      logger.e('[AI服务] 模板渲染失败: $e');
      return template;
    }
  }

  Future<String> getCompletion(String prompt, {AIFunctionType type = AIFunctionType.general}) async {
    if (prompt.isEmpty) return '';

    if (!isAiEnabled(type)) {
      logger.i('[AI服务] 功能类型 ${type.displayName} 的AI服务未启用，跳过请求');
      return '';
    }

    logger.i('[AI服务] 发送AI请求中...');

    const role = '你是一个帮助用户回答问题的AI助手。请严格按照用户的要求提供信息。如果要求JSON格式输出，请确保返回有效的JSON数据。';

    final response = await _sendRequest(role, prompt, functionType: type);

    final result = response?.choices.first.message.content ?? '';
    if (result.isEmpty) {
      logger.w('[AI服务] 请求失败，返回空结果');
      return '';
    }

    logger.i('[AI服务] 请求完成');
    return result;
  }
}
