import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/styles.dart';
import 'package:intl/intl.dart';

// ...existing imports...
/// 观点卡片组件
class ViewpointCard extends StatelessWidget {
  final BookViewpointModel viewpoint;
  final BookModel? book;
  const ViewpointCard({super.key, required this.viewpoint, required this.book});
  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: Dimensions.borderRadiusM),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          Dimensions.spacingM,
          Dimensions.spacingS,
          Dimensions.spacingM,
          Dimensions.spacingM,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Dimensions.verticalSpacerL,
            _buildTitle(context),
            Dimensions.verticalSpacerS,
            _buildViewpointBookInfo(context, book),
            Dimensions.verticalSpacerL,
            _buildContent(context),
            if (viewpoint.example.isNotEmpty) ...[Dimensions.verticalSpacerL, _buildExample(context)],
            Dimensions.verticalSpacerL,
            _buildFooter(context, book),
          ],
        ),
      ),
    );
  }

  /// 构建标题
  Widget _buildTitle(BuildContext context) {
    return SelectableText(
      viewpoint.title,
      style: AppTypography.headingMedium.copyWith(
        fontWeight: FontWeight.bold,
        height: 1.3,
        color: AppColors.getOnSurface(context),
      ),
    );
  }

  /// 构建内容
  Widget _buildContent(BuildContext context) {
    return SelectableText(
      viewpoint.content,
      style: AppTypography.bodyLarge.copyWith(
        height: 1.8,
        letterSpacing: 0.5,
        color: AppColors.getOnSurface(context).withValues(alpha: 0.9),
      ),
    );
  }

  /// 构建案例
  Widget _buildExample(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: Dimensions.spacingM, horizontal: Dimensions.spacingXs),
      decoration: BoxDecoration(
        color: AppColors.getSurfaceVariant(context).withValues(alpha: 0.3),
        borderRadius: Dimensions.borderRadiusM,
        border: Border.all(color: AppColors.getOutline(context).withValues(alpha: 0.1)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.lightbulb_outline, size: Dimensions.iconSizeS, color: AppColors.getPrimary(context)),
              Dimensions.horizontalSpacerS,
              Text(
                '书籍案例',
                style: AppTypography.labelLarge.copyWith(
                  color: AppColors.getPrimary(context),
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
          Dimensions.verticalSpacerS,
          SelectableText(
            viewpoint.example,
            style: AppTypography.bodyLarge.copyWith(
              height: 1.8,
              letterSpacing: 0.5,
              color: AppColors.getOnSurface(context).withValues(alpha: 0.8),
            ),
          ),
        ],
      ),
    );
  }

  /// 构建底部
  Widget _buildFooter(BuildContext context, BookModel? book) {
    final formattedDate = DateFormat('yyyy-MM-dd').format(viewpoint.createdAt);
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        Expanded(
          child: Row(
            children: [
              const Icon(Icons.calendar_today, size: Dimensions.iconSizeXs - 2, color: Colors.grey),
              const SizedBox(width: Dimensions.spacingXs + 2),
              Text(formattedDate, style: AppTypography.labelSmall.copyWith(color: Colors.grey)),
            ],
          ),
        ),
      ],
    );
  }

  /// 构建观点对应的书籍的名字和作者
  Widget _buildViewpointBookInfo(BuildContext context, BookModel? book) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        const Icon(Icons.menu_book, size: Dimensions.iconSizeXs - 2, color: Colors.grey),
        const SizedBox(width: Dimensions.spacingXs + 2),
        Text(
          book != null ? '《${book.title}》· ${book.author}' : '未知书籍',
          style: AppTypography.bodySmall.copyWith(color: Colors.grey),
        ),
      ],
    );
  }

  // 已移除底部“记感想”入口，改为在页面顶部/悬浮层提供统一入口
}
