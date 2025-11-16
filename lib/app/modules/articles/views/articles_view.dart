import 'package:daily_satori/app/services/logger_service.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:share_plus/share_plus.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/components/empty_states/articles_empty_view.dart';
import 'package:daily_satori/app/services/state/app_state_service.dart';
import 'package:daily_satori/app/repositories/repositories.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/extensions/i18n_extension.dart';
import 'package:daily_satori/app/config/app_config.dart';
import 'package:daily_satori/app/components/search/generic_search_bar.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'widgets/articles_tags_dialog.dart';
import 'widgets/articles_list.dart';
import 'widgets/article_calendar_dialog.dart';
import 'package:daily_satori/app/styles/index.dart';
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
      appBar: _ArticlesAppBar(controller: controller),
      body: _ArticlesBody(controller: controller),
    );
  }
}

// ============================================================================
// AppBar 组件 - 独立封装，减少主视图复杂度
// ============================================================================

class _ArticlesAppBar extends StatelessWidget implements PreferredSizeWidget {
  final ArticlesController controller;

  const _ArticlesAppBar({required this.controller});

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context) {
    return SAppBar(
      backgroundColorDark: const Color(0xFF121212),
      backgroundColorLight: const Color(0xFF5E8BFF),
      elevation: 0.5,
      leading: _buildCalendarButton(context),
      title: _buildTitle(),
      centerTitle: true,
      actions: _buildActions(context),
      foregroundColor: Colors.white,
    );
  }

  Widget _buildCalendarButton(BuildContext context) {
    return IconButton(
      icon: const Icon(FeatherIcons.calendar, color: Colors.white, size: 20),
      onPressed: () => _showCalendarDialog(context),
    );
  }

  Widget _buildTitle() {
    return GestureDetector(
      onDoubleTap: _scrollToTop,
      child: Text(controller.getTitle(), style: AppTypography.titleLarge),
    );
  }

  List<Widget> _buildActions(BuildContext context) {
    return [
      IconButton(
        icon: const Icon(FeatherIcons.search, color: Colors.white, size: 20),
        onPressed: _activateSearch,
      ),
      _buildMoreMenu(context),
    ];
  }

  Widget _buildMoreMenu(BuildContext context) {
    return Obx(
      () => PopupMenuButton<String>(
        icon: const Icon(Icons.more_horiz, color: Colors.white, size: 20),
        onSelected: (value) => _handleMenuSelection(value, context),
        itemBuilder: (context) => [
          SPopupMenuItem<String>(value: 'tags', icon: FeatherIcons.tag, text: '标签筛选'),
          SPopupMenuItem<String>(
            value: 'favorite',
            icon: controller.onlyFavorite.value ? Icons.favorite : Icons.favorite_border,
            text: controller.onlyFavorite.value ? '显示全部文章' : '只看收藏文章',
            iconColor: controller.onlyFavorite.value ? Colors.red : null,
          ),
        ],
      ),
    );
  }

  void _handleMenuSelection(String value, BuildContext context) {
    logger.d('选择菜单项: $value');
    switch (value) {
      case 'tags':
        _showTagsDialog(context);
      case 'favorite':
        controller.toggleFavorite(!controller.onlyFavorite.value);
    }
  }

  void _scrollToTop() {
    if (controller.scrollController.hasClients) {
      controller.scrollController.animateTo(0, duration: AnimationConfig.duration, curve: Curves.easeInOut);
    }
  }

  void _activateSearch() {
    controller.searchController.clear();
    controller.toggleSearchState();
  }

  void _showTagsDialog(BuildContext context) {
    logger.d('显示标签对话框');
    final tags = TagRepository.i.allModels();

    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: RoundedRectangleBorder(borderRadius: Dimensions.borderRadiusTop),
      builder: (context) => ArticlesTagsDialog(
        tags: tags,
        selectedTagId: controller.tagId.value,
        onTagSelected: controller.filterByTag,
        onClearFilters: controller.clearAllFilters,
      ),
    );
  }

  void _showCalendarDialog(BuildContext context) {
    if (controller.hasActiveFilters()) {
      logger.d('清除现有过滤条件');
      controller.clearAllFilters();
    }

    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: RoundedRectangleBorder(borderRadius: Dimensions.borderRadiusTop),
      isScrollControlled: true,
      builder: (context) => ArticleCalendarDialog(
        articleCountMap: controller.getDailyArticleCounts(),
        onDateSelected: controller.filterByDate,
        onShowAllArticles: controller.clearAllFilters,
      ),
    );
  }
}

// ============================================================================
// Body 组件 - 包含搜索栏、过滤器和文章列表
// ============================================================================

class _ArticlesBody extends StatelessWidget {
  final ArticlesController controller;

  const _ArticlesBody({required this.controller});

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      final appStateService = Get.find<AppStateService>();
      final isSearchBarVisible = appStateService.isSearchBarVisible.value;
      final hasActiveFilters = controller.hasActiveFilters();
      final isLoading = controller.isLoadingArticles.value;

      return Column(
        children: [
          if (isSearchBarVisible) _buildSearchBar(),
          if (hasActiveFilters) _buildFilterIndicator(),
          Expanded(child: _buildArticlesList(isLoading)),
        ],
      );
    });
  }

  Widget _buildSearchBar() {
    logger.d('显示搜索栏');
    return GenericSearchBar(
      controller: controller.searchController,
      focusNode: controller.searchFocusNode,
      hintText: 'hint.search_articles'.t,
      onSearch: (text) => controller.searchArticles(),
      onClear: () {
        controller.searchController.clear();
        controller.clearAllFilters();
      },
      isSearchVisible: true,
      onToggleSearch: controller.toggleSearchState,
      showFilterButton: false,
    );
  }

  Widget _buildFilterIndicator() {
    logger.d('显示过滤指示器: ${controller.getTitle()}');
    return SFilterIndicator(title: controller.getTitle(), onClear: controller.clearAllFilters);
  }

  Widget _buildArticlesList(bool isLoading) {
    if (controller.articles.isEmpty) {
      logger.d('文章列表为空，显示空状态');
      return const ArticlesEmptyView();
    }

    return ArticlesList(
      articles: controller.articles.toList(),
      isLoading: isLoading,
      scrollController: controller.scrollController,
      onRefresh: controller.reloadArticles,
      onArticleTap: _handleArticleTap,
      onFavoriteToggle: _handleFavoriteToggle,
      onShare: _handleShare,
    );
  }

  void _handleArticleTap(ArticleModel article) {
    logger.d('点击文章卡片: ${article.id}');
    Get.toNamed(Routes.articleDetail, arguments: article);
  }

  Future<void> _handleFavoriteToggle(ArticleModel article) async {
    ArticleRepository.i.toggleFavorite(article.id);
    controller.updateArticle(article.id);
  }

  Future<void> _handleShare(ArticleModel article) async {
    await SharePlus.instance.share(
      ShareParams(text: article.url ?? '', subject: article.aiTitle ?? article.title ?? ''),
    );
  }
}

// 本地的 _FilterIndicator 已抽取为通用组件 SFilterIndicator
