part of 'ai_service.dart';

/// HTML转Markdown扩展
///
/// 提供将HTML内容转换为格式良好的Markdown的功能
extension PartHtmlToMarkdown on AiService {
  /// 将HTML内容转换为Markdown格式
  ///
  /// 分析HTML内容,保持原始内容不变,生成格式良好的Markdown
  /// [htmlContent] 原始HTML内容
  /// [title] 文章标题
  /// [updatedAt] 更新时间
  Future<String> convertHtmlToMarkdown(String htmlContent, {String? title, DateTime? updatedAt}) async {
    if (htmlContent.isEmpty) return '';

    // 检查AI是否启用
    if (!SettingRepository.aiEnabled(SettingService.openAITokenKey, SettingService.openAIAddressKey)) {
      logger.i("[AI转换] AI服务未启用,跳过HTML到Markdown的转换");
      return '';
    }

    logger.i("[AI转换] 将HTML转换为Markdown中...");

    // 构建系统提示
    final roleTemplate = PluginService.i.getHtmlToMarkdownRole();

    // 发送请求
    final response = await _sendRequest(roleTemplate, htmlContent);

    // 处理响应
    final result = response?.choices.first.message.content ?? '';
    if (result.isEmpty) {
      logger.w("[AI转换] HTML到Markdown转换失败");
      return '';
    }

    logger.i("[AI转换] HTML到Markdown转换完成: ${getSubstring(result)}");
    return result;
  }
}
