import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/pages/books/providers/book_search_controller_provider.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

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

    // 页面加载后初始化搜索
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final initialKeyword = ref.read(bookSearchControllerProvider.notifier).consumeInitialKeyword();
      if (initialKeyword.isNotEmpty) {
        _searchController.text = initialKeyword;
        ref.read(bookSearchControllerProvider.notifier).searchBooks(initialKeyword);
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
    return Scaffold(appBar: _buildAppBar(context), body: _buildBody(context));
  }

  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(title: Text('ui.search'.t, style: AppTypography.appBarTitle), elevation: 0);
  }

  Widget _buildBody(BuildContext context) {
    return SafeArea(
      child: Column(
        children: [
          _buildSearchBar(context),
          Expanded(child: _buildSearchResults(context)),
        ],
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

  Widget _buildSearchResults(BuildContext context) {
    final controllerState = ref.watch(bookSearchControllerProvider);

    // 搜索中显示加载状态
    if (controllerState.isSearching) {
      return _buildSearchingState(context, controllerState.searchKeyword);
    }

    // 添加书籍中显示加载状态
    if (controllerState.isLoading) {
      return _buildLoadingState(context);
    }

    // 有错误时显示错误状态
    if (controllerState.errorMessage.isNotEmpty) {
      return _buildErrorState(context, controllerState.errorMessage);
    }

    // 搜索后无结果
    if (controllerState.searchResults.isEmpty && controllerState.searchKeyword.isNotEmpty) {
      return _buildEmptyState(context);
    }

    // 初始状态
    if (controllerState.searchResults.isEmpty) {
      return _buildInitialState(context);
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildSearchStatistics(context, controllerState.searchResults.length),
        Expanded(child: _buildResultsList(context, controllerState.searchResults)),
      ],
    );
  }

  /// 构建搜索统计信息
  Widget _buildSearchStatistics(BuildContext context, int resultCount) {
    return Container(
      padding: EdgeInsets.symmetric(horizontal: Dimensions.paddingPage.horizontal, vertical: Dimensions.spacingS),
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        border: Border(
          bottom: BorderSide(color: AppColors.getOnSurfaceVariant(context).withValues(alpha: Opacities.low), width: 1),
        ),
      ),
      child: Row(
        children: [
          Icon(
            Icons.library_books_outlined,
            size: Dimensions.iconSizeXs,
            color: AppColors.getOnSurfaceVariant(context),
          ),
          Dimensions.horizontalSpacerS,
          Text(
            '找到  本相关书籍',
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.getOnSurfaceVariant(context),
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLoadingState(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(valueColor: AlwaysStoppedAnimation<Color>(AppColors.getPrimary(context))),
          Dimensions.verticalSpacerM,
          Text('正在添加书籍...', style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurfaceVariant(context))),
        ],
      ),
    );
  }

  /// 搜索中状态
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
              valueColor: AlwaysStoppedAnimation<Color>(AppColors.getPrimary(context)),
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
          Text(
            '正在从 OpenLibrary 获取书籍信息',
            style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurfaceVariant(context)),
          ),
        ],
      ),
    );
  }

  /// 错误状态
  Widget _buildErrorState(BuildContext context, String errorMessage) {
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
              if (keyword.isNotEmpty) {
                ref.read(bookSearchControllerProvider.notifier).searchBooks(keyword);
              }
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

  Widget _buildResultsList(BuildContext context, List<BookSearchResult> searchResults) {
    return ListView.builder(
      padding: EdgeInsets.fromLTRB(
        Dimensions.paddingPage.left,
        Dimensions.spacingM,
        Dimensions.paddingPage.right,
        Dimensions.paddingPage.bottom,
      ),
      itemCount: searchResults.length,
      itemBuilder: (context, index) {
        final result = searchResults[index];
        return _buildResultCard(context, result);
      },
    );
  }

  Widget _buildResultCard(BuildContext context, BookSearchResult result) {
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
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 封面图片
              if (result.coverUrl.isNotEmpty) ...[
                ClipRRect(
                  borderRadius: BorderRadius.circular(Dimensions.radiusS),
                  child: Image.network(
                    result.coverUrl,
                    width: 80,
                    height: 120,
                    fit: BoxFit.cover,
                    errorBuilder: (context, error, stackTrace) {
                      return Container(
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
                      );
                    },
                  ),
                ),
                Dimensions.horizontalSpacerS,
              ],
              // 书籍信息
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 标题和分类行
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: Text(
                            result.title,
                            style: AppTypography.titleMedium.copyWith(
                              color: AppColors.getOnSurface(context),
                              fontWeight: FontWeight.w600,
                              height: 1.3,
                            ),
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        if (result.category.isNotEmpty) ...[
                          Dimensions.horizontalSpacerS,
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: Dimensions.spacingS + 2,
                              vertical: Dimensions.spacingXs,
                            ),
                            decoration: BoxDecoration(
                              color: AppColors.getPrimary(context).withValues(alpha: Opacities.low),
                              borderRadius: BorderRadius.circular(Dimensions.radiusS),
                            ),
                            child: Text(
                              result.category,
                              style: AppTypography.labelSmall.copyWith(color: AppColors.getPrimary(context)),
                            ),
                          ),
                        ],
                      ],
                    ),
                    // 作者信息
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
                        style: AppTypography.bodySmall.copyWith(
                          color: AppColors.getOnSurfaceVariant(context),
                          height: 1.5,
                        ),
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                    // 添加书籍提示
                    Dimensions.verticalSpacerS,
                    Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        Icon(
                          Icons.add_circle_outline,
                          size: Dimensions.iconSizeXs,
                          color: AppColors.getPrimary(context),
                        ),
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
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
