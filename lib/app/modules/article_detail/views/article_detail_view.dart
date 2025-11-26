import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/data/index.dart' show ArticleStatus;

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
    final cs = Theme.of(context).colorScheme;
    final tt = Theme.of(context).textTheme;

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: cs.primaryContainer.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: cs.primary.withValues(alpha: 0.2), width: 0.5),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: 14,
            height: 14,
            child: CircularProgressIndicator(strokeWidth: 2, color: cs.primary.withValues(alpha: 0.7)),
          ),
          const SizedBox(width: 10),
          Text('AI整理中', style: tt.bodySmall?.copyWith(color: cs.onSurface.withValues(alpha: 0.75), fontSize: 13)),
        ],
      ),
    );
  }
}
