import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/pages/article_detail/providers/article_detail_controller_provider.dart';
import 'package:daily_satori/app/data/index.dart' show ArticleStatus, ArticleModel;
import 'package:daily_satori/app/styles/index.dart';

import 'widgets/article_detail_app_bar.dart';
import 'widgets/original_content_tab.dart';
import 'widgets/summary_tab.dart';
import 'widgets/tab_bar_widget.dart';

/// 文章详情页面
/// 包含两个主要标签页：
/// 1. 摘要页面：显示文章的基本信息和AI生成的摘要
/// 2. 原文页面：显示文章的完整内容
class ArticleDetailView extends ConsumerWidget {
  const ArticleDetailView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final arguments = AppNavigation.arguments(context);
    final articleId = arguments is int ? arguments : (arguments as ArticleModel?)?.id;

    if (articleId == null) {
      return const Scaffold(body: Center(child: Text('文章不存在')));
    }

    // 直接 watch 带参数的 Provider，Riverpod 会自动处理缓存
    final controllerState = ref.watch(articleDetailControllerProvider(articleId));
    final article = controllerState.articleModel;

    return DefaultTabController(length: 2, child: _buildScaffold(context, ref, articleId, article));
  }

  // 页面骨架：AppBar + Body
  Widget _buildScaffold(BuildContext context, WidgetRef ref, int articleId, ArticleModel? article) {
    return Scaffold(
      appBar: ArticleDetailAppBar(articleId: articleId, article: article),
      body: Column(
        children: [
          _buildProcessingBanner(context, ref, article),
          _buildTabs(articleId, article),
          const ArticleTabBar(),
        ],
      ),
    );
  }

  // 内容区域：监听文章变化刷新标签页
  Widget _buildTabs(int articleId, ArticleModel? article) {
    return Expanded(
      child: TabBarView(
        physics: const NeverScrollableScrollPhysics(),
        children: [
          SummaryTab(articleId: articleId, article: article),
          OriginalContentTab(articleId: articleId, article: article),
        ],
      ),
    );
  }

  // 处理中横幅：仅在 AI 处理中显示
  Widget _buildProcessingBanner(BuildContext context, WidgetRef ref, ArticleModel? article) {
    final st = article?.status ?? ArticleStatus.pending;
    final busy = st == ArticleStatus.pending || st == ArticleStatus.webContentFetched;
    return busy ? _buildProcessingIndicator(context) : const SizedBox.shrink();
  }

  // 简洁处理指示器：更安静、不打扰的设计
  Widget _buildProcessingIndicator(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final primaryColor = colorScheme.primary;
    final surfaceContainer = colorScheme.surfaceContainerHighest;
    final onSurfaceVariant = colorScheme.onSurfaceVariant;

    return Container(
      margin: const EdgeInsets.fromLTRB(
        Dimensions.spacingM,
        Dimensions.spacingM,
        Dimensions.spacingM,
        Dimensions.spacingS,
      ),
      padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
      decoration: BoxDecoration(
        color: surfaceContainer,
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        border: Border(left: BorderSide(color: primaryColor, width: 3)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          // 静态图标
          Icon(Icons.auto_awesome_outlined, size: Dimensions.iconSizeS, color: primaryColor),
          const SizedBox(width: Dimensions.spacingS),
          Text('AI 整理中...', style: AppTypography.bodySmall.copyWith(color: onSurfaceVariant)),
          const SizedBox(width: Dimensions.spacingXs),
          // 三个点动画
          _BouncingDots(color: onSurfaceVariant),
        ],
      ),
    );
  }
}

/// 跳动点动画组件
class _BouncingDots extends StatefulWidget {
  final Color color;

  const _BouncingDots({required this.color});

  @override
  State<_BouncingDots> createState() => _BouncingDotsState();
}

class _BouncingDotsState extends State<_BouncingDots> with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(duration: const Duration(milliseconds: 1200), vsync: this)..repeat();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: List.generate(3, (index) {
        final delay = index * 0.15;
        final animation = Tween<double>(begin: 0.0, end: 1.0).animate(
          CurvedAnimation(
            parent: _controller,
            curve: Interval(delay, delay + 0.5, curve: Curves.easeInOutSine),
          ),
        );

        return AnimatedBuilder(
          animation: _controller,
          builder: (context, child) {
            return Transform.translate(
              offset: Offset(0, -4 * animation.value),
              child: Opacity(
                opacity: 0.4 + 0.6 * animation.value,
                child: Text('·', style: TextStyle(fontSize: 16, height: 1, color: widget.color)),
              ),
            );
          },
        );
      }),
    );
  }
}
