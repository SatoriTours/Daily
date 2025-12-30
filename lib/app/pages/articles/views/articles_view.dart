import 'package:daily_satori/app/navigation/app_navigation.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:share_plus/share_plus.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/components/empty_states/articles_empty_view.dart';
import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/routes/app_routes.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/components/search/generic_search_bar.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import 'widgets/articles_tags_dialog.dart';
import 'widgets/articles_list.dart';
import 'widgets/article_calendar_dialog.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/components/indicators/s_filter_indicator.dart';
import 'package:daily_satori/app/components/menus/s_popup_menu_item.dart';

/// 文章列表页面
/// 负责展示文章列表、搜索、过滤等功能
class ArticlesView extends ConsumerWidget {
  const ArticlesView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    logger.i('构建文章列表页面');
    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surface,
      appBar: _ArticlesAppBar(ref: ref),
      body: _ArticlesBody(ref: ref),
    );
  }
}

// ============================================================================
// AppBar 组件 - 独立封装，减少主视图复杂度
// ============================================================================

class _ArticlesAppBar extends StatelessWidget implements PreferredSizeWidget {
  final WidgetRef ref;

  const _ArticlesAppBar({required this.ref});

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context) {
    return SAppBar(
      backgroundColorDark: AppColors.backgroundDark,
      backgroundColorLight: AppColors.primary,
      elevation: 0.5,
      leading: _buildCalendarButton(context, ref),
      title: _buildTitle(ref),
      centerTitle: true,
      actions: _buildActions(context, ref),
      foregroundColor: Colors.white,
    );
  }

  Widget _buildCalendarButton(BuildContext context, WidgetRef ref) {
    return IconButton(
      icon: const Icon(FeatherIcons.calendar, color: Colors.white, size: 20),
      onPressed: () => _showCalendarDialog(context, ref),
    );
  }

  Widget _buildTitle(WidgetRef ref) {
    final state = ref.watch(articlesControllerProvider);
    return GestureDetector(
      onDoubleTap: () => _scrollToTop(ref),
      child: Text(state.getTitle(ref), style: AppTypography.titleLarge),
    );
  }

  List<Widget> _buildActions(BuildContext context, WidgetRef ref) {
    return [
      IconButton(
        icon: const Icon(FeatherIcons.search, color: Colors.white, size: 20),
        onPressed: () => _activateSearch(ref),
      ),
      _buildMoreMenu(context, ref),
    ];
  }

  Widget _buildMoreMenu(BuildContext context, WidgetRef ref) {
    final state = ref.watch(articlesControllerProvider);
    return PopupMenuButton<String>(
      icon: const Icon(Icons.more_horiz, color: Colors.white, size: 20),
      onSelected: (value) => _handleMenuSelection(value, context, ref),
      itemBuilder: (context) {
        final isFavorite = state.onlyFavorite;
        return [
          SPopupMenuItem<String>(value: 'tags', icon: FeatherIcons.tag, text: 'article.filter_tags'.t),
          SPopupMenuItem<String>(
            value: 'favorite',
            icon: isFavorite ? Icons.favorite : Icons.favorite_border,
            text: isFavorite ? 'article.show_all'.t : 'article.show_favorite_only'.t,
            iconColor: isFavorite ? Colors.red : null,
          ),
        ];
      },
    );
  }

  void _handleMenuSelection(String value, BuildContext context, WidgetRef ref) {
    switch (value) {
      case 'tags':
        _showTagsDialog(context, ref);
      case 'favorite':
        final state = ref.read(articlesControllerProvider);
        ref.read(articlesControllerProvider.notifier).toggleFavorite(!state.onlyFavorite);
    }
  }

  void _scrollToTop(WidgetRef ref) {
    final state = ref.read(articlesControllerProvider);
    if (state.scrollController != null && state.scrollController!.hasClients) {
      state.scrollController!.animateTo(0, duration: Animations.durationNormal, curve: Curves.easeInOut);
    }
  }

  void _activateSearch(WidgetRef ref) {
    final state = ref.read(articlesControllerProvider);
    state.searchController?.clear();
    ref.read(articlesControllerProvider.notifier).toggleSearchState();
  }

  void _showTagsDialog(BuildContext context, WidgetRef ref) {
    final state = ref.read(articlesControllerProvider);
    final tags = TagRepository.i.allModels();

    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: Dimensions.borderRadiusTop),
      builder: (context) => ArticlesTagsDialog(
        tags: tags,
        selectedTagId: state.tagId,
        onTagSelected: (id, name) => ref.read(articlesControllerProvider.notifier).filterByTag(id, name),
        onClearFilters: () => ref.read(articlesControllerProvider.notifier).clearAllFilters(),
      ),
    );
  }

  void _showCalendarDialog(BuildContext context, WidgetRef ref) {
    final state = ref.read(articlesControllerProvider);
    if (state.hasActiveFilters(ref)) {
      ref.read(articlesControllerProvider.notifier).clearAllFilters();
    }

    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: Dimensions.borderRadiusTop),
      isScrollControlled: true,
      builder: (context) => ArticleCalendarDialog(
        articleCountMap: state.getDailyArticleCounts(),
        onDateSelected: (date) => ref.read(articlesControllerProvider.notifier).filterByDate(date),
        onShowAllArticles: () => ref.read(articlesControllerProvider.notifier).clearAllFilters(),
      ),
    );
  }
}

