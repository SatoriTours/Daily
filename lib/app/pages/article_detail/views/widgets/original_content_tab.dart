import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:flutter_html/flutter_html.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:url_launcher/url_launcher.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/pages/article_detail/providers/article_detail_controller_provider.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/styles/index.dart';

/// 文章原始内容标签页
/// 支持两种内容格式：
/// 1. Markdown格式（优先显示）
/// 2. HTML格式
class OriginalContentTab extends ConsumerWidget {
  final int? articleId;
  final ArticleModel? article;

  const OriginalContentTab({super.key, this.articleId, required this.article});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (article == null) {
      return const Center(child: Text('文章不存在'));
    }

    return _hasMarkdownContent ? _buildMarkdownView(context, ref) : _buildHtmlView(context);
  }

  /// 判断是否有Markdown内容
  bool get _hasMarkdownContent => article?.aiMarkdownContent?.isNotEmpty ?? false;

  /// 构建Markdown视图
  Widget _buildMarkdownView(BuildContext context, WidgetRef ref) {
    final markdownContent = article?.aiMarkdownContent;
    if (markdownContent == null || markdownContent.isEmpty) {
      return _buildEmptyState(context, ref, message: "尚未生成Markdown内容", showGenerateButton: true);
    }

    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 标题区域
          _buildTitleSection(context, textTheme, colorScheme),

          // Markdown内容区域
          Padding(
            padding: Dimensions.paddingPage,
            child: MarkdownBody(
              data: markdownContent,
              selectable: true,
              styleSheet: MarkdownStyles.getStyleSheet(context),
              onTapLink: _handleLinkTap,
            ),
          ),
        ],
      ),
    );
  }

  /// 构建HTML视图
  Widget _buildHtmlView(BuildContext context) {
    final htmlContent = article?.htmlContent;
    if (htmlContent == null || htmlContent.isEmpty) {
      return _buildEmptyState(context, null, message: "无法加载原文内容", showGenerateButton: false);
    }

    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 标题区域
          _buildTitleSection(context, textTheme, colorScheme),

          // HTML内容区域
          Padding(
            padding: Dimensions.paddingPage,
            child: Html(
              data: htmlContent,
              style: HtmlStyles.getStyles(context),
              onLinkTap: (url, _, _) => _handleLinkTap(null, url, null),
              shrinkWrap: true,
            ),
          ),
        ],
      ),
    );
  }

  /// 构建标题区域
  Widget _buildTitleSection(BuildContext context, TextTheme textTheme, ColorScheme colorScheme) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(
        Dimensions.spacingL,
        Dimensions.spacingXl,
        Dimensions.spacingL,
        Dimensions.spacingL,
      ),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [colorScheme.primaryContainer.withAlpha(30), colorScheme.surface],
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            article!.showTitle(),
            style: textTheme.headlineMedium?.copyWith(
              fontWeight: FontWeight.bold,
              height: 1.3,
              color: colorScheme.onSurface,
            ),
          ),
          Dimensions.verticalSpacerM,
          Container(
            height: 3,
            width: 60,
            decoration: BoxDecoration(
              gradient: LinearGradient(colors: [colorScheme.primary, colorScheme.primary.withAlpha(0)]),
              borderRadius: BorderRadius.circular(Dimensions.radiusXs),
            ),
          ),
        ],
      ),
    );
  }

  /// 构建空状态视图
  Widget _buildEmptyState(
    BuildContext context,
    WidgetRef? ref, {
    required String message,
    required bool showGenerateButton,
  }) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.article_outlined, size: Dimensions.iconSizeXl, color: colorScheme.onSurfaceVariant),
          Dimensions.verticalSpacerM,
          Text(message, style: textTheme.bodyLarge),
          if (showGenerateButton) ...[
            Dimensions.verticalSpacerM,
            ElevatedButton.icon(
              onPressed: () => _generateMarkdown(context, ref!),
              icon: const Icon(Icons.article_outlined),
              label: const Text("生成Markdown"),
            ),
          ],
        ],
      ),
    );
  }

  /// 处理链接点击事件
  void _handleLinkTap(String? text, String? href, String? title) async {
    if (href == null || href.isEmpty) {
      logger.w('链接为空');
      return;
    }

    // 安全检查URL格式
    if (!(href.startsWith('http://') || href.startsWith('https://'))) {
      logger.e('无效链接格式: $href');
      UIUtils.showError('无效链接格式');
      return;
    }

    try {
      final uri = Uri.parse(href);
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      } else {
        logger.e('无法打开链接: $href');
        UIUtils.showError('无法打开链接');
      }
    } catch (e) {
      logger.e('打开链接时出错: $e');
      UIUtils.showError('打开链接时出错');
    }
  }

  /// 生成Markdown内容
  Future<void> _generateMarkdown(BuildContext context, WidgetRef ref) async {
    if (article == null || articleId == null) return;

    try {
      // 显示加载对话框
      DialogUtils.showLoading(tips: '正在生成Markdown内容，请稍候...');

      // 生成Markdown内容
      if (articleId != null) {
        await ref.read(articleDetailControllerProvider(articleId!).notifier).generateMarkdownContent();
      }

      // 关闭加载对话框
      DialogUtils.hideLoading();

      // 检查生成结果
      if (article?.aiMarkdownContent?.isNotEmpty ?? false) {
        logger.i('Markdown内容生成成功');
        UIUtils.showSuccess('Markdown内容生成成功');
      } else {
        logger.e('生成Markdown内容失败');
        UIUtils.showError('生成Markdown内容失败');
      }
    } catch (e) {
      DialogUtils.hideLoading();
      logger.e('生成Markdown时出错: $e');
      UIUtils.showError('生成过程中出错: $e');
    }
  }
}
