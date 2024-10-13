import 'package:daily_satori/app/services/settings_service.dart';
import 'package:daily_satori/global.dart';
import 'package:get/get.dart';
import 'package:openai_dart/openai_dart.dart';

class AiService extends GetxService {
  AiService._privateConstructor();
  static final AiService _instance = AiService._privateConstructor();
  static AiService get instance => _instance;

  Future<void> init() async {}

  Future<String> translate(String text) async {
    if (isChinese(text)) {
      return text;
    }
    // logger.i("[AI翻译]: ${getSubstring(text)}");
    return await sendRequest(
      '你是一个翻译助手, 能够将任何文本翻译成中文',
      '''
请将以下文本翻译成中文：
```
$text
```
注意事项：
1. 保持原文的意思和语气。
2. 确保翻译流畅自然。
3. 如果有专业术语，请尽量使用常见的翻译。
4. 请注意```内的内容是附加信息，翻译时要保持其完整性。
        ''',
    );
  }

  Future<String> summarizeOneLine(String text) async {
    // logger.i("[AI总结]: ${getSubstring(text)}");
    return await sendRequest(
      '你是一个文章读者, 能总结文章的核心内容',
      ' 一句话总结一下内容：```$text``` ',
    );
  }

  Future<String> summarize(String text) async {
    // logger.i("[AI总结]: ${getSubstring(text)}");
    return await sendRequest(
      '你是一个文章读者, 能总结文章的核心内容',
      ''''
请对以下文章进行总结成新的文章：
```
$text
```
注意事项：
1. 提取文章的主要观点和关键信息, 用中文输出。
2. 确保总结简洁明了, 直接给结果，不要给其他任何的解释。
3. 保持原文的意思，不要添加个人观点。
4. 请注意```内的内容是附加信息，翻译时要保持其完整性。
        ''',
    );
  }

  Future<String> getRelatedTags() async {
    return "";
  }

  Future<String> sendRequest(String role, String content) async {
    if (content.isEmpty || content.length <= 5) {
      return "";
    }
    try {
      content = getSubstring(content, length: 500);

      final client = createClient();
      final res = await client.createChatCompletion(
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
        ),
      );
      return res.choices.first.message.content ?? '';
    } catch (e) {
      logger.e("[AI] 请求失败: $e");
    }
    return "";
  }

  OpenAIClient createClient() {
    return OpenAIClient(
      apiKey: SettingsService.instance.getSetting(SettingsService.openAITokenKey),
      baseUrl: SettingsService.instance.getSetting(SettingsService.openAIAddressKey),
    );
  }
}
