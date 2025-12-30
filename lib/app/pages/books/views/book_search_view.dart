import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

/// 书籍搜索结果视图
class BookSearchView extends ConsumerWidget {
  const BookSearchView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(appBar: _buildAppBar(context), body: _buildBody(context, ref));
  }

  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(title: Text('ui.search'.t, style: AppTypography.appBarTitle), elevation: 0);
  }

  Widget _buildBody(BuildContext context, WidgetRef ref) {
    return SafeArea(
      child: Column(
        children: [
          _buildSearchBar(context, ref),
          Expanded(child: _buildSearchResults(context, ref)),
        ],
      ),
    );
  }

  Widget _buildSearchBar(BuildContext context, WidgetRef ref) {
    final searchController = ref.read(bookSearchControllerProvider.notifier).searchController;

    return Container(
      padding: EdgeInsets.fromLTRB(
        Dimensions.paddingPage.left,
        Dimensions.spacingS,
        Dimensions.paddingPage.right,
        Dimensions.paddingPage.bottom,
      ),
      child: TextField(
        controller: searchController,
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

  Widget _buildSearchResults(BuildContext context, WidgetRef ref) {
    final controllerState = ref.watch(bookSearchControllerProvider);

    if (controllerState.isLoading) {
      return _buildLoadingState(context);
    }

    final searchController = ref.read(bookSearchControllerProvider.notifier).searchController;

    if (controllerState.searchResults.isEmpty && searchController.text.isNotEmpty) {
      return _buildEmptyState(context);
    }

    if (controllerState.searchResults.isEmpty) {
      return _buildInitialState(context);
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildSearchStatistics(context, controllerState.searchResults.length),
        Expanded(child: _buildResultsList(context, ref, controllerState.searchResults)),
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
          Text(
            'ui.processing'.t,
            style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurfaceVariant(context)),
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

  Widget _buildResultsList(BuildContext context, WidgetRef ref, List<BookModel> searchResults) {
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
        return _buildResultCard(context, ref, result);
      },
    );
  }

  Widget _buildResultCard(BuildContext context, WidgetRef ref, BookModel result) {
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
              if (result.coverImage.isNotEmpty) ...[
                ClipRRect(
                  borderRadius: BorderRadius.circular(Dimensions.radiusS),
                  child: Image.network(
                    result.coverImage,
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
                    // 查看详情提示
                    Dimensions.verticalSpacerS,
                    Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        Icon(
                          Icons.visibility_outlined,
                          size: Dimensions.iconSizeXs,
                          color: AppColors.getPrimary(context),
                        ),
                        Dimensions.horizontalSpacerXs,
                        Text(
                          '点击查看详情',
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
