import 'package:flutter/material.dart';
import 'package:daily_satori/app/data/article/article_model.dart';
import 'package:daily_satori/app/styles/styles.dart';

/// 文章操作栏组件
///
/// 纯展示组件，通过回调函数与外部交互
class ArticleActionBar extends StatelessWidget {
  final ArticleModel articleModel;
  final bool isProcessing;
  final VoidCallback? onFavoriteToggle;
  final VoidCallback? onShare;

  const ArticleActionBar({
    super.key,
    required this.articleModel,
    this.isProcessing = false,
    this.onFavoriteToggle,
    this.onShare,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (isProcessing)
          Container(
            width: 28,
            height: Dimensions.spacingL,
            alignment: Alignment.center,
            margin: const EdgeInsets.only(right: Dimensions.spacingS),
            child: SizedBox(
              width: Dimensions.iconSizeXs,
              height: Dimensions.iconSizeXs,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                valueColor: AlwaysStoppedAnimation<Color>(colorScheme.primary),
              ),
            ),
          ),
        _buildActionButton(
          context,
          articleModel.isFavorite ? Icons.favorite : Icons.favorite_border,
          articleModel.isFavorite
              ? colorScheme.error
              : colorScheme.onSurfaceVariant.withValues(alpha: Opacities.medium),
          onFavoriteToggle,
        ),
        Dimensions.horizontalSpacerS,
        _buildActionButton(
          context,
          Icons.share,
          colorScheme.onSurfaceVariant.withValues(alpha: Opacities.medium),
          onShare,
        ),
      ],
    );
  }

  Widget _buildActionButton(BuildContext context, IconData icon, Color color, VoidCallback? onTap) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(Dimensions.radiusL),
      child: Container(
        width: 28,
        height: Dimensions.spacingL,
        alignment: Alignment.center,
        child: Icon(icon, size: Dimensions.iconSizeXs, color: color),
      ),
    );
  }
}
