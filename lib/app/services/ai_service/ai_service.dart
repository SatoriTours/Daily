import 'dart:convert';

import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:openai_dart/openai_dart.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/global.dart';
import 'package:template_expressions/template_expressions.dart';

part 'part.translate.dart';
part 'part.summarize.dart';

class AiService {
  AiService._privateConstructor();
  static final AiService _instance = AiService._privateConstructor();
  static AiService get i => _instance;

  var _model = ChatCompletionModel.modelId('gpt-4o-mini');

  Future<void> init() async {
    logger.i("[初始化服务] AiService");
  }

  Future<CreateChatCompletionResponse?> _sendRequest(String role, String content,
      {ResponseFormat responseFormat = const ResponseFormat.text()}) async {
    if (content.isEmpty || content.length <= 5) {
      return null;
    }
    final client = _createClient();
    try {
      content = getSubstring(content, length: 5000);

      final res = await client.createChatCompletion(
        request: CreateChatCompletionRequest(
          model: _model,
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
      logger.e("[AI] 请求失败: ${client.baseUrl} ${e.toString()}");
    }
    return null;
  }

  OpenAIClient _createClient() {
    var modelName = SettingService.i.getSetting(SettingService.aiModelKey);
    _model = ChatCompletionModel.modelId(modelName);

    return OpenAIClient(
      apiKey: SettingService.i.getSetting(SettingService.openAITokenKey),
      baseUrl: SettingService.i.getSetting(SettingService.openAIAddressKey),
    );
  }
}
