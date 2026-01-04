import 'package:daily_satori/app_exports.dart';

import 'package:daily_satori/app/pages/articles/providers/articles_controller_provider.dart';

import 'widgets/articles_tags_dialog.dart';
import 'widgets/articles_list.dart';
import 'widgets/article_calendar_dialog.dart';
import 'package:daily_satori/app/styles/styles.dart';

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
    final displayTitle = ref.watch(displayTitleProvider);

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
        child: Text(displayTitle, style: AppTypography.titleLarge),
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
        _buildMoreMenu(context, ref, state, controller),
      ],
      foregroundColor: Colors.white,
    );
  }

  Widget _buildMoreMenu(
    BuildContext context,
    WidgetRef ref,
    ArticlesControllerState state,
    ArticlesController controller,
  ) {
    return PopupMenuButton<String>(
      icon: const Icon(Icons.more_horiz, color: Colors.white, size: 20),
      onSelected: (value) {
        switch (value) {
          case 'tags':
            _showTagsDialog(context, state, controller, ref);
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

  void _showTagsDialog(
    BuildContext context,
    ArticlesControllerState state,
    ArticlesController controller,
    WidgetRef ref,
  ) {
    final tags = ref.read(articleAllTagsProvider);

    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: Dimensions.borderRadiusTop),
      builder: (_) => ArticlesTagsDialog(
        tags: tags,
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
    final hasFilters = ref.read(hasFiltersProvider);
    if (hasFilters) {
      controller.clearAllFilters();
    }
    final articleCountMap = ref.read(articleDailyCountsProvider);

    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: Dimensions.borderRadiusTop),
      isScrollControlled: true,
      builder: (_) => ArticleCalendarDialog(
        articleCountMap: articleCountMap,
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
    final articles = ref.watch(articleStateProvider.select((s) => s.articles));
    final isLoading = ref.watch(articleStateProvider.select((s) => s.isLoading));
    final isSearchVisible = ref.watch(appGlobalStateProvider.select((s) => s.isSearchBarVisible));
    final hasFilters = ref.watch(hasFiltersProvider);
    final displayTitle = ref.watch(displayTitleProvider);

    return Column(
      children: [
        if (isSearchVisible) _buildSearchBar(state, controller),
        if (hasFilters) FilterIndicator(title: displayTitle, onClear: controller.clearAllFilters),
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
      onArticleTap: (article) => AppNavigation.toNamed(Routes.articleDetail, arguments: article.id),
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
