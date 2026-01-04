import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/styles.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

/// 过滤指示器组件
///
/// 用于展示当前已应用的过滤条件，并提供清除入口。
/// 支持国际化和统一样式系统。
///
/// 使用示例:
/// ```dart
/// FilterIndicator(
///   title: '技术',
///   onClear: () => controller.clearFilter(),
/// )
/// ```
class FilterIndicator extends StatelessWidget {
  /// 过滤条件标题
  final String title;

  /// 清除回调
  final VoidCallback onClear;

  /// 前缀文案国际化key
  final String? prefixKey;

  /// 清除按钮文案国际化key
  final String? clearTextKey;

  /// 外边距
  final EdgeInsetsGeometry? margin;

  /// 内边距
  final EdgeInsetsGeometry? padding;

  /// 是否显示图标
  final bool showIcon;

  const FilterIndicator({
    super.key,
    required this.title,
    required this.onClear,
    this.prefixKey,
    this.clearTextKey,
    this.margin,
    this.padding,
    this.showIcon = true,
  });

  /// 使用默认参数创建
  factory FilterIndicator.standard({required String title, required VoidCallback onClear, EdgeInsetsGeometry? margin}) {
    return FilterIndicator(
      title: title,
      onClear: onClear,
      prefixKey: 'component.filtered_indicator_prefix',
      clearTextKey: 'component.filtered_indicator_clear',
      margin: margin ?? const EdgeInsets.fromLTRB(12, 8, 12, 8),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
    );
  }

  /// 获取前缀文案
  String get _prefixText {
    if (prefixKey != null) return prefixKey!.t;
    return 'component.filtered_indicator_prefix'.t;
  }

  /// 获取清除按钮文案
  String get _clearText {
    if (clearTextKey != null) return clearTextKey!.t;
    return 'component.filtered_indicator_clear'.t;
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: margin ?? const EdgeInsets.fromLTRB(12, 8, 12, 8),
      padding: padding ?? const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: AppColors.getSurfaceContainerHighest(context),
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
      ),
      child: Row(
        children: [
          if (showIcon) ...[
            Icon(Icons.filter_alt_outlined, size: Dimensions.iconSizeS, color: AppColors.getOnSurfaceVariant(context)),
            Dimensions.horizontalSpacerS,
          ],
          Expanded(
            child: Text(
              '$_prefixText$title',
              style: AppTypography.labelMedium.copyWith(color: AppColors.getOnSurface(context)),
              overflow: TextOverflow.ellipsis,
              maxLines: 1,
            ),
          ),
          Dimensions.horizontalSpacerS,
          InkWell(
            onTap: onClear,
            borderRadius: BorderRadius.circular(Dimensions.radiusXs),
            child: Padding(
              padding: const EdgeInsets.all(4),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    _clearText,
                    style: AppTypography.labelSmall.copyWith(
                      color: AppColors.getPrimary(context),
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  if (showIcon) ...[
                    Dimensions.horizontalSpacerXs,
                    Icon(Icons.close, size: Dimensions.iconSizeXs, color: AppColors.getPrimary(context)),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// 保持向后兼容性的别名
@Deprecated('使用 FilterIndicator 替代')
class SFilterIndicator extends FilterIndicator {
  const SFilterIndicator({
    super.key,
    required super.title,
    required super.onClear,
    super.prefixKey = 'component.filtered_indicator_prefix',
    super.clearTextKey = 'component.filtered_indicator_clear',
    super.margin,
    super.padding,
  });
}
