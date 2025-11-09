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
    return Scaffold(
      appBar: _buildAppBar(context, i18nService),
      body: _buildBody(context, i18nService),
    );
  }

  PreferredSizeWidget _buildAppBar(BuildContext context, I18nService i18nService) {
    return AppBar(
      title: Text(i18nService.translations.search, style: AppTypography.appBarTitle),
      elevation: 0,
    );
  }

  Widget _buildBody(BuildContext context, I18nService i18nService) {
    return SafeArea(
      child: Column(
        children: [
          _buildSearchBar(context, i18nService),
          Expanded(
            child: _buildSearchResults(context, i18nService),
          ),
        ],
      ),
    );
  }

  Widget _buildSearchBar(BuildContext context, I18nService i18nService) {
    return Container(
      padding: Dimensions.paddingPage,
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: controller.searchController,
              autofocus: true,
              decoration: InputStyles.getSearchDecoration(
                context,
                hintText: i18nService.translations.hintTitle,
              ),
              onSubmitted: (value) {
                if (value.trim().isNotEmpty) {
                  controller.searchBooks(value.trim());
                }
              },
            ),
          ),
          Dimensions.horizontalSpacerS,
          Obx(
            () => controller.isLoading.value
                ? Container(
                    width: 48,
                    height: 48,
                    padding: const EdgeInsets.all(12),
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      valueColor: AlwaysStoppedAnimation<Color>(AppColors.getPrimary(context)),
                    ),
                  )
                : IconButton(
                    onPressed: () {
                      final text = controller.searchController.text.trim();
                      if (text.isNotEmpty) {
                        controller.searchBooks(text);
                      }
                    },
                    icon: const Icon(Icons.search),
                    style: ButtonStyles.getIconButtonStyle(context),
                  ),
          ),
        ],
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

      return _buildResultsList(context, i18nService);
    });
  }

  Widget _buildLoadingState(BuildContext context, I18nService i18nService) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(
            valueColor: AlwaysStoppedAnimation<Color>(AppColors.getPrimary(context)),
          ),
          Dimensions.verticalSpacerM,
          Text(
            i18nService.translations.processing,
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.getOnSurfaceVariant(context),
            ),
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
            style: AppTypography.bodyLarge.copyWith(
              color: AppColors.getOnSurfaceVariant(context),
            ),
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
          Icon(
            Icons.menu_book_rounded,
            size: 64,
            color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.3),
          ),
          Dimensions.verticalSpacerM,
          Text(
            '搜索您想添加的书籍',
            style: AppTypography.bodyLarge.copyWith(
              color: AppColors.getOnSurfaceVariant(context),
            ),
          ),
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
      padding: EdgeInsets.symmetric(horizontal: Dimensions.paddingPage.horizontal),
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
      child: InkWell(
        onTap: () => controller.selectBook(result),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        child: Padding(
          padding: Dimensions.paddingCard,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          result.title,
                          style: AppTypography.titleMedium.copyWith(
                            color: AppColors.getOnSurface(context),
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        Dimensions.verticalSpacerS,
                        Text(
                          result.author,
                          style: AppTypography.bodyMedium.copyWith(
                            color: AppColors.getOnSurfaceVariant(context),
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (result.category.isNotEmpty) ...[
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: AppColors.getPrimary(context).withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(Dimensions.radiusS),
                      ),
                      child: Text(
                        result.category,
                        style: AppTypography.labelSmall.copyWith(
                          color: AppColors.getPrimary(context),
                        ),
                      ),
                    ),
                  ],
                ],
              ),
              if (result.introduction.isNotEmpty) ...[
                Dimensions.verticalSpacerM,
                Text(
                  result.introduction,
                  style: AppTypography.bodySmall.copyWith(
                    color: AppColors.getOnSurfaceVariant(context),
                    height: 1.4,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
              if (result.publishYear.isNotEmpty || result.isbn.isNotEmpty) ...[
                Dimensions.verticalSpacerS,
                Row(
                  children: [
                    if (result.publishYear.isNotEmpty) ...[
                      Icon(
                        Icons.calendar_today_rounded,
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
                    if (result.publishYear.isNotEmpty && result.isbn.isNotEmpty) ...[
                      const SizedBox(width: Dimensions.spacingM),
                    ],
                    if (result.isbn.isNotEmpty) ...[
                      Icon(
                        Icons.book_rounded,
                        size: 14,
                        color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.6),
                      ),
                      const SizedBox(width: 4),
                      Expanded(
                        child: Text(
                          'ISBN: ${result.isbn}',
                          style: AppTypography.labelSmall.copyWith(
                            color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.8),
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}