// ============================================================================
// Body 组件 - 包含搜索栏、过滤器和文章列表
// ============================================================================

class _ArticlesBody extends StatelessWidget {
  final WidgetRef ref;

  const _ArticlesBody({required this.ref});

  @override
  Widget build(BuildContext context) {
    final articlesState = ref.watch(articlesControllerProvider);
    final appState = ref.watch(appGlobalStateProvider);
    final isSearchBarVisible = appState.isSearchBarVisible;
    final hasActiveFilters = articlesState.hasActiveFilters(ref);
    final isLoading = articlesState.isLoadingArticles(ref);
    final filterTitle = hasActiveFilters ? articlesState.getTitle(ref) : '';

    return Column(
      children: [
        if (isSearchBarVisible) _buildSearchBar(ref),
        if (hasActiveFilters)
          FilterIndicator(
            title: filterTitle,
            onClear: () => ref.read(articlesControllerProvider.notifier).clearAllFilters(),
          ),
        Expanded(child: _buildArticlesList(ref, isLoading)),
      ],
    );
  }

  Widget _buildSearchBar(WidgetRef ref) {
    final state = ref.read(articlesControllerProvider);
    return GenericSearchBar(
      controller: state.searchController!,
      focusNode: state.searchFocusNode!,
      hintText: 'hint.search_articles'.t,
      onSearch: (text) => ref.read(articlesControllerProvider.notifier).searchArticles(state.searchController!),
      onClear: () {
        state.searchController!.clear();
        ref.read(articlesControllerProvider.notifier).clearAllFilters(state.searchController);
      },
      isSearchVisible: true,
      onToggleSearch: () => ref.read(articlesControllerProvider.notifier).toggleSearchState(),
      showFilterButton: false,
    );
  }

  Widget _buildArticlesList(WidgetRef ref, bool isLoading) {
    final state = ref.watch(articlesControllerProvider);
    final articles = state.getArticles(ref);
    if (articles.isEmpty) {
      return const ArticlesEmptyView();
    }

    return ArticlesList(
      articles: articles.toList(),
      isLoading: isLoading,
      scrollController: state.scrollController!,
      onRefresh: () => ref.read(articlesControllerProvider.notifier).reloadArticles(),
      onArticleTap: (article) => _handleArticleTap(article),
      onFavoriteToggle: (article) => _handleFavoriteToggle(article, ref),
      onShare: (article) => _handleShare(article),
    );
  }

  void _handleArticleTap(ArticleModel article) {
    AppNavigation.toNamed(Routes.articleDetail, arguments: article);
  }

  Future<void> _handleFavoriteToggle(ArticleModel article, WidgetRef ref) async {
    ArticleRepository.i.toggleFavorite(article.id);
    ref.read(articlesControllerProvider.notifier).updateArticle(article.id);
  }

  Future<void> _handleShare(ArticleModel article) async {
    await SharePlus.instance.share(
      ShareParams(text: article.url ?? '', subject: article.aiTitle ?? article.title ?? ''),
    );
  }
}

// 本地的 _FilterIndicator 已抽取为通用组件 SFilterIndicator
