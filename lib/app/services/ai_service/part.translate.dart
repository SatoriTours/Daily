part of 'ai_service.dart';

extension PartTranslate on AiService {
  Future<String> translate(String text) async {
    if (!SettingService.i.aiEnabled()) return text;
    if (isChinese(text)) {
      return text;
    }
    // logger.i("[AI翻译]: ${getSubstring(text)}");
    final res = await _sendRequest(
      _translateRole(text),
      _translatePrompt(text),
    );
    return res?.choices.first.message.content ?? '';
  }

  String _translateRole(String text) => _renderTemplate(
        PluginService.i.getTranslateRole(),
        {'text': text},
      );
  String _translatePrompt(String text) => _renderTemplate(
        PluginService.i.getTranslatePrompt(),
        {'text': text},
      );
}
