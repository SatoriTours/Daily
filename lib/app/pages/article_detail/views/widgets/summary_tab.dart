import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/pages/article_detail/views/widgets/article_image_view.dart';
import 'package:daily_satori/app/pages/article_detail/views/widgets/article_tags.dart';
import 'package:daily_satori/app/pages/article_detail/providers/article_detail_controller_provider.dart';
import 'package:daily_satori/app/styles/index.dart';

class SummaryTab extends ConsumerWidget {
  final int? articleId;
  final ArticleModel? article;

  const SummaryTab({super.key, this.articleId, required this.article});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (article == null || articleId == null) {
      return const Center(child: Text('文章不存在'));
    }

    final controllerState = ref.watch(
      articleDetailControllerProvider(articleId!),
    );
    final tags = controllerState.tags;

    final hasAiTitle = article?.aiTitle?.isNotEmpty ?? false;
    final hasAiContent = article?.aiContent?.isNotEmpty ?? false;

    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(vertical: Dimensions.spacingL),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (_hasHeaderImage) _buildHeaderImage(),

          Padding(
            padding: const EdgeInsets.fromLTRB(
              Dimensions.spacingL,
              Dimensions.spacingL,
              Dimensions.spacingL,
              Dimensions.spacingM,
            ),
            child: _AnimatedText(
              text: hasAiTitle ? article!.aiTitle! : article!.showTitle(),
              isAnimated: hasAiTitle,
            ),
          ),

          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: Dimensions.spacingL,
              vertical: Dimensions.spacingM,
            ),
            child: ArticleTags(tags: tags),
          ),

          Padding(
            padding: const EdgeInsets.fromLTRB(
              Dimensions.spacingL,
              Dimensions.spacingM,
              Dimensions.spacingL,
              Dimensions.spacingL,
            ),
            child: _AnimatedContent(
              content: hasAiContent
                  ? article!.aiContent!
                  : article!.showContent(),
              isAnimated: hasAiContent,
            ),
          ),

          if (_hasComment) _buildCommentSection(context),
        ],
      ),
    );
  }

  bool get _hasHeaderImage =>
      article!.shouldShowHeaderImage() ||
      (article!.coverImageUrl?.isNotEmpty ?? false);

  bool get _hasComment => article!.comment?.isNotEmpty ?? false;

  Widget _buildHeaderImage() {
    return Padding(
      padding: const EdgeInsets.only(bottom: Dimensions.spacingS),
      child: ArticleImageView(
        imagePath: article!.getHeaderImagePath(),
        article: article,
        networkUrl: article!.coverImageUrl,
      ),
    );
  }

  Widget _buildCommentSection(BuildContext context) {
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
          Text(article!.comment ?? '', style: textTheme.bodyMedium),
        ],
      ),
    );
  }
}

class _AnimatedText extends StatefulWidget {
  final String text;
  final bool isAnimated;

  const _AnimatedText({required this.text, required this.isAnimated});

  @override
  State<_AnimatedText> createState() => _AnimatedTextState();
}

class _AnimatedTextState extends State<_AnimatedText>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _fadeAnimation;

  bool _wasAnimated = false;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 1000),
      vsync: this,
    );
    _fadeAnimation = CurvedAnimation(
      parent: _controller,
      curve: Curves.easeInOut,
    );

    if (widget.isAnimated) {
      _controller.value = 1.0;
    } else {
      _controller.value = 1.0;
    }
    _wasAnimated = widget.isAnimated;
  }

  @override
  void didUpdateWidget(_AnimatedText oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.isAnimated != widget.isAnimated && widget.isAnimated) {
      _controller.reset();
      _controller.forward();
      _wasAnimated = true;
    } else if (widget.isAnimated && !_wasAnimated) {
      _controller.reset();
      _controller.forward();
      _wasAnimated = true;
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _fadeAnimation,
      builder: (context, child) {
        return Opacity(opacity: _fadeAnimation.value, child: child);
      },
      child: Text(
        widget.text,
        style: AppTheme.getTextTheme(context).titleLarge,
      ),
    );
  }
}

class _AnimatedContent extends StatefulWidget {
  final String content;
  final bool isAnimated;

  const _AnimatedContent({required this.content, required this.isAnimated});

  @override
  State<_AnimatedContent> createState() => _AnimatedContentState();
}

class _AnimatedContentState extends State<_AnimatedContent>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _fadeAnimation;

  bool _wasAnimated = false;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 1000),
      vsync: this,
    );
    _fadeAnimation = CurvedAnimation(
      parent: _controller,
      curve: Curves.easeInOut,
    );

    if (widget.isAnimated) {
      _controller.value = 1.0;
    } else {
      _controller.value = 1.0;
    }
    _wasAnimated = widget.isAnimated;
  }

  @override
  void didUpdateWidget(_AnimatedContent oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.isAnimated != widget.isAnimated && widget.isAnimated) {
      _controller.reset();
      _controller.forward();
      _wasAnimated = true;
    } else if (widget.isAnimated && !_wasAnimated) {
      _controller.reset();
      _controller.forward();
      _wasAnimated = true;
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _fadeAnimation,
      builder: (context, child) {
        return Opacity(opacity: _fadeAnimation.value, child: child);
      },
      child: _buildFormattedContent(context, widget.content),
    );
  }

  Widget _buildFormattedContent(BuildContext context, String content) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    final sections = _parseContent(content);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (sections['summary']?.isNotEmpty ?? false) ...[
          Text(
            sections['summary']!,
            style: AppTypography.bodyLarge.copyWith(
              height: 1.8,
              color: colorScheme.onSurface,
              fontSize: AppTypography.bodyLarge.fontSize! + 1,
            ),
          ),
          if (sections['hasKeyPoints'] == true) Dimensions.verticalSpacerXl,
        ],
        if (sections['hasKeyPoints'] == true &&
            (sections['keyPoints'] as List).isNotEmpty) ...[
          Text(
            '核心观点',
            style: textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.bold,
              color: colorScheme.primary,
            ),
          ),
          Dimensions.verticalSpacerM,
          ...sections['keyPoints'].asMap().entries.map<Widget>(
            (entry) => _buildKeyPoint(context, entry),
          ),
        ],
        if ((sections['summary']?.isEmpty ?? true) &&
            (sections['hasKeyPoints'] == false ||
                (sections['keyPoints'] as List).isEmpty)) ...[
          Text(
            content,
            style: AppTypography.bodyLarge.copyWith(
              height: 1.8,
              fontSize: AppTypography.bodyLarge.fontSize! + 1,
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildKeyPoint(
    BuildContext context,
    MapEntry<int, String> indexedPoint,
  ) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    final index = indexedPoint.key;
    final point = indexedPoint.value;
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
                fontSize: AppTypography.bodyLarge.fontSize! + 1,
              ),
            ),
          ),
        ],
      ),
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
