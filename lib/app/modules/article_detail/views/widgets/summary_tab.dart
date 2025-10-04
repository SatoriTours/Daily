import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/modules/article_detail/controllers/article_detail_controller.dart';
import 'package:daily_satori/app/modules/article_detail/views/widgets/article_image_view.dart';
import 'package:daily_satori/app/modules/article_detail/views/widgets/article_tags.dart';
import 'package:daily_satori/app/styles/app_theme.dart';

/// 文章摘要标签页
/// 显示文章的基本信息，包括：
/// - 封面图片（如果有）
/// - 标题
/// - 标签
/// - 内容摘要（带格式化的核心观点）
/// - 评论（如果有）
class SummaryTab extends StatelessWidget {
  final ArticleDetailController controller;

  const SummaryTab({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(vertical: 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 封面图片
          if (_hasHeaderImage) _buildHeaderImage(),

          // 标题区域
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 24, 24, 16),
            child: Text(controller.articleModel.showTitle(), style: AppTheme.getTextTheme(context).headlineSmall),
          ),

          // 标签区域
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
            child: Obx(() => ArticleTags(tags: controller.tags.value)),
          ),

          // 内容区域（优化格式化显示）
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
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
    final content = controller.articleModel.showContent();
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
            style: textTheme.bodyLarge?.copyWith(
              height: 1.8,
              color: colorScheme.onSurface,
            ),
          ),
          const SizedBox(height: 32),
        ],

        // 核心观点部分
        if (sections['hasKeyPoints'] == true) ...[
          Text(
            '核心观点',
            style: textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.bold,
              color: colorScheme.primary,
            ),
          ),
          const SizedBox(height: 20),
          ...sections['keyPoints'].map<Widget>((point) => _buildKeyPoint(context, point)),
        ] else ...[
          // 如果没有核心观点结构，直接显示内容
          Text(
            content,
            style: textTheme.bodyMedium?.copyWith(height: 1.8),
          ),
        ],
      ],
    );
  }

  /// 构建单个核心观点
  Widget _buildKeyPoint(BuildContext context, String point) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    // 移除编号前缀（如 "1. "）
    final cleanPoint = point.replaceFirst(RegExp(r'^\d+\.\s*'), '');

    return Padding(
      padding: const EdgeInsets.only(bottom: 24),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 装饰性圆点
          Container(
            margin: const EdgeInsets.only(top: 8, right: 16),
            width: 8,
            height: 8,
            decoration: BoxDecoration(
              color: colorScheme.primary,
              shape: BoxShape.circle,
            ),
          ),
          // 观点内容
          Expanded(
            child: Text(
              cleanPoint,
              style: textTheme.bodyLarge?.copyWith(
                height: 1.8,
                color: colorScheme.onSurface,
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// 解析内容，分离出不同部分
  Map<String, dynamic> _parseContent(String content) {
    final result = <String, dynamic>{
      'summary': '',
      'keyPoints': <String>[],
      'hasKeyPoints': false,
    };

    // 检查是否包含"核心观点"标题
    if (content.contains('## 核心观点') || content.contains('核心观点')) {
      result['hasKeyPoints'] = true;

      // 分割内容
      final parts = content.split(RegExp(r'##?\s*核心观点'));
      if (parts.length > 1) {
        result['summary'] = parts[0].trim();

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
    }

    return result;
  }

  /// 判断是否有头图
  bool get _hasHeaderImage =>
      controller.articleModel.shouldShowHeaderImage() || (controller.articleModel.coverImageUrl?.isNotEmpty ?? false);

  /// 判断是否有评论
  bool get _hasComment => controller.articleModel.comment?.isNotEmpty ?? false;

  /// 构建头图组件
  Widget _buildHeaderImage() {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: ArticleImageView(
        imagePath: controller.articleModel.getHeaderImagePath(),
        controller: controller,
        networkUrl: controller.articleModel.coverImageUrl,
      ),
    );
  }

  /// 构建评论区域
  Widget _buildCommentSection(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('评论', style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          Text(controller.articleModel.comment ?? '', style: textTheme.bodyMedium),
        ],
      ),
    );
  }
}
