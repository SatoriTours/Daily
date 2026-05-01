import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/pages/article_detail/providers/article_detail_controller_provider.dart';
import 'package:flutter_html/flutter_html.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:url_launcher/url_launcher.dart';

class OriginalContentTab extends ConsumerWidget {
  final int articleId;

  const OriginalContentTab({super.key, required this.articleId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controllerState = ref.watch(
      articleDetailControllerProvider(articleId),
    );
    final article = controllerState.articleModel;

    if (article == null) {
      return const Center(child: Text('文章不存在'));
    }

    final hasMarkdown = article.aiMarkdownContent?.isNotEmpty ?? false;

    return hasMarkdown
        ? _buildMarkdownView(context, ref, article)
        : _buildHtmlView(context, article);
  }

  Widget _buildMarkdownView(
    BuildContext context,
    WidgetRef ref,
    ArticleModel article,
  ) {
    final markdownContent = article.aiMarkdownContent;
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    if (markdownContent == null || markdownContent.isEmpty) {
      return _buildEmptyMarkdownState(context, ref);
    }

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildTitleSection(context, textTheme, colorScheme, article),
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

  Widget _buildEmptyMarkdownState(BuildContext context, WidgetRef ref) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.article_outlined,
            size: Dimensions.iconSizeXl,
            color: colorScheme.onSurfaceVariant,
          ),
          const SizedBox(height: 16),
          Text('尚未生成Markdown内容', style: textTheme.bodyLarge),
          const SizedBox(height: 16),
          ElevatedButton.icon(
            onPressed: () => _generateMarkdown(context, ref),
            icon: const Icon(Icons.auto_awesome),
            label: const Text('生成Markdown'),
          ),
        ],
      ),
    );
  }

  Widget _buildHtmlView(BuildContext context, ArticleModel article) {
    final htmlContent = article.htmlContent;

    if (htmlContent == null || htmlContent.isEmpty) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.article_outlined, size: 64, color: Colors.grey),
            SizedBox(height: 16),
            Text('无法加载原文内容'),
          ],
        ),
      );
    }

    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildTitleSection(context, textTheme, colorScheme, article),
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

  Widget _buildTitleSection(
    BuildContext context,
    TextTheme textTheme,
    ColorScheme colorScheme,
    ArticleModel article,
  ) {
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
          colors: [
            colorScheme.primaryContainer.withAlpha(30),
            colorScheme.surface,
          ],
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            article.showTitle(),
            style: textTheme.headlineMedium?.copyWith(
              fontWeight: FontWeight.bold,
              height: 1.3,
              color: colorScheme.onSurface,
            ),
          ),
          const SizedBox(height: 16),
          Container(
            height: 3,
            width: 60,
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [colorScheme.primary, colorScheme.primary.withAlpha(0)],
              ),
              borderRadius: BorderRadius.circular(Dimensions.radiusXs),
            ),
          ),
        ],
      ),
    );
  }

  void _handleLinkTap(String? text, String? href, String? title) async {
    if (href == null || href.isEmpty) {
      logger.w('链接为空');
      return;
    }

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

  Future<void> _generateMarkdown(BuildContext context, WidgetRef ref) async {
    try {
      DialogUtils.showLoading(tips: '正在生成Markdown内容，请稍候...');
      await ref
          .read(articleDetailControllerProvider(articleId).notifier)
          .generateMarkdownContent();
      DialogUtils.hideLoading();

      final controllerState = ref.read(
        articleDetailControllerProvider(articleId),
      );
      if (controllerState.articleModel?.aiMarkdownContent?.isNotEmpty ??
          false) {
        UIUtils.showSuccess('Markdown内容生成成功');
      } else {
        UIUtils.showError('生成Markdown内容失败');
      }
    } catch (e) {
      DialogUtils.hideLoading();
      logger.e('生成Markdown时出错: $e');
      UIUtils.showError('生成过程中出错: $e');
    }
  }
}
