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
    logger.i("[AI翻译]: ${text.substring(0, 50)}");
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

  Future<String> summarize(String text) async {
    logger.i("[AI总结]: ${text.substring(0, 50)}");
    return await sendRequest(
        '你是一个信息提取助手，能够从文章中提取关键信息和要点',
        ''''
请对以下文章进行总结：
```
$text
```
注意事项：
1. 提取文章的主要观点和关键信息。
2. 确保总结简洁明了。
3. 保持原文的意思，不要添加个人观点。
4. 请注意```内的内容是附加信息，翻译时要保持其完整性。
        ''',
    );
  }

  Future<String> sendRequest(String role, String content) async {
    if (content.isEmpty || content.length <= 5) {
      return "";
    }

    content = content.length > 500 ? content.substring(0, 500) : content;

    final client = createClient();
    final res = await client.createChatCompletion(
      request: CreateChatCompletionRequest(
        model: ChatCompletionModel.modelId('gpt-4o-mini'),
        messages: [
          ChatCompletionMessage.system(
            content: role,
          ),
          ChatCompletionMessage.user(
            content: ChatCompletionUserMessageContent.string(content),
          ),
        ],
        temperature: 0,
      ),
    );
    // logger.d(res.choices);
    return res.choices.first.message.content ?? '';
  }

  OpenAIClient createClient() {
    return OpenAIClient(
      apiKey: SettingsService.instance.getSetting(SettingsService.openAITokenKey),
      baseUrl: SettingsService.instance.getSetting(SettingsService.openAIAddressKey),
    );
  }
}
