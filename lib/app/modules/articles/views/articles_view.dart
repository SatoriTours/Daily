import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:feather_icons/feather_icons.dart';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/components/empty_states/articles_empty_view.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/services/web_service/web_service.dart';

import 'widgets/articles_search_bar.dart';
import 'widgets/articles_tags_dialog.dart';
import 'widgets/articles_list.dart';
import 'widgets/article_calendar_dialog.dart';

/// 文章列表页面 - 保持状态
class ArticlesView extends GetView<ArticlesController> {
  const ArticlesView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surface,
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
                controller.onlyFavorite.value ||
                controller.selectedFilterDate.value != null)
              _buildFilterIndicator(context),
            // 文章列表
            const Expanded(child: ArticlesList()),
          ],
        ),
      );
    });
  }

  /// 构建搜索栏
  Widget _buildSearchBar() {
    return Obx(() {
      final bool shouldShowSearchBar = controller.isSearchVisible.value || controller.searchController.text.isNotEmpty;

      if (!shouldShowSearchBar) return const SizedBox.shrink();
      return const ArticlesSearchBar();
    });
  }

  /// 构建过滤指示器
  Widget _buildFilterIndicator(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Container(
      margin: const EdgeInsets.fromLTRB(12, 0, 12, 8),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(color: colorScheme.surfaceVariant, borderRadius: BorderRadius.circular(8)),
      child: Row(
        children: [
          Expanded(
            child: Text(
              '已过滤: ${controller.getTitle()}',
              style: textTheme.labelMedium?.copyWith(color: colorScheme.onSurfaceVariant),
            ),
          ),
          InkWell(
            onTap: controller.clearAllFilters,
            borderRadius: BorderRadius.circular(4),
            child: Padding(
              padding: const EdgeInsets.all(4),
              child: Text('清除', style: textTheme.labelMedium?.copyWith(color: colorScheme.primary)),
            ),
          ),
        ],
      ),
    );
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return AppBar(
      backgroundColor: isDark ? const Color(0xFF121212) : const Color(0xFF5E8BFF),
      elevation: 0.5,
      // 左侧显示日历图标
      leading: IconButton(
        icon: const Icon(FeatherIcons.calendar, color: Colors.white, size: 20),
        onPressed: () => _showCalendarDialog(context),
      ),
      // 标题居中，保留双击滚动到顶部功能
      title: GestureDetector(
        onDoubleTap: _scrollToTop,
        child: Obx(() => Text(controller.getTitle(), style: const TextStyle(fontSize: 18, color: Colors.white))),
      ),
      centerTitle: true,
      actions: [
        // 搜索按钮
        IconButton(icon: const Icon(FeatherIcons.search, color: Colors.white, size: 20), onPressed: _activateSearch),
        // 更多按钮（三点）
        PopupMenuButton<String>(
          icon: const Icon(Icons.more_horiz, color: Colors.white, size: 20),
          onSelected: (value) {
            switch (value) {
              case 'tags':
                _showTagsDialog(context);
                break;
              case 'favorite':
                controller.toggleFavorite(!controller.onlyFavorite.value);
                break;
            }
          },
          itemBuilder:
              (context) => [
                _buildPopupMenuItem(context, 'tags', FeatherIcons.tag, '标签筛选'),
                _buildPopupMenuItem(
                  context,
                  'favorite',
                  controller.onlyFavorite.value ? Icons.favorite : Icons.favorite_border,
                  controller.onlyFavorite.value ? '显示全部文章' : '只看收藏文章',
                  iconColor: controller.onlyFavorite.value ? Colors.red : null,
                ),
              ],
        ),
      ],
    );
  }

  /// 构建菜单项
  PopupMenuItem<String> _buildPopupMenuItem(
    BuildContext context,
    String value,
    IconData icon,
    String text, {
    Color? iconColor,
  }) {
    return PopupMenuItem(
      value: value,
      child: Row(children: [Icon(icon, size: 20, color: iconColor), const SizedBox(width: 8), Text(text)]),
    );
  }

  /// 构建WebSocket连接状态指示器
  Widget _buildConnectionIndicator() {
    return Obx(() {
      final isConnected = WebService.i.webSocketTunnel.isConnected.value;
      return Center(
        child: isConnected ? const Icon(Icons.circle, color: Colors.green, size: 14) : const SizedBox.shrink(),
      );
    });
  }

  /// 激活搜索
  void _activateSearch() {
    // 清除当前搜索内容
    controller.searchController.clear();

    // 激活搜索栏
    controller.toggleSearchState();
  }

  /// 显示标签选择对话框
  void _showTagsDialog(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) => const ArticlesTagsDialog(),
    );
  }

  /// 显示日历选择对话框
  void _showCalendarDialog(BuildContext context) {
    // 如果已经有其他筛选，先清除
    if (controller.searchController.text.isNotEmpty ||
        controller.tagName.value.isNotEmpty ||
        controller.onlyFavorite.value) {
      controller.clearAllFilters();
    }

    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      isScrollControlled: true,
      builder: (context) => const ArticleCalendarDialog(),
    );
  }
}
