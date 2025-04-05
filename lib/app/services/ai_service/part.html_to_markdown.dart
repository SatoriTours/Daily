part of 'ai_service.dart';

/// HTML转Markdown扩展
///
/// 提供HTML内容转换为Markdown的功能
extension PartHtmlToMarkdown on AiService {
  /// 将HTML转换为Markdown
  ///
  /// [html] HTML内容
  /// [functionType] 功能类型，默认为1（文章分析）
  Future<String> htmlToMarkdown(String html, {int functionType = 1}) async {
    if (html.isEmpty) return '';

    // 验证AI服务可用性
    if (!isAiEnabled(functionType)) {
      logger.i("[AI转换] 功能类型 $functionType 的AI服务未启用，跳过HTML到Markdown转换");
      return html;
    }

    logger.i("[AI转换] 将HTML转换为Markdown中...");

    // 准备转换提示
    final role = PluginService.i.getHtmlToMarkdownRole();

    // 发送请求
    final response = await _sendRequest(role, html, functionType: functionType);

    // 处理响应
    final result = response?.choices.first.message.content ?? '';
    if (result.isEmpty) {
      logger.w("[AI转换] HTML到Markdown转换失败");
      return html;
    }

    logger.i("[AI转换] HTML到Markdown转换完成");
    return result;
  }

  /// 将HTML转换为Markdown(兼容旧版API)
  ///
  /// [htmlContent] HTML内容
  /// [title] 文章标题，可选
  /// [updatedAt] 更新时间，可选
  Future<String> convertHtmlToMarkdown(String htmlContent, {String? title, DateTime? updatedAt}) async {
    return htmlToMarkdown(htmlContent, functionType: 1);
  }
}
