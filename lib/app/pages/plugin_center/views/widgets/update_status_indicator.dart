import 'package:daily_satori/app/styles/styles.dart';
import 'package:flutter/material.dart';

/// 更新状态指示器组件
class UpdateStatusIndicator extends StatelessWidget {
  /// 正在更新的文件名
  final String updatingFileName;

  /// 构造函数
  const UpdateStatusIndicator({super.key, required this.updatingFileName});

  @override
  Widget build(BuildContext context) {
    if (updatingFileName.isEmpty) {
      return const SizedBox.shrink();
    }

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        vertical: Dimensions.spacingS + 2,
        horizontal: Dimensions.spacingM,
      ),
      decoration: BoxDecoration(
        color: AppColors.getPrimary(
          context,
        ).withValues(alpha: Opacities.extraLow),
        border: Border(
          bottom: BorderSide(
            color: AppColors.getPrimary(
              context,
            ).withValues(alpha: Opacities.low),
            width: 1,
          ),
        ),
      ),
      child: Row(
        children: [
          SizedBox(
            width: Dimensions.iconSizeS,
            height: Dimensions.iconSizeS,
            child: CircularProgressIndicator(
              strokeWidth: 2.5,
              valueColor: AlwaysStoppedAnimation<Color>(
                AppColors.getPrimary(context),
              ),
            ),
          ),
          Dimensions.horizontalSpacerM,
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '正在更新插件',
                  style: AppTypography.bodyMedium.copyWith(
                    color: AppColors.getPrimary(context),
                    fontWeight: FontWeight.w500,
                  ),
                ),
                Dimensions.verticalSpacerXs,
                Text(
                  updatingFileName,
                  style: AppTypography.bodySmall.copyWith(
                    color: AppColors.getPrimary(
                      context,
                    ).withValues(alpha: Opacities.mediumHigh),
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
