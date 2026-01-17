import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/pages/article_detail/views/widgets/article_image_view.dart';
import 'package:daily_satori/app/pages/article_detail/views/widgets/article_tags.dart';
import 'package:daily_satori/app/pages/article_detail/providers/article_detail_controller_provider.dart';

class SummaryTab extends ConsumerWidget {
  final int articleId;

  const SummaryTab({super.key, required this.articleId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controllerState = ref.watch(
      articleDetailControllerProvider(articleId),
    );
    final article = controllerState.articleModel;

    if (article == null) {
      return const Center(child: Text('文章不存在'));
    }

    final hasAiTitle = article.aiTitle?.isNotEmpty ?? false;
    final hasAiContent = article.aiContent?.isNotEmpty ?? false;
    final hasHeaderImage =
        article.shouldShowHeaderImage() ||
        (article.coverImageUrl?.isNotEmpty ?? false);
    final hasComment = article.comment?.isNotEmpty ?? false;

    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(vertical: Dimensions.spacingL),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (hasHeaderImage) _buildHeaderImage(article),
          _buildTitle(context, article, hasAiTitle),
          _buildTags(controllerState.tags),
          _buildContent(article, hasAiContent),
          if (hasComment) _buildCommentSection(context, article),
        ],
      ),
    );
  }

  Widget _buildHeaderImage(ArticleModel article) {
    return Padding(
      padding: const EdgeInsets.only(bottom: Dimensions.spacingS),
      child: ArticleImageView(
        imagePath: article.getHeaderImagePath(),
        article: article,
        networkUrl: article.coverImageUrl,
      ),
    );
  }

  Widget _buildTitle(
    BuildContext context,
    ArticleModel article,
    bool isAnimated,
  ) {
    final title = isAnimated ? article.aiTitle! : article.showTitle();

    return Padding(
      padding: const EdgeInsets.fromLTRB(
        Dimensions.spacingL,
        Dimensions.spacingL,
        Dimensions.spacingL,
        Dimensions.spacingM,
      ),
      child: AnimatedText(
        text: title,
        style: AppTheme.getTextTheme(context).titleLarge,
      ),
    );
  }

  Widget _buildTags(String tags) {
    if (tags.isEmpty) return const SizedBox.shrink();

    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: Dimensions.spacingL,
        vertical: Dimensions.spacingM,
      ),
      child: ArticleTags(tags: tags),
    );
  }

  Widget _buildContent(ArticleModel article, bool isAnimated) {
    final content = isAnimated ? article.aiContent! : article.showContent();

    return Padding(
      padding: const EdgeInsets.fromLTRB(
        Dimensions.spacingL,
        Dimensions.spacingM,
        Dimensions.spacingL,
        Dimensions.spacingL,
      ),
      child: AnimatedContent(content: content, isAnimated: isAnimated),
    );
  }

  Widget _buildCommentSection(BuildContext context, ArticleModel article) {
    final textTheme = AppTheme.getTextTheme(context);

    return Padding(
      padding: Dimensions.paddingPage,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '评论',
            style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
          ),
          Dimensions.verticalSpacerM,
          Text(article.comment ?? '', style: textTheme.bodyMedium),
        ],
      ),
    );
  }
}

/// 动画文本组件
class AnimatedText extends StatefulWidget {
  final String text;
  final TextStyle? style;

  const AnimatedText({super.key, required this.text, this.style});

  @override
  State<AnimatedText> createState() => _AnimatedTextState();
}

class _AnimatedTextState extends State<AnimatedText>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 500),
      vsync: this,
    );
    _animation = CurvedAnimation(parent: _controller, curve: Curves.easeInOut);
    _controller.forward();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _animation,
      builder: (context, child) {
        return Opacity(opacity: _animation.value, child: child);
      },
      child: Text(widget.text, style: widget.style),
    );
  }
}

/// 动画内容组件
class AnimatedContent extends StatefulWidget {
  final String content;
  final bool isAnimated;

  const AnimatedContent({
    super.key,
    required this.content,
    required this.isAnimated,
  });

