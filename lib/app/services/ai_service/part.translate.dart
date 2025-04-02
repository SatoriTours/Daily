part of 'ai_service.dart';

/// AI翻译扩展
///
/// 提供文本翻译功能
extension PartTranslate on AiService {
  /// 将文本翻译成中文
  ///
  /// [text] 需要翻译的文本
  /// [functionType] 功能类型，默认为0（通用配置）
  Future<String> translateToChinese(String text, {int functionType = 0}) async {
    if (text.isEmpty) return '';

    // 验证AI服务可用性
    if (!isAiEnabled(functionType)) {
      logger.i("[AI翻译] 功能类型 $functionType 的AI服务未启用，跳过翻译");
      return text;
    }

    logger.i("[AI翻译] 翻译文本中...");

    // 准备翻译提示
    final role = _renderTemplate(PluginService.i.getTranslateRole(), {'sourceLanguage': '非中文', 'targetLanguage': '中文'});

    // 发送请求
    final response = await _sendRequest(role, text, functionType: functionType);

    // 处理响应
    final result = response?.choices.first.message.content ?? '';
    if (result.isEmpty) {
      logger.w("[AI翻译] 翻译失败，返回原文");
      return text;
    }

    logger.i("[AI翻译] 翻译完成");
    return result;
  }

  /// 将文本翻译成英文
  ///
  /// [text] 需要翻译的文本
  /// [functionType] 功能类型，默认为0（通用配置）
  Future<String> translateToEnglish(String text, {int functionType = 0}) async {
    if (text.isEmpty) return '';

    // 验证AI服务可用性
    if (!isAiEnabled(functionType)) {
      logger.i("[AI翻译] 功能类型 $functionType 的AI服务未启用，跳过翻译");
      return text;
    }

    logger.i("[AI翻译] 翻译文本中...");

    // 准备翻译提示
    final role = _renderTemplate(PluginService.i.getTranslateRole(), {'sourceLanguage': '非英文', 'targetLanguage': '英文'});

    // 发送请求
    final response = await _sendRequest(role, text, functionType: functionType);

    // 处理响应
    final result = response?.choices.first.message.content ?? '';
    if (result.isEmpty) {
      logger.w("[AI翻译] 翻译失败，返回原文");
      return text;
    }

    logger.i("[AI翻译] 翻译完成");
    return result;
  }

  /// 将文本翻译成中文(兼容旧版API)
  ///
  /// [text] 需要翻译的文本
  Future<String> translate(String text) async {
    return translateToChinese(text, functionType: 1);
  }
}
