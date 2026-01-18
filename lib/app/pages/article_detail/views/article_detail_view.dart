/// 文章详情页面
///
/// 简洁的状态管理：
/// - 页面状态通过 articleDetailControllerProvider(articleId) 获取
/// - 子组件通过 articleId 参数自行获取状态
library;

import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/pages/article_detail/providers/article_detail_controller_provider.dart';

import 'widgets/article_detail_app_bar.dart';
import 'widgets/summary_tab.dart';
import 'widgets/original_content_tab.dart';
import 'widgets/tab_bar_widget.dart';

class ArticleDetailView extends ConsumerWidget {
  final int articleId;

  const ArticleDetailView({super.key, required this.articleId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controllerState = ref.watch(
      articleDetailControllerProvider(articleId),
    );
    final article = controllerState.articleModel;

    if (article == null) {
      return const Scaffold(body: Center(child: Text('文章不存在')));
    }

    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: ArticleDetailAppBar(articleId: articleId, article: article),
        body: Column(
          children: [
            Expanded(
              child: TabBarView(
                physics: const NeverScrollableScrollPhysics(),
                children: [
                  SummaryTab(articleId: articleId),
                  OriginalContentTab(articleId: articleId),
                ],
              ),
            ),
            const ArticleTabBar(),
          ],
        ),
      ),
    );
  }
}
