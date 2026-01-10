part of 'ai_service.dart';

extension PartTranslate on AiService {
  Future<String> translateToChinese(String text, {AIFunctionType type = AIFunctionType.general}) async {
    if (text.isEmpty) return '';

    if (!isAiEnabled(type)) {
      logger.i('[AI翻译] 功能类型 ${type.displayName} 的AI服务未启用，跳过翻译');
      return text;
    }

    logger.i('[AI翻译] 翻译文本中...');

    final role = _renderTemplate(PluginService.i.getTranslateRole(), {});
    logger.i('[AI翻译] 翻译提示: $role');

    final response = await _sendRequest(role, text, functionType: type);

    final result = response?.choices.first.message.content ?? '';
    if (result.isEmpty) {
      logger.w('[AI翻译] 翻译失败，返回原文');
      return text;
    }

    logger.i('[AI翻译] 翻译完成');
    return result;
  }

  Future<String> translate(String text) async =>
      translateToChinese(text, type: AIFunctionType.articleAnalysis);
}
