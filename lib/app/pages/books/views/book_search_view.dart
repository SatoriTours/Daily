import 'package:daily_satori/app/pages/books/providers/book_search_controller_provider.dart';


import 'package:daily_satori/app_exports.dart';
/// 书籍搜索结果视图
class BookSearchView extends ConsumerStatefulWidget {
  const BookSearchView({super.key});

  @override
  ConsumerState<BookSearchView> createState() => _BookSearchViewState();
}

class _BookSearchViewState extends ConsumerState<BookSearchView> {
  late final TextEditingController _searchController;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final keyword = ref.read(bookSearchControllerProvider.notifier).consumeInitialKeyword();
      if (keyword.isNotEmpty) {
        _searchController.text = keyword;
        ref.read(bookSearchControllerProvider.notifier).searchBooks(keyword);
      }
    });
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(bookSearchControllerProvider);
    final title = state.searchResults.isNotEmpty ? '搜索 (${state.searchResults.length})' : 'ui.search'.t;

    return Scaffold(
      appBar: AppBar(title: Text(title, style: AppTypography.appBarTitle), elevation: 0),
      body: SafeArea(
        child: Column(
          children: [
            _buildSearchBar(context),
            Expanded(child: _SearchResults(searchController: _searchController)),
          ],
        ),
      ),
    );
  }

  Widget _buildSearchBar(BuildContext context) {
    return Container(
      padding: EdgeInsets.fromLTRB(
        Dimensions.paddingPage.left,
        Dimensions.spacingS,
        Dimensions.paddingPage.right,
        Dimensions.paddingPage.bottom,
      ),
      child: TextField(
        controller: _searchController,
        autofocus: true,
        decoration: InputStyles.getSearchDecoration(context, hintText: 'hint.title'.t).copyWith(
          contentPadding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingXs),
          isDense: true,
        ),
        onSubmitted: (value) {
          if (value.trim().isNotEmpty) {
            ref.read(bookSearchControllerProvider.notifier).searchBooks(value.trim());
          }
        },
      ),
    );
  }
}

/// 搜索结果区域
class _SearchResults extends ConsumerWidget {
  final TextEditingController searchController;
  const _SearchResults({required this.searchController});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(bookSearchControllerProvider);

    if (state.isSearching) return _buildSearchingState(context, state.searchKeyword);
    if (state.isLoading) return _buildLoadingState(context);
    if (state.errorMessage.isNotEmpty) return _buildErrorState(context, ref, state.errorMessage);
    if (state.searchResults.isEmpty && state.searchKeyword.isNotEmpty) return _buildEmptyState(context);
    if (state.searchResults.isEmpty) return _buildInitialState(context);

