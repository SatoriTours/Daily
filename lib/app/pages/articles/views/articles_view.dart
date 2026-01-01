import 'package:daily_satori/app/navigation/app_navigation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:share_plus/share_plus.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/pages/articles/providers/articles_controller_provider.dart';
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
class ArticlesView extends ConsumerWidget {
  const ArticlesView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surface,
      appBar: const _ArticlesAppBar(),
      body: const _ArticlesBody(),
    );
  }
}

// ============================================================================
// AppBar 组件
// ============================================================================

class _ArticlesAppBar extends ConsumerWidget implements PreferredSizeWidget {
  const _ArticlesAppBar();

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(articlesControllerProvider);
    final controller = ref.read(articlesControllerProvider.notifier);
    final title = ref.watch(articlesTitleProvider);

    return SAppBar(
      backgroundColorDark: AppColors.backgroundDark,
      backgroundColorLight: AppColors.primary,
      elevation: 0.5,
      leading: IconButton(
        icon: const Icon(FeatherIcons.calendar, color: Colors.white, size: 20),
        onPressed: () => _showCalendarDialog(context, ref, state, controller),
      ),
      title: GestureDetector(
        onDoubleTap: () => _scrollToTop(state),
        child: Text(title, style: AppTypography.titleLarge),
      ),
      centerTitle: true,
      actions: [
        IconButton(
          icon: const Icon(FeatherIcons.search, color: Colors.white, size: 20),
          onPressed: () {
            state.searchController?.clear();
            controller.toggleSearchState();
          },
        ),
        _buildMoreMenu(context, state, controller),
      ],
      foregroundColor: Colors.white,
    );
  }

  Widget _buildMoreMenu(BuildContext context, ArticlesControllerState state, ArticlesController controller) {
    return PopupMenuButton<String>(
      icon: const Icon(Icons.more_horiz, color: Colors.white, size: 20),
      onSelected: (value) {
        switch (value) {
          case 'tags':
            _showTagsDialog(context, state, controller);
          case 'favorite':
            controller.toggleFavorite(!state.onlyFavorite);
        }
      },
      itemBuilder: (_) => [
        SPopupMenuItem<String>(value: 'tags', icon: FeatherIcons.tag, text: 'article.filter_tags'.t),
        SPopupMenuItem<String>(
          value: 'favorite',
          icon: state.onlyFavorite ? Icons.favorite : Icons.favorite_border,
          text: state.onlyFavorite ? 'article.show_all'.t : 'article.show_favorite_only'.t,
          iconColor: state.onlyFavorite ? Colors.red : null,
        ),
      ],
    );
  }

  void _scrollToTop(ArticlesControllerState state) {
    final sc = state.scrollController;
    if (sc != null && sc.hasClients) {
      sc.animateTo(0, duration: Animations.durationNormal, curve: Curves.easeInOut);
    }
  }

  void _showTagsDialog(BuildContext context, ArticlesControllerState state, ArticlesController controller) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: Dimensions.borderRadiusTop),
      builder: (_) => ArticlesTagsDialog(
        tags: TagRepository.i.allModels(),
        selectedTagId: state.tagId,
        onTagSelected: controller.filterByTag,
        onClearFilters: controller.clearAllFilters,
      ),
    );
  }

  void _showCalendarDialog(
    BuildContext context,
    WidgetRef ref,
    ArticlesControllerState state,
    ArticlesController controller,
  ) {
    final hasFilters = ref.read(articlesHasFiltersProvider);
    if (hasFilters) {
      controller.clearAllFilters();
    }
    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: Dimensions.borderRadiusTop),
      isScrollControlled: true,
      builder: (_) => ArticleCalendarDialog(
        articleCountMap: controller.getDailyArticleCounts(),
        onDateSelected: controller.filterByDate,
        onShowAllArticles: controller.clearAllFilters,
      ),
    );
  }
}

// ============================================================================
// Body 组件
// ============================================================================

class _ArticlesBody extends ConsumerWidget {
  const _ArticlesBody();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(articlesControllerProvider);
    final controller = ref.read(articlesControllerProvider.notifier);
    final isSearchVisible = ref.watch(appGlobalStateProvider.select((s) => s.isSearchBarVisible));
    final articles = ref.watch(articleStateProvider.select((s) => s.articles));
    final isLoading = ref.watch(articleStateProvider.select((s) => s.isLoading));
    final hasFilters = ref.watch(articlesHasFiltersProvider);
    final title = ref.watch(articlesTitleProvider);

    return Column(
      children: [
        if (isSearchVisible) _buildSearchBar(state, controller),
        if (hasFilters) FilterIndicator(title: title, onClear: controller.clearAllFilters),
        Expanded(child: _buildArticlesList(articles, isLoading, state, controller)),
      ],
    );
  }

  Widget _buildSearchBar(ArticlesControllerState state, ArticlesController controller) {
    return GenericSearchBar(
      controller: state.searchController!,
      focusNode: state.searchFocusNode!,
      hintText: 'hint.search_articles'.t,
      onSearch: (_) => controller.searchArticles(state.searchController!),
      onClear: () {
        state.searchController!.clear();
        controller.clearAllFilters(state.searchController);
      },
      isSearchVisible: true,
      onToggleSearch: controller.toggleSearchState,
      showFilterButton: false,
    );
  }

  Widget _buildArticlesList(
    List<ArticleModel> articles,
    bool isLoading,
    ArticlesControllerState state,
    ArticlesController controller,
  ) {
    if (articles.isEmpty && !isLoading) {
      return const ArticlesEmptyView();
    }
    return ArticlesList(
      articles: articles,
      isLoading: isLoading,
      scrollController: state.scrollController!,
      onRefresh: controller.reloadArticles,
      onArticleTap: (article) => AppNavigation.toNamed(Routes.articleDetail, arguments: article),
      onFavoriteToggle: (article) {
        ArticleRepository.i.toggleFavorite(article.id);
        controller.updateArticle(article.id);
      },
      onShare: (article) => SharePlus.instance.share(
        ShareParams(text: article.url ?? '', subject: article.aiTitle ?? article.title ?? ''),
      ),
    );
  }
}
