import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:logger/logger.dart';

import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';

import 'article_card.dart';

/// 文章列表组件
/// 负责展示文章列表和加载状态
class ArticlesList extends GetView<ArticlesController> {
  const ArticlesList({super.key});

  // 日志记录器
  static final _logger = Logger(printer: PrettyPrinter(methodCount: 0));

  @override
  Widget build(BuildContext context) {
    _logger.d('构建文章列表组件');
    return RefreshIndicator(
      onRefresh: () async {
        _logger.i('下拉刷新文章列表');
        await controller.reloadArticles();
      },
      color: AppTheme.getColorScheme(context).primary,
      child: _buildListView(),
    );
  }

  /// 构建列表视图
  Widget _buildListView() {
    return Obx(() {
      final itemCount = _calculateItemCount();
      _logger.d('文章列表项数量: $itemCount');

      return ListView.builder(
        controller: controller.scrollController,
        itemCount: itemCount,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        // 使用 ListView.builder 的 separatorBuilder 会导致额外的 build 调用
        // 所以我们在 itemBuilder 中处理间距
        itemBuilder: (context, index) {
          if (index == controller.articles.length) {
            return _buildLoadingIndicator(context);
          }

          return Padding(
            padding: EdgeInsets.only(bottom: index < controller.articles.length - 1 ? 8.0 : 0),
            child: ArticleCard(key: ValueKey(controller.articles[index].id), articleModel: controller.articles[index]),
          );
        },
      );
    });
  }

  /// 计算列表项总数
  int _calculateItemCount() {
    return controller.articles.length + (controller.isLoading.value ? 1 : 0);
  }

  /// 构建加载指示器
  Widget _buildLoadingIndicator(BuildContext context) {
    _logger.d('显示加载指示器');
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Container(
      padding: const EdgeInsets.symmetric(vertical: 20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: 24,
            height: 24,
            child: CircularProgressIndicator(
              strokeWidth: 2.5,
              valueColor: AlwaysStoppedAnimation<Color>(colorScheme.primary),
            ),
          ),
          const SizedBox(height: 12),
          Text('加载更多内容...', style: textTheme.labelMedium?.copyWith(color: colorScheme.onSurfaceVariant)),
        ],
      ),
    );
  }
}
