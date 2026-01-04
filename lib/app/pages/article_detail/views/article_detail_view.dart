import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/pages/article_detail/providers/article_detail_controller_provider.dart';
import 'package:daily_satori/app/data/data.dart' show ArticleModel;

import 'widgets/article_detail_app_bar.dart';
import 'widgets/original_content_tab.dart';
import 'widgets/summary_tab.dart';
import 'widgets/tab_bar_widget.dart';

class ArticleDetailView extends ConsumerWidget {
  const ArticleDetailView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final arguments = AppNavigation.arguments(context);
    final articleId = arguments is int ? arguments : (arguments as ArticleModel?)?.id;

    if (articleId == null) {
      return const Scaffold(body: Center(child: Text('文章不存在')));
    }

    final controllerState = ref.watch(articleDetailControllerProvider(articleId));
    final article = controllerState.articleModel;

    return DefaultTabController(length: 2, child: _buildScaffold(context, articleId, article));
  }

  Widget _buildScaffold(BuildContext context, int articleId, ArticleModel? article) {
    return Scaffold(
      appBar: ArticleDetailAppBar(articleId: articleId, article: article),
      body: Column(children: [_buildContent(articleId, article), const ArticleTabBar()]),
    );
  }

  Widget _buildContent(int articleId, ArticleModel? article) {
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
