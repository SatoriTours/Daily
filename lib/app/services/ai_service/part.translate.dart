part of 'ai_service.dart';

/// AI翻译功能扩展
///
/// 提供非中文内容的翻译服务
extension PartTranslate on AiService {
  /// 翻译文本
  ///
  /// 将非中文文本翻译为中文，如果是中文则原样返回
  /// [text] 需要翻译的文本
  Future<String> translate(String text) async {
    // 验证条件：AI可用且非中文文本
    if (!SettingService.i.aiEnabled()) {
      logger.i("[AI翻译] AI服务未启用，跳过翻译");
      return text;
    }

    if (isChinese(text)) {
      logger.i("[AI翻译] 已是中文内容，无需翻译");
      return text;
    }

    logger.i("[AI翻译] 正在翻译: ${getSubstring(text, length: 30)}...");

    // 准备翻译提示
    final role = _translateRole(text);
    final prompt = _translatePrompt(text);

    // 发送翻译请求
    final response = await _sendRequest(role, prompt);

    // 处理响应
    final result = response?.choices.first.message.content ?? '';
    if (result.isEmpty) {
      logger.w("[AI翻译] 翻译结果为空");
      return text;
    }

    logger.i("[AI翻译] 翻译完成");
    return result;
  }

  /// 生成翻译角色提示
  String _translateRole(String text) => _renderTemplate(PluginService.i.getTranslateRole(), {'text': text});

  /// 生成翻译内容提示
  String _translatePrompt(String text) => _renderTemplate(PluginService.i.getTranslatePrompt(), {'text': text});
}
