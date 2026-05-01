import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/styles.dart';

/// 按钮类型枚举
enum AppButtonType {
  /// 主要按钮 - 填充背景色
  primary,

  /// 次要按钮 - 使用轮廓样式
  secondary,
}

/// 应用按钮组件
///
/// 一个遵循应用设计规范的按钮组件，支持主要和次要风格
///
/// 使用示例:
/// ```dart
/// AppButton(
///   title: '保存',
///   type: AppButtonType.primary,
///   onPressed: () => print('按钮被点击'),
/// )
/// ```
class AppButton extends StatelessWidget {
  /// 按钮标题
  final String title;

  /// 按钮类型
  final AppButtonType type;

  /// 按钮点击回调
  final VoidCallback? onPressed;

  /// 按钮高度，默认使用标准按钮高度
  final double? height;

  /// 按钮图标，可选
  final IconData? icon;

  /// 按钮是否禁用
  final bool isDisabled;

  /// 创建一个应用按钮
  ///
  /// [title] 按钮显示的文本
  /// [type] 按钮类型，主要或次要
  /// [onPressed] 按钮点击回调
  /// [height] 可选的按钮高度
  /// [icon] 可选的按钮图标
  /// [isDisabled] 按钮是否禁用，默认为false
  const AppButton({
    super.key,
    required this.title,
    required this.type,
    required this.onPressed,
    this.height,
    this.icon,
    this.isDisabled = false,
  });
  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: height ?? Dimensions.buttonHeight,
      child: type == AppButtonType.primary
          ? _buildPrimaryButton(context)
          : _buildSecondaryButton(context),
    );
  }

  /// 构建主要按钮
  Widget _buildPrimaryButton(BuildContext context) {
    return ElevatedButton(
      onPressed: isDisabled ? null : onPressed,
      style: ElevatedButton.styleFrom(
        backgroundColor: Theme.of(context).colorScheme.primary,
        foregroundColor: Theme.of(context).colorScheme.onPrimary,
        padding: Dimensions.paddingHorizontalM,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusM),
        ),
        disabledBackgroundColor: Theme.of(
          context,
        ).colorScheme.surfaceContainerHighest,
        disabledForegroundColor: Theme.of(context).colorScheme.onSurfaceVariant,
        elevation: 0,
      ),
      child: _buildButtonContent(context),
    );
  }

  /// 构建次要按钮
  Widget _buildSecondaryButton(BuildContext context) {
    return OutlinedButton(
      onPressed: isDisabled ? null : onPressed,
      style: OutlinedButton.styleFrom(
        foregroundColor: Theme.of(context).colorScheme.primary,
        side: BorderSide(
          color: isDisabled
              ? Theme.of(context).colorScheme.outline.withValues(alpha: 0.3)
              : Theme.of(context).colorScheme.primary,
          width: 1.5,
        ),
        padding: Dimensions.paddingHorizontalM,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusM),
        ),
        disabledForegroundColor: Theme.of(context).colorScheme.onSurfaceVariant,
      ),
      child: _buildButtonContent(context),
    );
  }

  /// 构建按钮内容
  Widget _buildButtonContent(BuildContext context) {
    if (icon != null) {
      return Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: Dimensions.iconSizeS),
          Dimensions.horizontalSpacerS,
          Text(title, style: AppTypography.labelLarge),
        ],
      );
    }
    return Text(title, style: AppTypography.labelLarge);
  }
}
