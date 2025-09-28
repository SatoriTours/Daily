import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/repositories/article_repository.dart' show ArticleStatus;

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
    body: _buildBody(context),
  );

  // 页面主体：横幅 + 内容 + TabBar
  Widget _buildBody(BuildContext context) =>
      Column(children: [_buildProcessingBanner(context), _buildTabs(), const ArticleTabBar()]);

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
    return busy ? _buildBannerCard(context) : const SizedBox.shrink();
  });

  // 横幅卡片容器
  Widget _buildBannerCard(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
    child: Card(
      color: Theme.of(context).colorScheme.primaryContainer.withValues(alpha: 0.18),
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        child: _buildBannerRow(context),
      ),
    ),
  );

  // 横幅主体行
  Widget _buildBannerRow(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        _buildBannerLeadStripe(context),
        _gapW(12),
        Icon(Icons.hourglass_bottom, size: 22, color: cs.primary.withValues(alpha: 0.95)),
        _gapW(10),
        Expanded(child: _buildBannerTexts(context)),
      ],
    );
  }

  // 左侧强调条
  Widget _buildBannerLeadStripe(BuildContext context) => Container(
    width: 6,
    height: 48,
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.55),
      borderRadius: BorderRadius.circular(3),
    ),
  );

  // 文本与进度条
  Widget _buildBannerTexts(BuildContext context) {
    final tt = Theme.of(context).textTheme;
    final cs = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'AI处理中…',
          style: tt.bodyMedium?.copyWith(color: cs.onSurface, fontWeight: FontWeight.w600),
        ),
        _gapH(4),
        Text('正在整理标题、摘要与Markdown，完成后将自动更新本页', style: tt.bodySmall?.copyWith(color: cs.onSurfaceVariant)),
        _gapH(8),
        _buildLoadingBar(context),
      ],
    );
  }

  // 线性进度条
  Widget _buildLoadingBar(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(4),
    child: LinearProgressIndicator(
      minHeight: 4,
      backgroundColor: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.22),
      color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.85),
    ),
  );

  // 水平/垂直间距
  Widget _gapW(double w) => SizedBox(width: w);
  Widget _gapH(double h) => SizedBox(height: h);
}
