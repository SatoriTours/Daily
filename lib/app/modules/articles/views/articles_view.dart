import 'package:daily_satori/app/services/logger_service.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:feather_icons/feather_icons.dart';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/components/empty_states/articles_empty_view.dart';

import 'widgets/articles_search_bar.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import 'widgets/articles_tags_dialog.dart';
import 'widgets/articles_list.dart';
import 'widgets/article_calendar_dialog.dart';
import 'package:daily_satori/app/styles/font_style.dart';
import 'package:daily_satori/app/components/indicators/s_filter_indicator.dart';
import 'package:daily_satori/app/components/menus/s_popup_menu_item.dart';

/// 文章列表页面
/// 负责展示文章列表、搜索、过滤等功能
class ArticlesView extends GetView<ArticlesController> {
  const ArticlesView({super.key});

  @override
  Widget build(BuildContext context) {
    logger.i('构建文章列表页面');
    FocusScope.of(context).unfocus();
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
      logger.d('显示搜索栏');
      return const ArticlesSearchBar();
    });
  }

  /// 构建过滤指示器部分
  Widget _buildFilterIndicatorSection(BuildContext context) {
    return Obx(() {
      if (!controller.hasActiveFilters()) return const SizedBox.shrink();

      logger.d('显示过滤指示器: ${controller.getTitle()}');
      return SFilterIndicator(title: controller.getTitle(), onClear: controller.clearAllFilters);
    });
  }

  /// 构建文章列表部分
  Widget _buildArticlesSection() {
    return Obx(() {
      if (controller.articles.isEmpty) {
        logger.d('文章列表为空，显示空状态');
        return const Expanded(child: ArticlesEmptyView());
      }
      return const Expanded(child: ArticlesList());
    });
  }

  // 已由 controller.hasActiveFilters 提供判断

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return SAppBar(
      backgroundColorDark: const Color(0xFF121212),
      backgroundColorLight: const Color(0xFF5E8BFF),
      elevation: 0.5,
      leading: _buildCalendarButton(context),
      title: _buildAppBarTitle(),
      centerTitle: true,
      actions: _buildAppBarActions(context),
      foregroundColor: Colors.white,
    );
  }

  /// 构建日历按钮
  Widget _buildCalendarButton(BuildContext context) {
    return IconButton(
      icon: const Icon(FeatherIcons.calendar, color: Colors.white, size: 20),
      onPressed: () {
        logger.d('打开日历对话框');
        _showCalendarDialog(context);
      },
    );
  }

  /// 构建应用栏标题
  Widget _buildAppBarTitle() {
    return GestureDetector(
      onDoubleTap: () {
        logger.d('双击标题，滚动到顶部');
        _scrollToTop();
      },
      child: Obx(() => Text(controller.getTitle(), style: MyFontStyle.appBarTitleStyle)),
    );
  }

  /// 构建应用栏操作按钮
  List<Widget> _buildAppBarActions(BuildContext context) {
    return [
      IconButton(
        icon: const Icon(FeatherIcons.search, color: Colors.white, size: 20),
        onPressed: () {
          logger.d('激活搜索');
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
      itemBuilder: (context) => [
        SPopupMenuItem<String>(value: 'tags', icon: FeatherIcons.tag, text: '标签筛选'),
        _buildFavoriteMenuItem(context),
      ],
    );
  }

  /// 处理菜单选择
  void _handleMenuSelection(String value, BuildContext context) {
    logger.d('选择菜单项: $value');
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
    return SPopupMenuItem<String>(
      value: 'favorite',
      icon: controller.onlyFavorite.value ? Icons.favorite : Icons.favorite_border,
      text: controller.onlyFavorite.value ? '显示全部文章' : '只看收藏文章',
      iconColor: controller.onlyFavorite.value ? Colors.red : null,
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
    logger.d('显示标签对话框');
    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) => const ArticlesTagsDialog(),
    );
  }

  /// 显示日历选择对话框
  void _showCalendarDialog(BuildContext context) {
    if (controller.hasActiveFilters()) {
      logger.d('清除现有过滤条件');
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

// 本地的 _FilterIndicator 已抽取为通用组件 SFilterIndicator
