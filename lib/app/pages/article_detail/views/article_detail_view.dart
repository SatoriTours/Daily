import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/data/index.dart' show ArticleStatus;
import 'package:daily_satori/app/styles/index.dart';

import '../controllers/article_detail_controller.dart';
import 'widgets/article_detail_app_bar.dart';
import 'widgets/original_content_tab.dart';
import 'widgets/summary_tab.dart';
import 'widgets/tab_bar_widget.dart';

/// 文章详情页面
/// 包含两个主要标签页：
/// 1. 摘要页面：显示文章的基本信息和AI生成的摘要
/// 2. 原文页面：显示文章的完整内容
class ArticleDetailView extends GetView<ArticleDetailController> {
  const ArticleDetailView({super.key});

  // 根构建：提供 Tab 数量与 Scaffold
  @override
  Widget build(BuildContext context) => DefaultTabController(length: 2, child: _buildScaffold(context));

  // 页面骨架：AppBar + Body
  Widget _buildScaffold(BuildContext context) => Scaffold(
    appBar: ArticleDetailAppBar(controller: controller),
    body: Column(children: [_buildProcessingBanner(context), _buildTabs(), const ArticleTabBar()]),
  );

  // 内容区域：监听文章变化刷新标签页
  Widget _buildTabs() => Expanded(
    child: Obx(() {
      controller.article.value; // 触发重建
      return TabBarView(
        physics: const NeverScrollableScrollPhysics(),
        children: [
          SummaryTab(controller: controller),
          OriginalContentTab(controller: controller),
        ],
      );
    }),
  );

  // 处理中横幅：仅在 AI 处理中显示
  Widget _buildProcessingBanner(BuildContext context) => Obx(() {
    final st = controller.article.value?.status ?? controller.articleModel.status;
    final busy = st == ArticleStatus.pending || st == ArticleStatus.webContentFetched;
    return busy ? _buildSlimBanner(context) : const SizedBox.shrink();
  });

  // 简洁横幅：仅显示一条带图标和文字的细条
  Widget _buildSlimBanner(BuildContext context) {
    return Container(
      margin: EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
      padding: EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
      decoration: BoxDecoration(
        color: AppColors.getPrimaryContainer(context).withValues(alpha: Opacities.extraLow),
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        border: Border.all(color: AppColors.getPrimary(context).withValues(alpha: Opacities.low), width: 0.5),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: Dimensions.iconSizeXs,
            height: Dimensions.iconSizeXs,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              color: AppColors.getPrimary(context).withValues(alpha: Opacities.high),
            ),
          ),
          Dimensions.horizontalSpacerS,
          Text('AI整理中', style: AppTypography.bodySmall.copyWith(color: AppColors.getPrimary(context))),
        ],
      ),
    );
  }
}
