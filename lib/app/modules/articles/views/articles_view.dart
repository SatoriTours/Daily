import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:feather_icons/feather_icons.dart';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/components/lists/articles_list.dart';
import 'package:daily_satori/app/components/empty_states/articles_empty_view.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/services/web_service/web_service.dart';

import 'widgets/articles_search_bar.dart';
import 'widgets/articles_tags_dialog.dart';

/// 文章列表页面 - 保持状态
class ArticlesView extends GetView<ArticlesController> {
  const ArticlesView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.background,
      appBar: _buildAppBar(context),
      body: Stack(children: [_buildMainContent(context), _buildSearchBar()]),
    );
  }

  /// 滚动到顶部
  void _scrollToTop() {
    if (controller.scrollController.hasClients) {
      controller.scrollController.animateTo(0, duration: const Duration(milliseconds: 300), curve: Curves.easeInOut);
    }
  }

  /// 构建主要内容区域
  Widget _buildMainContent(BuildContext context) {
    return Obx(() {
      // 显示空状态
      if (controller.articles.isEmpty) {
        return const Center(child: ArticlesEmptyView());
      }

      // 显示搜索结果或常规列表
      return Padding(
        padding: const EdgeInsets.only(top: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 如果有过滤条件，显示过滤指示器
            if (controller.searchController.text.isNotEmpty ||
                controller.tagName.value.isNotEmpty ||
                controller.onlyFavorite.value)
              _buildFilterIndicator(context),

            // 文章列表
            Expanded(
              child: ArticlesList(
                articles: controller.articles,
                scrollController: controller.scrollController,
                onRefresh: controller.reloadArticles,
                isLoading: controller.isLoading.value,
                onArticleUpdated: () {
                  if (controller.articles.isNotEmpty) {
                    controller.updateArticle(controller.articles.last.id);
                  }
                },
              ),
            ),
          ],
        ),
      );
    });
  }

  /// 构建过滤指示器
  Widget _buildFilterIndicator(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    String filterText = '';

    if (controller.searchController.text.isNotEmpty) {
      filterText = '搜索结果: "${controller.searchController.text}"';
    } else if (controller.tagName.value.isNotEmpty) {
      filterText = '标签: "${controller.tagName.value}"';
    } else if (controller.onlyFavorite.value) {
      filterText = '收藏的文章';
    }

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
      child: Row(
        children: [
          Expanded(
            child: Text(
              filterText,
              style: TextStyle(fontSize: 14, color: colorScheme.onSurfaceVariant),
              overflow: TextOverflow.ellipsis,
            ),
          ),
          _buildClearFilterButton(context),
        ],
      ),
    );
  }

  /// 构建清除过滤按钮
  Widget _buildClearFilterButton(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    return InkWell(
      onTap: () => controller.clearAllFilters(),
      borderRadius: BorderRadius.circular(20),
      child: Padding(
        padding: const EdgeInsets.all(4),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(FeatherIcons.x, size: 14, color: colorScheme.primary),
            const SizedBox(width: 4),
            Text('清除', style: TextStyle(fontSize: 12, color: colorScheme.primary)),
          ],
        ),
      ),
    );
  }

  /// 构建搜索栏
  Widget _buildSearchBar() {
    return Obx(() {
      final shouldShowSearchBar = controller.enableSearch.value || controller.searchController.text.isNotEmpty;

      return Visibility(
        visible: shouldShowSearchBar,
        child: Positioned(
          top: 0,
          left: 0,
          right: 0,
          child: ArticlesSearchBar(controller: controller, onClose: () => controller.toggleSearchState()),
        ),
      );
    });
  }

  /// 构建WebSocket连接状态指示器
  Widget _buildConnectionIndicator() {
    return Obx(() {
      final isConnected = WebService.i.webSocketTunnel.isConnected.value;
      return isConnected ? const Icon(Icons.circle, color: Colors.green, size: 20) : const SizedBox.shrink();
    });
  }

  /// 构建AppBar
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    final isDark = AppTheme.isDarkMode(context);

    return AppBar(
      backgroundColor: isDark ? const Color(0xFF121212) : const Color(0xFF5E8BFF),
      elevation: 0.5,
      title: GestureDetector(
        onDoubleTap: _scrollToTop,
        child: Obx(() => Text(controller.getTitle(), style: const TextStyle(fontSize: 18, color: Colors.white))),
      ),
      centerTitle: true,
      leading: Center(child: _buildConnectionIndicator()),
      actions: [
        IconButton(
          icon: const Icon(FeatherIcons.search, color: Colors.white, size: 20),
          onPressed: () => controller.toggleSearchState(),
        ),
        IconButton(
          icon: const Icon(FeatherIcons.tag, color: Colors.white, size: 20),
          onPressed: () => _showTagsDialog(context),
        ),
      ],
    );
  }

  /// 显示标签选择对话框
  void _showTagsDialog(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) => ArticlesTagsDialog(controller: controller),
    );
  }
}
