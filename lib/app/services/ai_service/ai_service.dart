import 'dart:convert';

import 'package:daily_satori/app/services/tags_service.dart';
import 'package:openai_dart/openai_dart.dart';

import 'package:daily_satori/app/services/settings_service.dart';
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

  Future<CreateChatCompletionResponse?> _sendRequest(String role, String content,
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
          temperature: 0,
          responseFormat: responseFormat,
        ),
      );
      return res;
    } catch (e) {
      logger.e("[AI] 请求失败: $e");
    }
    return null;
  }

  void reloadClient() {
    if (SettingsService.i.aiEnabled()) _client = _createClient();
  }

  OpenAIClient _createClient() {
    return OpenAIClient(
      apiKey: SettingsService.i.getSetting(SettingsService.openAITokenKey),
      baseUrl: SettingsService.i.getSetting(SettingsService.openAIAddressKey),
    );
  }
}
