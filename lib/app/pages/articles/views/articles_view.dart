import 'package:daily_satori/app_exports.dart';

import 'package:daily_satori/app/pages/articles/providers/articles_controller_provider.dart';
import 'package:daily_satori/app/pages/articles/providers/articles_derived_providers.dart';

import 'widgets/articles_tags_dialog.dart';
import 'widgets/articles_list.dart';
import 'widgets/article_calendar_dialog.dart';

/// 文章列表页面
class ArticlesView extends ConsumerStatefulWidget {
  const ArticlesView({super.key});

  @override
  ConsumerState<ArticlesView> createState() => _ArticlesViewState();
}

class _ArticlesViewState extends ConsumerState<ArticlesView> {
  final ScrollController _scrollController = ScrollController();
  final TextEditingController _searchController = TextEditingController();
  final FocusNode _searchFocusNode = FocusNode();

  @override
  void dispose() {
    _scrollController.dispose();
    _searchController.dispose();
    _searchFocusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surface,
      appBar: const _ArticlesAppBar(),
      body: const _ArticlesBody(),
    );
  }
}

// AppBar 组件

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
        onDoubleTap: () => _scrollToTop(context),
        child: Text(displayTitle, style: AppTypography.titleLarge),
      ),
      centerTitle: true,
      actions: [
        IconButton(
          icon: const Icon(FeatherIcons.search, color: Colors.white, size: 20),
          onPressed: () {
            controller.toggleSearchState();
          },
        ),
        _buildMoreMenu(context, ref, state, controller),
      ],
      foregroundColor: Colors.white,
    );
  }

  void _scrollToTop(BuildContext context) {
    final scrollController = context
        .findAncestorStateOfType<_ArticlesViewState>()
        ?._scrollController;
    if (scrollController != null && scrollController.hasClients) {
      scrollController.animateTo(
        0,
        duration: Animations.durationNormal,
        curve: Curves.easeInOut,
      );
    }
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
        SPopupMenuItem<String>(
          value: 'tags',
          icon: FeatherIcons.tag,
          text: 'article.filter_tags'.t,
        ),
        SPopupMenuItem<String>(
          value: 'favorite',
          icon: state.onlyFavorite ? Icons.favorite : Icons.favorite_border,
          text: state.onlyFavorite
              ? 'article.show_all'.t
              : 'article.show_favorite_only'.t,
          iconColor: state.onlyFavorite ? Colors.red : null,
        ),
      ],
    );
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
      shape: const RoundedRectangleBorder(
        borderRadius: Dimensions.borderRadiusTop,
      ),
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
      shape: const RoundedRectangleBorder(
        borderRadius: Dimensions.borderRadiusTop,
      ),
      isScrollControlled: true,
      builder: (_) => ArticleCalendarDialog(
        articleCountMap: articleCountMap,
        onDateSelected: controller.filterByDate,
        onShowAllArticles: controller.clearAllFilters,
      ),
    );
  }
}

// Body 组件

class _ArticlesBody extends ConsumerWidget {
  const _ArticlesBody();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controller = ref.read(articlesControllerProvider.notifier);
    final articles = ref.watch(articleStateProvider.select((s) => s.articles));
    final isLoading = ref.watch(
      articleStateProvider.select((s) => s.isLoading),
    );
    final isSearchVisible = ref.watch(
      appGlobalStateProvider.select((s) => s.isSearchBarVisible),
    );
    final hasFilters = ref.watch(hasFiltersProvider);
    final displayTitle = ref.watch(displayTitleProvider);

    final articlesViewState = context
        .findAncestorStateOfType<_ArticlesViewState>();
    final scrollController =
        articlesViewState?._scrollController ?? ScrollController();
    final searchController =
        articlesViewState?._searchController ?? TextEditingController();
    final searchFocusNode = articlesViewState?._searchFocusNode ?? FocusNode();

    return Column(
      children: [
        if (isSearchVisible)
          GenericSearchBar(
            controller: searchController,
            focusNode: searchFocusNode,
            hintText: 'hint.search_articles'.t,
            onSearch: (query) => controller.searchArticles(query),
            onClear: () {
              controller.clearAllFilters();
            },
            isSearchVisible: true,
            onToggleSearch: controller.toggleSearchState,
            showFilterButton: false,
          ),
        if (hasFilters)
          FilterIndicator(
            title: displayTitle,
            onClear: controller.clearAllFilters,
          ),
        Expanded(
          child: _buildArticlesList(
            articles,
            isLoading,
            controller,
            scrollController,
          ),
        ),
      ],
    );
  }

  Widget _buildArticlesList(
    List<ArticleModel> articles,
    bool isLoading,
    ArticlesController controller,
    ScrollController scrollController,
  ) {
    if (articles.isEmpty && !isLoading) {
      return const ArticlesEmptyView();
    }
    return ArticlesList(
      articles: articles,
      isLoading: isLoading,
      scrollController: scrollController,
      onRefresh: controller.reloadArticles,
      onArticleTap: (article) =>
          AppNavigation.toNamed(Routes.articleDetail, arguments: article.id),
      onFavoriteToggle: (article) {
        ArticleRepository.i.toggleFavorite(article.id);
        controller.updateArticle(article.id);
      },
      onShare: (article) => SharePlus.instance.share(
        ShareParams(
          text: article.url ?? '',
          subject: article.aiTitle ?? article.title ?? '',
        ),
      ),
    );
  }
}
