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
/// - 内容摘要
/// - 评论（如果有）
class SummaryTab extends StatelessWidget {
  final ArticleDetailController controller;

  const SummaryTab({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 封面图片
          if (_hasHeaderImage) _buildHeaderImage(),

          // 标题区域
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Text(controller.articleModel.showTitle(), style: AppTheme.getTextTheme(context).headlineSmall),
          ),

          // 标签区域
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Obx(() => ArticleTags(tags: controller.tags.value)),
          ),

          // 内容区域
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            child: Text(controller.articleModel.showContent(), style: AppTheme.getTextTheme(context).bodyMedium),
          ),

          // 评论区域（如果有）
          if (_hasComment) _buildCommentSection(context),
        ],
      ),
    );
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
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('评论', style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          Text(controller.articleModel.comment ?? '', style: textTheme.bodyMedium),
        ],
      ),
    );
  }
}