  @override
  State<AnimatedContent> createState() => _AnimatedContentState();
}

class _AnimatedContentState extends State<AnimatedContent>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 800),
      vsync: this,
    );
    _animation = CurvedAnimation(parent: _controller, curve: Curves.easeInOut);
    if (widget.isAnimated) {
      _controller.forward();
    } else {
      _controller.value = 1.0;
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return AnimatedBuilder(
      animation: _animation,
      builder: (context, child) {
        return Opacity(opacity: _animation.value, child: child);
      },
      child: _buildFormattedContent(textTheme, colorScheme),
    );
  }

  Widget _buildFormattedContent(TextTheme textTheme, ColorScheme colorScheme) {
    final sections = _parseContent(widget.content);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (sections['summary'].isNotEmpty) ...[
          Text(
            sections['summary'],
            style: AppTypography.bodyLarge.copyWith(
              height: 1.8,
              color: colorScheme.onSurface,
            ),
          ),
        ],
        if (sections['hasKeyPoints'] &&
            (sections['keyPoints'] as List).isNotEmpty) ...[
          Dimensions.verticalSpacerXl,
          _buildKeyPointsSection(textTheme, colorScheme, sections['keyPoints']),
        ],
      ],
    );
  }

  Widget _buildKeyPointsSection(
    TextTheme textTheme,
    ColorScheme colorScheme,
    List<String> keyPoints,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '核心观点',
          style: textTheme.titleMedium?.copyWith(
            fontWeight: FontWeight.bold,
            color: colorScheme.primary,
          ),
        ),
        Dimensions.verticalSpacerM,
        ...keyPoints.asMap().entries.map<Widget>((entry) {
          final index = entry.key;
          final point = entry.value;
          final cleanPoint = point.replaceFirst(RegExp(r'^\d+\.\s*'), '');

          return Padding(
            padding: const EdgeInsets.only(bottom: Dimensions.spacingL),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  margin: const EdgeInsets.only(
                    top: Dimensions.spacingXs / 2,
                    right: Dimensions.spacingM,
                  ),
                  width: Dimensions.iconSizeL,
                  height: Dimensions.iconSizeL,
                  decoration: BoxDecoration(
                    color: colorScheme.primary,
                    shape: BoxShape.circle,
                  ),
                  child: Center(
                    child: Text(
                      '${index + 1}',
                      style: textTheme.labelLarge?.copyWith(
                        color: colorScheme.onPrimary,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
                Expanded(
                  child: Text(
                    cleanPoint,
                    style: AppTypography.bodyLarge.copyWith(
                      height: 1.8,
                      color: colorScheme.onSurface,
                    ),
                  ),
                ),
              ],
            ),
          );
        }),
      ],
    );
  }

  Map<String, dynamic> _parseContent(String content) {
    final result = <String, dynamic>{
      'summary': '',
      'keyPoints': <String>[],
      'hasKeyPoints': false,
    };

    if (content.contains('## 核心观点') || content.contains('核心观点')) {
      result['hasKeyPoints'] = true;
      final parts = content.split(RegExp(r'##?\s*核心观点'));
      if (parts.length > 1) {
        result['summary'] = _cleanMarkdownHeaders(parts[0]).trim();
        final keyPointsSection = parts[1].trim();
        final lines = keyPointsSection.split('\n');
        for (final line in lines) {
          final trimmed = line.trim();
          if (RegExp(r'^\d+\.\s+.+').hasMatch(trimmed)) {
            result['keyPoints'].add(trimmed);
          }
        }
      }
    } else {
      result['summary'] = _cleanMarkdownHeaders(content);
    }

    return result;
  }

  String _cleanMarkdownHeaders(String content) {
    var cleaned = content.replaceAll(RegExp(r'^#\s+', multiLine: true), '');
    cleaned = cleaned.replaceAll(RegExp(r'^##\s+', multiLine: true), '');
    cleaned = cleaned.replaceAll(RegExp(r'^###\s+', multiLine: true), '');
    cleaned = cleaned.replaceAll(RegExp(r'\n{3,}'), '\n\n');
    return cleaned.trim();
  }
}
