import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/services/i18n/index.dart';
import 'package:daily_satori/app/models/book_search_result.dart';
import 'package:daily_satori/app/modules/books/controllers/book_search_controller.dart';

/// 书籍搜索结果视图
class BookSearchView extends GetView<BookSearchController> {
  const BookSearchView({super.key});

  @override
  Widget build(BuildContext context) {
    final i18nService = I18nService.i;
    return Scaffold(appBar: _buildAppBar(context, i18nService), body: _buildBody(context, i18nService));
  }

  PreferredSizeWidget _buildAppBar(BuildContext context, I18nService i18nService) {
    return AppBar(title: Text(i18nService.translations.search, style: AppTypography.appBarTitle), elevation: 0);
  }

  Widget _buildBody(BuildContext context, I18nService i18nService) {
    return SafeArea(
      child: Column(
        children: [
          _buildSearchBar(context, i18nService),
          Expanded(child: _buildSearchResults(context, i18nService)),
        ],
      ),
    );
  }

  Widget _buildSearchBar(BuildContext context, I18nService i18nService) {
    return Container(
      padding: EdgeInsets.fromLTRB(
        Dimensions.paddingPage.left,
        Dimensions.spacingS,
        Dimensions.paddingPage.right,
        Dimensions.paddingPage.bottom,
      ),
      child: TextField(
        controller: controller.searchController,
        autofocus: true,
        decoration: InputStyles.getSearchDecoration(
          context,
          hintText: i18nService.translations.hintTitle,
        ).copyWith(contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4), isDense: true),
        onSubmitted: (value) {
          if (value.trim().isNotEmpty) {
            controller.searchBooks(value.trim());
          }
        },
      ),
    );
  }

  Widget _buildSearchResults(BuildContext context, I18nService i18nService) {
    return Obx(() {
      if (controller.isLoading.value) {
        return _buildLoadingState(context, i18nService);
      }

      if (controller.searchResults.isEmpty && controller.searchController.text.isNotEmpty) {
        return _buildEmptyState(context, i18nService);
      }

      if (controller.searchResults.isEmpty) {
        return _buildInitialState(context, i18nService);
      }

      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _buildSearchStatistics(context),
          Expanded(child: _buildResultsList(context, i18nService)),
        ],
      );
    });
  }

  /// 构建搜索统计信息
  Widget _buildSearchStatistics(BuildContext context) {
    return Container(
      padding: EdgeInsets.symmetric(horizontal: Dimensions.paddingPage.horizontal, vertical: Dimensions.spacingS),
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        border: Border(
          bottom: BorderSide(color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.1), width: 1),
        ),
      ),
      child: Row(
        children: [
          Icon(Icons.library_books_outlined, size: 16, color: AppColors.getOnSurfaceVariant(context)),
          const SizedBox(width: 8),
          Text(
            '找到 ${controller.searchResults.length} 本相关书籍',
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.getOnSurfaceVariant(context),
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLoadingState(BuildContext context, I18nService i18nService) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(valueColor: AlwaysStoppedAnimation<Color>(AppColors.getPrimary(context))),
          Dimensions.verticalSpacerM,
          Text(
            i18nService.translations.processing,
            style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurfaceVariant(context)),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState(BuildContext context, I18nService i18nService) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.search_off_rounded,
            size: 64,
            color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.3),
          ),
          Dimensions.verticalSpacerM,
          Text(
            i18nService.translations.emptySearch,
            style: AppTypography.bodyLarge.copyWith(color: AppColors.getOnSurfaceVariant(context)),
          ),
          Dimensions.verticalSpacerS,
          Text(
            '请尝试使用更具体的关键词',
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.7),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInitialState(BuildContext context, I18nService i18nService) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.menu_book_rounded, size: 64, color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.3)),
          Dimensions.verticalSpacerM,
          Text('搜索您想添加的书籍', style: AppTypography.bodyLarge.copyWith(color: AppColors.getOnSurfaceVariant(context))),
          Dimensions.verticalSpacerS,
          Text(
            '输入书名、作者或关键词进行搜索',
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.7),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildResultsList(BuildContext context, I18nService i18nService) {
    return ListView.builder(
      padding: EdgeInsets.fromLTRB(
        Dimensions.paddingPage.left,
        Dimensions.spacingM,
        Dimensions.paddingPage.right,
        Dimensions.paddingPage.bottom,
      ),
      itemCount: controller.searchResults.length,
      itemBuilder: (context, index) {
        final result = controller.searchResults[index];
        return _buildResultCard(context, result, i18nService);
      },
    );
  }

  Widget _buildResultCard(BuildContext context, BookSearchResult result, I18nService i18nService) {
    return Card(
      margin: EdgeInsets.only(bottom: Dimensions.spacingM),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        side: BorderSide(color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.1), width: 1),
      ),
      child: InkWell(
        onTap: () => controller.selectBook(result),
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
                          color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(Dimensions.radiusS),
                        ),
                        child: Icon(
                          Icons.book,
                          size: 40,
                          color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.3),
                        ),
                      );
                    },
                  ),
                ),
                const SizedBox(width: 12),
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
                          const SizedBox(width: 12),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                            decoration: BoxDecoration(
                              color: AppColors.getPrimary(context).withValues(alpha: 0.1),
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
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        Icon(
                          Icons.person_outline,
                          size: 14,
                          color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.7),
                        ),
                        const SizedBox(width: 6),
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
                      const SizedBox(height: 12),
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
                    // 出版信息
                    if (result.publishYear.isNotEmpty || result.isbn.isNotEmpty) ...[
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 16,
                        runSpacing: 8,
                        children: [
                          if (result.publishYear.isNotEmpty)
                            Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.calendar_today_outlined,
                                  size: 14,
                                  color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.6),
                                ),
                                const SizedBox(width: 4),
                                Text(
                                  result.publishYear,
                                  style: AppTypography.labelSmall.copyWith(
                                    color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.8),
                                  ),
                                ),
                              ],
                            ),
                          if (result.isbn.isNotEmpty)
                            Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.book_outlined,
                                  size: 14,
                                  color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.6),
                                ),
                                const SizedBox(width: 4),
                                Text(
                                  'ISBN: ${result.isbn}',
                                  style: AppTypography.labelSmall.copyWith(
                                    color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.8),
                                  ),
                                ),
                              ],
                            ),
                        ],
                      ),
                    ],
                    // 添加提示
                    const SizedBox(height: 12),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        Icon(Icons.add_circle_outline, size: 16, color: AppColors.getPrimary(context)),
                        const SizedBox(width: 4),
                        Text(
                          '点击添加到书架',
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
