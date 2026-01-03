import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/pages/article_detail/providers/article_detail_controller_provider.dart';
import 'package:daily_satori/app/data/index.dart' show ArticleModel;

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

    return DefaultTabController(length: 2, child: _buildScaffold(context, articleId, article));
  }

  // 页面骨架：AppBar + Body
  Widget _buildScaffold(BuildContext context, int articleId, ArticleModel? article) {
    return Scaffold(
      appBar: ArticleDetailAppBar(articleId: articleId, article: article),
      body: Column(
        children: [
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
}
