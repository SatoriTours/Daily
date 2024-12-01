part of 'ai_service.dart';

extension PartTranslate on AiService {
  Future<String> translate(String text) async {
    if (!SettingService.i.aiEnabled()) return text;
    if (isChinese(text)) {
      return text;
    }
    // logger.i("[AI翻译]: ${getSubstring(text)}");
    final res = await _sendRequest(
      _translateRole,
      _translatePrompt(text),
    );
    return res?.choices.first.message.content ?? '';
  }

  String get _translateRole => '你是一个翻译助手, 能够将任何文本翻译成中文';
  String _translatePrompt(String text) => '''
请将以下文本翻译成中文：
```
$text
```
注意事项：
1. 保持原文的意思和语气。
2. 确保翻译流畅自然。
3. 如果有专业术语，请尽量使用常见的翻译。
4. 请注意```内的内容是附加信息，不包括 ```, 翻译时要保持其完整性。
''';
}
