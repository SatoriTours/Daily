import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/modules/article_detail/controllers/article_detail_controller.dart';
import 'package:daily_satori/app/modules/article_detail/views/widgets/article_image_view.dart';
import 'package:daily_satori/app/modules/article_detail/views/widgets/article_tags.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/index.dart';

class SummaryTab extends StatelessWidget {
  final ArticleDetailController controller;

  const SummaryTab({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: Dimensions.paddingVerticalM.copyWith(bottom: Dimensions.spacingM),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (controller.articleModel.shouldShowHeaderImage())
            ArticleImageView(imagePath: controller.articleModel.getHeaderImagePath(), controller: controller),
          if (controller.articleModel.shouldShowHeaderImage()) const SizedBox(height: 10),
          _buildTitle(context),
          const SizedBox(height: 15),
          Obx(() => ArticleTags(tags: controller.tags.value)),
          const SizedBox(height: 15),
          _buildContent(context),
          if (controller.articleModel.comment?.isNotEmpty ?? false) const SizedBox(height: 24),
          if (controller.articleModel.comment?.isNotEmpty ?? false) _buildComment(context),
        ],
      ),
    );
  }

  Widget _buildTitle(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Text(
        (controller.articleModel.aiTitle ?? controller.articleModel.title) ?? '',
        style: textTheme.headlineSmall,
      ),
    );
  }

  Widget _buildContent(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Text(controller.articleModel.aiContent ?? '', style: textTheme.bodyMedium),
    );
  }

  Widget _buildComment(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
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
