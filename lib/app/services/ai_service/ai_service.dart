import 'dart:convert';

import 'package:openai_dart/openai_dart.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/global.dart';

part 'part.translate.dart';
part 'part.summarize.dart';

class AiService {
  AiService._privateConstructor();
  static final AiService _instance = AiService._privateConstructor();
  static AiService get i => _instance;

  OpenAIClient? _client;

  Future<void> init() async {
    logger.i("[初始化服务] AiService");
    reloadClient();
  }

  Future<CreateChatCompletionResponse?> _sendRequest(
      String role, String content,
      {ResponseFormat? responseFormat}) async {
    if (content.isEmpty || content.length <= 5) {
      return null;
    }
    if (_client == null) {
      logger.i("[AiService] AI 未启用");
      return null;
    }
    try {
      content = getSubstring(content, length: 5000);

      final res = await _client!.createChatCompletion(
        request: CreateChatCompletionRequest(
          model: ChatCompletionModel.modelId('gpt-4o-mini'),
          messages: [
            ChatCompletionMessage.system(
              content: role.trim(),
            ),
            ChatCompletionMessage.user(
              content: ChatCompletionUserMessageContent.string(content.trim()),
            ),
          ],
          temperature: 0.5,
          responseFormat: responseFormat,
        ),
      );
      return res;
    } catch (e) {
      logger.e("[AI] 请求失败: ${e.toString()}");
    }
    return null;
  }

  void reloadClient() {
    if (SettingService.i.aiEnabled()) _client = _createClient();
  }

  OpenAIClient _createClient() {
    return OpenAIClient(
      apiKey: SettingService.i.getSetting(SettingService.openAITokenKey),
      baseUrl: SettingService.i.getSetting(SettingService.openAIAddressKey),
    );
  }
}
