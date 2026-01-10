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
  Future<String> convertHtmlToMarkdown(
    String htmlContent, {
    String? title,
    DateTime? updatedAt,
  }) async {
    final markdown = await htmlToMarkdown(htmlContent, functionType: 1);
    return _postProcessMarkdown(markdown);
  }

  /// 后处理Markdown内容，优化排版
  ///
  /// 修复常见排版问题：
  /// - 删除多余空行
  /// - 优化列表间距
  /// - 统一标点符号
  /// - 修复标题格式
  String _postProcessMarkdown(String markdown) {
    if (markdown.isEmpty) return markdown;

    logger.i("[AI转换] 开始后处理Markdown内容，长度: ${markdown.length}");

    // 分步骤处理
    var processed = markdown;

    // 1. 修复标题格式：避免将普通段落误认为标题
    processed = _fixTitleFormat(processed);

    // 2. 删除多余空行：确保段落间只有一个空行
    processed = _removeExtraBlankLines(processed);

    // 3. 优化列表间距：列表项之间不空行，列表与其他内容之间空一行
    processed = _optimizeListSpacing(processed);

    // 4. 统一标点符号：确保使用中文标点
    processed = _normalizePunctuation(processed);

    logger.i("[AI转换] Markdown后处理完成，长度: ${processed.length}");
    return processed;
  }

  /// 修复标题格式
  ///
  /// 避免将普通段落误认为标题，同时保留真正的文章标题
  String _fixTitleFormat(String text) {
    var processed = text;

    // 检查是否第一个段落被错误地标记为标题
    final lines = processed.split('\n');
    if (lines.isNotEmpty &&
        lines.first.startsWith('# ') &&
        lines.first.length < 30) {
      // 如果第一个标题很短，检查是否是常见的误判情况
      final firstLine = lines.first;
      final content = firstLine.substring(2).trim();

      // 常见误判的关键词 - 这些通常是文章内容中的小标题，不是真正的文章标题
      final falseTitleKeywords = [
        '主要功能',
        '功能特点',
        '产品介绍',
        '使用说明',
        '注意事项',
        '基本功能',
        '核心功能',
        '功能列表',
        '功能概述',
        '目录',
        '内容简介',
        '特点',
        '优势',
        '使用方法',
        '安装步骤',
        '配置说明',
        '常见问题',
      ];

      // 检查是否是真正的文章标题还是误判的小标题
      bool isFalseTitle = falseTitleKeywords.any(
        (keyword) => content.contains(keyword),
      );

      // 如果是真正的文章标题，应该包含完整的句子或表达完整意思
      bool isRealTitle =
          content.contains('。') ||
          content.contains('！') ||
          content.contains('？') ||
          content.length > 15; // 真正的标题通常更长

      // 如果是常见的误判关键词，则移除标题标记
      if (isFalseTitle && !isRealTitle) {
        lines[0] = content;
        processed = lines.join('\n');
        logger.i("[AI转换] 修复了标题误判: $firstLine -> $content");
      } else {
        // 保留真正的标题
        logger.i("[AI转换] 保留文章标题: $firstLine");
      }
    }

    return processed;
  }

  /// 删除多余空行
  String _removeExtraBlankLines(String text) {
    // 将连续的空行（2个或更多）替换为单个空行
    return text.replaceAll(RegExp(r'\n{3,}'), '\n\n');
  }

  /// 优化列表间距
  String _optimizeListSpacing(String text) {
    final lines = text.split('\n');
    final result = <String>[];

    for (int i = 0; i < lines.length; i++) {
      final currentLine = lines[i];

      // 检查下一行是否是列表项
      final nextLine = i + 1 < lines.length ? lines[i + 1] : '';
      final isNextListItem =
          nextLine.trim().startsWith('- ') ||
          nextLine.trim().startsWith('* ') ||
          RegExp(r'^\s*\d+\.\s+').hasMatch(nextLine.trim());

      // 检查上一行是否是列表项
      final prevLine = i > 0 ? lines[i - 1] : '';
      final isPrevListItem =
          prevLine.trim().startsWith('- ') ||
          prevLine.trim().startsWith('* ') ||
          RegExp(r'^\s*\d+\.\s+').hasMatch(prevLine.trim());

      // 如果是列表项之间的空行，跳过
      if (currentLine.trim().isEmpty && isPrevListItem && isNextListItem) {
        continue;
      }

      result.add(currentLine);
    }

    return result.join('\n');
  }

  /// 统一标点符号
  String _normalizePunctuation(String text) {
    var processed = text;

    // 英文逗号转中文逗号（在中文语境下）
    processed = processed.replaceAll(',', '，');

    // 英文句号转中文句号（在中文语境下）
    processed = processed.replaceAllMapped(
      RegExp(r'([^a-zA-Z0-9])\.(?=\s|$)'),
      (match) => '${match.group(1)}。',
    );

    // 英文冒号转中文冒号
    processed = processed.replaceAll(':', '：');

    // 英文分号转中文分号
    processed = processed.replaceAll(';', '；');

    // 英文问号转中文问号
    processed = processed.replaceAll('?', '？');

    // 英文感叹号转中文感叹号
    processed = processed.replaceAll('!', '！');

    return processed;
  }
}
