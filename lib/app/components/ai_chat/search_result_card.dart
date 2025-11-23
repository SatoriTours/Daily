import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/modules/ai_chat/models/search_result.dart';

/// 搜索结果卡片组件
class SearchResultCard extends StatelessWidget {
  final SearchResult result;

  const SearchResultCard({super.key, required this.result});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.only(bottom: Dimensions.spacingXs),
      elevation: 0,
      color: AppColors.getSurfaceContainerHighest(context),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        side: BorderSide(color: AppColors.getOutline(context).withValues(alpha: 0.1), width: 1),
      ),
      child: InkWell(
        onTap: () => _navigateToDetail(),
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        child: Padding(
          padding: EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
          child: Row(
            children: [
              // 类型图标
              Text(result.typeIcon, style: TextStyle(fontSize: 16)),
              SizedBox(width: Dimensions.spacingS),
              // 标题
              Expanded(
                child: Text(
                  result.title,
                  style: AppTypography.bodyMedium.copyWith(
                    fontWeight: FontWeight.w500,
                    color: AppColors.getOnSurface(context),
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              // 收藏图标
              if (result.isFavorite == true) ...[
                SizedBox(width: Dimensions.spacingXs),
                Icon(Icons.favorite, size: 14, color: AppColors.getError(context)),
              ],
              // 箭头图标
              SizedBox(width: Dimensions.spacingXs),
              Icon(Icons.chevron_right, size: 18, color: AppColors.getOnSurfaceVariant(context)),
            ],
          ),
        ),
      ),
    );
  }

  /// 导航到详情页
  void _navigateToDetail() {
    switch (result.type) {
      case SearchResultType.article:
        Get.toNamed(Routes.articleDetail, arguments: result.id);
        break;
      case SearchResultType.diary:
        // 日记编辑器路由暂时未定义，可以后续添加
        Get.snackbar('提示', '日记详情功能开发中');
        break;
      case SearchResultType.book:
        // 书籍详情路由暂时未定义，可以后续添加
        Get.snackbar('提示', '书籍详情功能开发中');
        break;
    }
  }
}
