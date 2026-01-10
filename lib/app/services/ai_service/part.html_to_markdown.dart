part of 'ai_service.dart';

extension PartHtmlToMarkdown on AiService {
  Future<String> htmlToMarkdown(String html, {AIFunctionType type = AIFunctionType.articleAnalysis}) async {
    if (html.isEmpty) return '';

    if (!isAiEnabled(type)) {
      logger.i('[AI转换] 功能类型 ${type.displayName} 的AI服务未启用，跳过HTML到Markdown转换');
      return html;
    }

    logger.i('[AI转换] 将HTML转换为Markdown中...');

    final role = PluginService.i.getHtmlToMarkdownRole();

    final response = await _sendRequest(role, html, functionType: type);

    final result = response?.choices.first.message.content ?? '';
    if (result.isEmpty) {
      logger.w('[AI转换] HTML到Markdown转换失败');
      return html;
    }

    logger.i('[AI转换] HTML到Markdown转换完成');
    return result;
  }

  Future<String> convertHtmlToMarkdown(
    String htmlContent, {
    String? title,
    DateTime? updatedAt,
  }) async {
    final markdown = await htmlToMarkdown(htmlContent);
    return _postProcessMarkdown(markdown);
  }

  String _postProcessMarkdown(String markdown) {
    if (markdown.isEmpty) return markdown;

    logger.i('[AI转换] 开始后处理Markdown内容，长度: ${markdown.length}');

    var processed = markdown;
    processed = _fixTitleFormat(processed);
    processed = _removeExtraBlankLines(processed);
    processed = _optimizeListSpacing(processed);
    processed = _normalizePunctuation(processed);

    logger.i('[AI转换] Markdown后处理完成，长度: ${processed.length}');
    return processed;
  }

  String _fixTitleFormat(String text) {
    var processed = text;
    final lines = processed.split('\n');
    if (lines.isNotEmpty && lines.first.startsWith('# ') && lines.first.length < 30) {
      final firstLine = lines.first;
      final content = firstLine.substring(2).trim();

      const falseTitleKeywords = [
        '主要功能', '功能特点', '产品介绍', '使用说明', '注意事项',
        '基本功能', '核心功能', '功能列表', '功能概述', '目录',
        '内容简介', '特点', '优势', '使用方法', '安装步骤',
        '配置说明', '常见问题',
      ];

      final isFalseTitle = falseTitleKeywords.any((keyword) => content.contains(keyword));
      final isRealTitle = content.contains('。') || content.contains('！') || content.contains('？') || content.length > 15;

      if (isFalseTitle && !isRealTitle) {
        lines[0] = content;
        processed = lines.join('\n');
        logger.i('[AI转换] 修复了标题误判: $firstLine -> $content');
      } else {
        logger.i('[AI转换] 保留文章标题: $firstLine');
      }
    }

    return processed;
  }

  String _removeExtraBlankLines(String text) => text.replaceAll(RegExp(r'\n{3,}'), '\n\n');

  String _optimizeListSpacing(String text) {
    final lines = text.split('\n');
    final result = <String>[];

    for (int i = 0; i < lines.length; i++) {
      final currentLine = lines[i];
      final nextLine = i + 1 < lines.length ? lines[i + 1] : '';
      final prevLine = i > 0 ? lines[i - 1] : '';

      final isNextListItem = nextLine.trim().startsWith('- ') ||
          nextLine.trim().startsWith('* ') ||
          RegExp(r'^\s*\d+\.\s+').hasMatch(nextLine.trim());
      final isPrevListItem = prevLine.trim().startsWith('- ') ||
          prevLine.trim().startsWith('* ') ||
          RegExp(r'^\s*\d+\.\s+').hasMatch(prevLine.trim());

      if (currentLine.trim().isEmpty && isPrevListItem && isNextListItem) {
        continue;
      }

      result.add(currentLine);
    }

    return result.join('\n');
  }

  String _normalizePunctuation(String text) {
    var processed = text;
    processed = processed.replaceAll(',', '，');
    processed = processed.replaceAllMapped(
      RegExp(r'([^a-zA-Z0-9])\.(?=\s|$)'),
      (match) => '${match.group(1)}。',
    );
    processed = processed.replaceAll(':', '：');
    processed = processed.replaceAll(';', '；');
    processed = processed.replaceAll('?', '？');
    processed = processed.replaceAll('!', '！');
    return processed;
  }
}
