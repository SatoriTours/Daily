import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/pages/article_detail/views/widgets/article_image_view.dart';
import 'package:daily_satori/app/pages/article_detail/views/widgets/article_tags.dart';
import 'package:daily_satori/app/pages/article_detail/providers/article_detail_controller_provider.dart';
import 'package:daily_satori/app/styles/index.dart';

/// 文章摘要标签页
/// 显示文章的基本信息，包括：
/// - 封面图片（如果有）
/// - 标题
/// - 标签
/// - 内容摘要（带格式化的核心观点）
/// - 评论（如果有）
class SummaryTab extends ConsumerWidget {
  final ArticleModel? article;

  const SummaryTab({super.key, required this.article});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (article == null) {
      return const Center(child: Text('文章不存在'));
    }

    final controllerState = ref.watch(articleDetailControllerProvider);
    final tags = controllerState.tags;

    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(vertical: Dimensions.spacingL),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 封面图片
          if (_hasHeaderImage) _buildHeaderImage(),

          // 标题区域
          Padding(
            padding: const EdgeInsets.fromLTRB(
              Dimensions.spacingL,
              Dimensions.spacingL,
              Dimensions.spacingL,
              Dimensions.spacingM,
            ),
            child: Text(article!.showTitle(), style: AppTheme.getTextTheme(context).titleLarge),
          ),

          // 标签区域
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingL, vertical: Dimensions.spacingM),
            child: ArticleTags(tags: tags),
          ),

          // 内容区域（优化格式化显示）
          Padding(
            padding: const EdgeInsets.fromLTRB(
              Dimensions.spacingL,
              Dimensions.spacingM,
              Dimensions.spacingL,
              Dimensions.spacingL,
            ),
            child: _buildFormattedContent(context),
          ),

          // 评论区域（如果有）
          if (_hasComment) _buildCommentSection(context),
        ],
      ),
    );
  }

  /// 构建格式化的内容（增强核心观点的视觉层次）
  Widget _buildFormattedContent(BuildContext context) {
    final content = article!.showContent();
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    // 解析内容，分离标题、摘要和核心观点
    final sections = _parseContent(content);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 如果有独立的摘要部分，先显示
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

        // 核心观点部分
        if (sections['hasKeyPoints'] == true && (sections['keyPoints'] as List).isNotEmpty) ...[
          Text(
            '核心观点',
            style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold, color: colorScheme.primary),
          ),
          Dimensions.verticalSpacerM,
          // 使用带索引的方式构建核心观点列表
          ...sections['keyPoints'].asMap().entries.map<Widget>((entry) => _buildKeyPoint(context, entry)),
        ],

        // 如果既没有摘要也没有核心观点，显示原始内容
        if ((sections['summary']?.isEmpty ?? true) &&
            (sections['hasKeyPoints'] == false || (sections['keyPoints'] as List).isEmpty)) ...[
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

  /// 构建单个核心观点（使用数字编号）
  Widget _buildKeyPoint(BuildContext context, MapEntry<int, String> indexedPoint) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    final index = indexedPoint.key;
    final point = indexedPoint.value;

    // 移除编号前缀（如 "1. "），因为我们会自己添加
    final cleanPoint = point.replaceFirst(RegExp(r'^\d+\.\s*'), '');

    return Padding(
      padding: const EdgeInsets.only(bottom: Dimensions.spacingL),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 数字序号（圆形背景）
          Container(
            margin: const EdgeInsets.only(top: Dimensions.spacingXs / 2, right: Dimensions.spacingM),
            width: Dimensions.iconSizeL,
            height: Dimensions.iconSizeL,
            decoration: BoxDecoration(color: colorScheme.primary, shape: BoxShape.circle),
            child: Center(
              child: Text(
                '${index + 1}',
                style: textTheme.labelLarge?.copyWith(color: colorScheme.onPrimary, fontWeight: FontWeight.bold),
              ),
            ),
          ),
          // 观点内容
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

  /// 解析内容，分离出不同部分
  Map<String, dynamic> _parseContent(String content) {
    final result = <String, dynamic>{'summary': '', 'keyPoints': <String>[], 'hasKeyPoints': false};

    // 先检查是否包含"核心观点"标题（在清理前检查）
    if (content.contains('## 核心观点') || content.contains('核心观点')) {
      result['hasKeyPoints'] = true;

      // 分割内容
      final parts = content.split(RegExp(r'##?\s*核心观点'));
      if (parts.length > 1) {
        // 清理摘要部分的Markdown标记
        result['summary'] = _cleanMarkdownHeaders(parts[0]).trim();

        // 提取核心观点列表
        final keyPointsSection = parts[1].trim();
        final lines = keyPointsSection.split('\n');

        for (final line in lines) {
          final trimmed = line.trim();
          // 匹配编号列表项（如 "1. xxx"）
          if (RegExp(r'^\d+\.\s+.+').hasMatch(trimmed)) {
            result['keyPoints'].add(trimmed);
          }
        }
      }
    } else {
      // 如果没有核心观点结构，清理整个内容的Markdown标记
      result['summary'] = _cleanMarkdownHeaders(content);
    }

    return result;
  }

  /// 清理Markdown标题标记
  String _cleanMarkdownHeaders(String content) {
    // 移除一级标题 #
    var cleaned = content.replaceAll(RegExp(r'^#\s+', multiLine: true), '');
    // 移除二级标题 ##
    cleaned = cleaned.replaceAll(RegExp(r'^##\s+', multiLine: true), '');
    // 移除三级标题 ###
    cleaned = cleaned.replaceAll(RegExp(r'^###\s+', multiLine: true), '');
    // 移除多余的空行
    cleaned = cleaned.replaceAll(RegExp(r'\n{3,}'), '\n\n');
    return cleaned.trim();
  }

  /// 判断是否有头图
  bool get _hasHeaderImage =>
      article!.shouldShowHeaderImage() || (article!.coverImageUrl?.isNotEmpty ?? false);

  /// 判断是否有评论
  bool get _hasComment => article!.comment?.isNotEmpty ?? false;

  /// 构建头图组件
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

  /// 构建评论区域
  Widget _buildCommentSection(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return Padding(
      padding: Dimensions.paddingPage,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('评论', style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
          Dimensions.verticalSpacerM,
          Text(article!.comment ?? '', style: textTheme.bodyMedium),
        ],
      ),
    );
  }
}
