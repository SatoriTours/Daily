import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:logger/logger.dart';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/components/empty_states/articles_empty_view.dart';
import 'package:daily_satori/app/styles/app_theme.dart';

import 'widgets/articles_search_bar.dart';
import 'widgets/articles_tags_dialog.dart';
import 'widgets/articles_list.dart';
import 'widgets/article_calendar_dialog.dart';

/// 文章列表页面
/// 负责展示文章列表、搜索、过滤等功能
class ArticlesView extends GetView<ArticlesController> {
  const ArticlesView({super.key});

  // 日志记录器
  static final _logger = Logger(printer: PrettyPrinter(methodCount: 0));

  @override
  Widget build(BuildContext context) {
    _logger.i('构建文章列表页面');
    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surface,
      appBar: _buildAppBar(context),
      body: _buildBody(context),
    );
  }

  /// 构建页面主体
  Widget _buildBody(BuildContext context) {
    return Column(
      children: [
        // 搜索栏
        _buildSearchBarSection(),
        // 过滤指示器
        _buildFilterIndicatorSection(context),
        // 文章列表
        _buildArticlesSection(),
      ],
    );
  }

  /// 构建搜索栏部分
  Widget _buildSearchBarSection() {
    return Obx(() {
      final bool shouldShowSearchBar = controller.isSearchVisible.value || controller.searchController.text.isNotEmpty;
      if (!shouldShowSearchBar) return const SizedBox.shrink();
      _logger.d('显示搜索栏');
      return const ArticlesSearchBar();
    });
  }

  /// 构建过滤指示器部分
  Widget _buildFilterIndicatorSection(BuildContext context) {
    return Obx(() {
      if (!_shouldShowFilterIndicator()) return const SizedBox.shrink();

      _logger.d('显示过滤指示器: ${controller.getTitle()}');
      return _FilterIndicator(title: controller.getTitle(), onClear: controller.clearAllFilters);
    });
  }

  /// 构建文章列表部分
  Widget _buildArticlesSection() {
    return Obx(() {
      if (controller.articles.isEmpty) {
        _logger.d('文章列表为空，显示空状态');
        return const Expanded(child: ArticlesEmptyView());
      }
      return const Expanded(child: ArticlesList());
    });
  }

  /// 判断是否应该显示过滤指示器
  bool _shouldShowFilterIndicator() {
    return controller.searchController.text.isNotEmpty ||
        controller.tagName.value.isNotEmpty ||
        controller.onlyFavorite.value ||
        controller.selectedFilterDate.value != null;
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      backgroundColor:
          Theme.of(context).brightness == Brightness.dark ? const Color(0xFF121212) : const Color(0xFF5E8BFF),
      elevation: 0.5,
      leading: _buildCalendarButton(context),
      title: _buildAppBarTitle(),
      centerTitle: true,
      actions: _buildAppBarActions(context),
    );
  }

  /// 构建日历按钮
  Widget _buildCalendarButton(BuildContext context) {
    return IconButton(
      icon: const Icon(FeatherIcons.calendar, color: Colors.white, size: 20),
      onPressed: () {
        _logger.d('打开日历对话框');
        _showCalendarDialog(context);
      },
    );
  }

  /// 构建应用栏标题
  Widget _buildAppBarTitle() {
    return GestureDetector(
      onDoubleTap: () {
        _logger.d('双击标题，滚动到顶部');
        _scrollToTop();
      },
      child: Obx(() => Text(controller.getTitle(), style: const TextStyle(fontSize: 18, color: Colors.white))),
    );
  }

  /// 构建应用栏操作按钮
  List<Widget> _buildAppBarActions(BuildContext context) {
    return [
      IconButton(
        icon: const Icon(FeatherIcons.search, color: Colors.white, size: 20),
        onPressed: () {
          _logger.d('激活搜索');
          _activateSearch();
        },
      ),
      _buildMoreMenu(context),
    ];
  }

  /// 构建更多菜单
  Widget _buildMoreMenu(BuildContext context) {
    return PopupMenuButton<String>(
      icon: const Icon(Icons.more_horiz, color: Colors.white, size: 20),
      onSelected: (value) => _handleMenuSelection(value, context),
      itemBuilder:
          (context) => [
            _buildPopupMenuItem(context, 'tags', FeatherIcons.tag, '标签筛选'),
            _buildFavoriteMenuItem(context),
          ],
    );
  }

  /// 处理菜单选择
  void _handleMenuSelection(String value, BuildContext context) {
    _logger.d('选择菜单项: $value');
    switch (value) {
      case 'tags':
        _showTagsDialog(context);
        break;
      case 'favorite':
        controller.toggleFavorite(!controller.onlyFavorite.value);
        break;
    }
  }

  /// 构建收藏菜单项
  PopupMenuItem<String> _buildFavoriteMenuItem(BuildContext context) {
    return _buildPopupMenuItem(
      context,
      'favorite',
      controller.onlyFavorite.value ? Icons.favorite : Icons.favorite_border,
      controller.onlyFavorite.value ? '显示全部文章' : '只看收藏文章',
      iconColor: controller.onlyFavorite.value ? Colors.red : null,
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

  /// 滚动到顶部
  void _scrollToTop() {
    if (controller.scrollController.hasClients) {
      controller.scrollController.animateTo(0, duration: const Duration(milliseconds: 300), curve: Curves.easeInOut);
    }
  }

  /// 激活搜索
  void _activateSearch() {
    controller.searchController.clear();
    controller.toggleSearchState();
  }

  /// 显示标签选择对话框
  void _showTagsDialog(BuildContext context) {
    _logger.d('显示标签对话框');
    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) => const ArticlesTagsDialog(),
    );
  }

  /// 显示日历选择对话框
  void _showCalendarDialog(BuildContext context) {
    if (_shouldShowFilterIndicator()) {
      _logger.d('清除现有过滤条件');
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

/// 过滤指示器组件
class _FilterIndicator extends StatelessWidget {
  final String title;
  final VoidCallback onClear;

  const _FilterIndicator({required this.title, required this.onClear});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Container(
      margin: const EdgeInsets.fromLTRB(12, 8, 12, 8),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(color: colorScheme.surfaceContainerHighest, borderRadius: BorderRadius.circular(8)),
      child: Row(
        children: [
          Expanded(child: Text('已过滤: $title', style: textTheme.labelMedium?.copyWith(color: colorScheme.onSurface))),
          InkWell(
            onTap: onClear,
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
}
