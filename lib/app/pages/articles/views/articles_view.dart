/// 文章列表页面
///
/// 简洁的状态管理:
/// - 页面状态 (ArticlesControllerState): 过滤条件、排序等
/// - 数据状态 (ArticleState): 文章列表、加载状态等
library;

import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/pages/articles/providers/articles_controller_provider.dart';
import 'package:daily_satori/app/pages/articles/providers/articles_derived_providers.dart';

import 'widgets/articles_list.dart';
import 'widgets/articles_tags_dialog.dart';
import 'widgets/article_calendar_dialog.dart';

class ArticlesView extends ConsumerWidget {
  const ArticlesView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surface,
      appBar: const ArticlesAppBar(),
      body: const ArticlesBody(),
    );
  }
}

class ArticlesAppBar extends ConsumerWidget implements PreferredSizeWidget {
  const ArticlesAppBar({super.key});

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
        onPressed: () => _showCalendarDialog(context, ref),
        tooltip: '日历',
      ),
      title: GestureDetector(
        onDoubleTap: () => ArticlesList.scrollToTop(context),
        child: Text(displayTitle, style: AppTypography.titleLarge),
      ),
      centerTitle: true,
      actions: [
        IconButton(
          icon: const Icon(FeatherIcons.search, color: Colors.white, size: 20),
          onPressed: controller.toggleSearchState,
          tooltip: '搜索',
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
            _showTagsDialog(context, ref, state);
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
    WidgetRef ref,
    ArticlesControllerState state,
  ) {
    final tags = ref.read(articleAllTagsProvider);
    final controller = ref.read(articlesControllerProvider.notifier);

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

  void _showCalendarDialog(BuildContext context, WidgetRef ref) {
    final controller = ref.read(articlesControllerProvider.notifier);
    final hasFilters = ref.read(hasFiltersProvider);

    if (hasFilters) controller.clearAllFilters();
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

class ArticlesBody extends ConsumerWidget {
  const ArticlesBody({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final articles = ref.watch(articleStateProvider.select((s) => s.articles));
    final isLoading = ref.watch(
      articleStateProvider.select((s) => s.isLoading),
    );
    final isSearchVisible = ref.watch(
      appGlobalStateProvider.select((s) => s.isSearchBarVisible),
    );
    final hasFilters = ref.watch(hasFiltersProvider);
    final displayTitle = ref.watch(displayTitleProvider);
    final controller = ref.read(articlesControllerProvider.notifier);

    return Column(
      children: [
        if (isSearchVisible) const ArticlesSearchBar(),
        if (hasFilters)
          FilterIndicator(
            title: displayTitle,
            onClear: controller.clearAllFilters,
          ),
        Expanded(
          child: ArticlesList(
            articles: articles,
            isLoading: isLoading,
            onRefresh: controller.reloadArticles,
            onArticleTap: (article) => AppNavigation.toNamed(
              Routes.articleDetail,
              arguments: article.id,
            ),
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
          ),
        ),
      ],
    );
  }
}

class ArticlesSearchBar extends ConsumerStatefulWidget {
  const ArticlesSearchBar({super.key});

  @override
  ConsumerState<ArticlesSearchBar> createState() => _ArticlesSearchBarState();
}

class _ArticlesSearchBarState extends ConsumerState<ArticlesSearchBar> {
  late final TextEditingController _controller;
  late final FocusNode _focusNode;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
    _focusNode = FocusNode();
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GenericSearchBar(
      controller: _controller,
      focusNode: _focusNode,
      hintText: 'hint.search_articles'.t,
      onSearch: (query) {
        ref.read(articlesControllerProvider.notifier).searchArticles(query);
        _focusNode.unfocus();
      },
      onClear: () {
        _controller.clear();
        ref.read(articlesControllerProvider.notifier).clearAllFilters();
      },
      isSearchVisible: true,
      onToggleSearch: ref
          .read(articlesControllerProvider.notifier)
          .toggleSearchState,
      showFilterButton: false,
    );
  }
}
