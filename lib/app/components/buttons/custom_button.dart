import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';

/// 自定义按钮组件
///
/// 一个可定制的按钮组件，支持以下特性：
/// - 主要和次要样式
/// - 可选的图标
/// - 可选的全宽度显示
/// - 自动适应暗色/亮色主题
///
/// 使用示例:
/// ```dart
/// CustomButton(
///   label: '确定',
///   icon: Icons.check,
///   isPrimary: true,
///   onPressed: () => print('按钮被点击'),
/// )
/// ```
class CustomButton extends StatelessWidget {
  /// 按钮文本
  final String label;

  /// 可选的按钮图标
  final IconData? icon;

  /// 按钮点击回调
  final VoidCallback? onPressed;

  /// 是否使用主要样式
  ///
  /// true 表示使用主题色背景
  /// false 表示使用边框样式
  final bool isPrimary;

  /// 是否占满父容器宽度
  final bool isFullWidth;

  /// 创建一个自定义按钮
  ///
  /// [label] 按钮显示的文本
  /// [icon] 可选的按钮图标
  /// [onPressed] 按钮点击回调
  /// [isPrimary] 是否使用主要样式，默认为 true
  /// [isFullWidth] 是否占满父容器宽度，默认为 false
  const CustomButton({
    super.key,
    required this.label,
    this.icon,
    this.onPressed,
    this.isPrimary = true,
    this.isFullWidth = false,
  });
  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: isFullWidth ? double.infinity : null,
      child: ElevatedButton(
        onPressed: onPressed,
        style: _buildButtonStyle(context),
        child: _buildButtonContent(context),
      ),
    );
  }

  /// 构建按钮样式
  ButtonStyle _buildButtonStyle(BuildContext context) {
    if (isPrimary) {
      return ElevatedButton.styleFrom(backgroundColor: AppColors.getPrimary(context), foregroundColor: Colors.white);
    }
    return ElevatedButton.styleFrom(
      backgroundColor: Theme.of(context).brightness == Brightness.dark ? AppColors.getSurface(context) : Colors.white,
      foregroundColor: AppColors.getPrimary(context),
      side: BorderSide(color: AppColors.getPrimary(context)),
    );
  }

  /// 构建按钮内容
  Widget _buildButtonContent(BuildContext context) {
    return Row(
      mainAxisSize: isFullWidth ? MainAxisSize.max : MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (icon != null) ...[Icon(icon), const SizedBox(width: 8)],
        Text(label, style: AppTypography.labelLarge),
      ],
    );
  }
}