    return _buildResultsList(context, ref, state.searchResults);
  }

  Widget _buildSearchingState(BuildContext context, String keyword) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          SizedBox(
            width: 48,
            height: 48,
            child: CircularProgressIndicator(
              strokeWidth: 3,
              valueColor: AlwaysStoppedAnimation(AppColors.getPrimary(context)),
            ),
          ),
          Dimensions.verticalSpacerL,
          Text(
            '正在搜索「$keyword」...',
            style: AppTypography.bodyLarge.copyWith(
              color: AppColors.getOnSurface(context),
              fontWeight: FontWeight.w500,
            ),
          ),
          Dimensions.verticalSpacerS,
          Text('正在获取书籍信息', style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurfaceVariant(context))),
        ],
      ),
    );
  }

  Widget _buildLoadingState(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(valueColor: AlwaysStoppedAnimation(AppColors.getPrimary(context))),
          Dimensions.verticalSpacerM,
          Text('正在添加书籍...', style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurfaceVariant(context))),
        ],
      ),
    );
  }

  Widget _buildErrorState(BuildContext context, WidgetRef ref, String errorMessage) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.error_outline_rounded,
            size: 64,
            color: AppColors.getError(context).withValues(alpha: Opacities.high),
          ),
          Dimensions.verticalSpacerM,
          Text(
            '搜索失败',
            style: AppTypography.bodyLarge.copyWith(
              color: AppColors.getOnSurface(context),
              fontWeight: FontWeight.w500,
            ),
          ),
          Dimensions.verticalSpacerS,
          Padding(
            padding: Dimensions.paddingPage,
            child: Text(
              errorMessage,
              textAlign: TextAlign.center,
              style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurfaceVariant(context)),
            ),
          ),
          Dimensions.verticalSpacerL,
          FilledButton.icon(
            onPressed: () {
              final keyword = ref.read(bookSearchControllerProvider).searchKeyword;
              if (keyword.isNotEmpty) ref.read(bookSearchControllerProvider.notifier).searchBooks(keyword);
            },
            icon: const Icon(Icons.refresh),
            label: Text('button.retry'.t),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.search_off_rounded,
            size: 64,
            color: AppColors.getOnSurfaceVariant(context).withValues(alpha: Opacities.high),
          ),
          Dimensions.verticalSpacerM,
          Text(
            'empty.search'.t,
            style: AppTypography.bodyLarge.copyWith(color: AppColors.getOnSurfaceVariant(context)),
          ),
          Dimensions.verticalSpacerS,
          Text(
            '请尝试使用更具体的关键词',
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.getOnSurfaceVariant(context).withValues(alpha: Opacities.highOpaque),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInitialState(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.menu_book_rounded,
            size: 64,
            color: AppColors.getOnSurfaceVariant(context).withValues(alpha: Opacities.high),
          ),
          Dimensions.verticalSpacerM,
          Text('搜索书籍', style: AppTypography.bodyLarge.copyWith(color: AppColors.getOnSurfaceVariant(context))),
          Dimensions.verticalSpacerS,
          Text(
            '输入书名、作者或关键词进行搜索',
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.getOnSurfaceVariant(context).withValues(alpha: Opacities.highOpaque),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildResultsList(BuildContext context, WidgetRef ref, List<BookSearchResult> results) {
    return ListView.builder(
      padding: EdgeInsets.fromLTRB(
        Dimensions.paddingPage.left,
        Dimensions.spacingM,
        Dimensions.paddingPage.right,
        Dimensions.paddingPage.bottom,
      ),
      itemCount: results.length,
      itemBuilder: (_, index) => _BookResultCard(result: results[index]),
    );
  }
}

/// 书籍搜索结果卡片
class _BookResultCard extends ConsumerWidget {
  final BookSearchResult result;
  const _BookResultCard({required this.result});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Card(
      margin: const EdgeInsets.only(bottom: Dimensions.spacingM),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        side: BorderSide(color: AppColors.getOnSurfaceVariant(context).withValues(alpha: Opacities.low), width: 1),
      ),
      child: InkWell(
        onTap: () => ref.read(bookSearchControllerProvider.notifier).selectBook(result),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        child: Padding(
          padding: Dimensions.paddingCard,
          child: IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (result.coverUrl.isNotEmpty) ...[_buildCover(context), Dimensions.horizontalSpacerS],
                Expanded(child: _buildInfo(context)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildCover(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      child: Image.network(
        result.coverUrl,
        width: 80,
        height: 120,
        fit: BoxFit.cover,
        errorBuilder: (context, error, stackTrace) => Container(
          width: 80,
          height: 120,
          decoration: BoxDecoration(
            color: AppColors.getOnSurfaceVariant(context).withValues(alpha: Opacities.low),
            borderRadius: BorderRadius.circular(Dimensions.radiusS),
          ),
          child: Icon(
            Icons.book,
            size: 40,
            color: AppColors.getOnSurfaceVariant(context).withValues(alpha: Opacities.high),
          ),
        ),
      ),
    );
  }

  Widget _buildInfo(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 标题行
            Text(
              result.title,
              style: AppTypography.titleMedium.copyWith(
                color: AppColors.getOnSurface(context),
                fontWeight: FontWeight.w600,
                height: 1.3,
              ),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            // 作者
            Dimensions.verticalSpacerS,
            Row(
              children: [
                Icon(
                  Icons.person_outline,
                  size: Dimensions.iconSizeXs - 2,
                  color: AppColors.getOnSurfaceVariant(context).withValues(alpha: Opacities.highOpaque),
                ),
                const SizedBox(width: Dimensions.spacingXs + 2),
                Expanded(
                  child: Text(
                    result.author,
                    style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurfaceVariant(context)),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
            // 简介
            if (result.introduction.isNotEmpty) ...[
              Dimensions.verticalSpacerS,
              Text(
                result.introduction,
                style: AppTypography.bodySmall.copyWith(color: AppColors.getOnSurfaceVariant(context), height: 1.5),
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ],
        ),
        // 添加提示
        Padding(
          padding: const EdgeInsets.only(top: Dimensions.spacingS),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              Icon(Icons.add_circle_outline, size: Dimensions.iconSizeXs, color: AppColors.getPrimary(context)),
              Dimensions.horizontalSpacerXs,
              Text(
                '点击添加书籍',
                style: AppTypography.labelSmall.copyWith(
                  color: AppColors.getPrimary(context),
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}