import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/styles.dart';

/// 自定义标签组件
///
/// 一个可点击的标签组件，支持以下特性：
/// - 选中/未选中状态
/// - 可选的图标
/// - 自动适应主题色
/// - 点击事件处理
///
/// 使用示例:
/// ```dart
/// CustomChip(
///   label: '标签',
///   icon: Icons.tag,
///   isSelected: true,
///   onTap: () => print('标签被点击'),
/// )
/// ```
class CustomChip extends StatelessWidget {
  /// 标签文本
  final String label;

  /// 可选的标签图标
  final IconData? icon;

  /// 标签点击回调
  final VoidCallback? onTap;

  /// 是否处于选中状态
  final bool isSelected;

  /// 创建一个自定义标签
  ///
  /// [label] 标签显示的文本
  /// [icon] 可选的标签图标
  /// [onTap] 标签点击回调
  /// [isSelected] 是否处于选中状态，默认为 false
  const CustomChip({
    super.key,
    required this.label,
    this.icon,
    this.onTap,
    this.isSelected = false,
  });
  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Chip(
        backgroundColor: _getBackgroundColor(context),
        avatar: _buildIcon(context),
        label: _buildLabel(context),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      ),
    );
  }

  /// 获取背景颜色
  Color _getBackgroundColor(BuildContext context) {
    return isSelected
        ? AppColors.getPrimary(context)
        : AppColors.getPrimary(context).withAlpha(26);
  }

  /// 构建图标
  Widget? _buildIcon(BuildContext context) {
    if (icon == null) return null;
    return Icon(
      icon,
      size: 16,
      color: isSelected ? Colors.white : AppColors.getPrimary(context),
    );
  }

  /// 构建标签文本
  Widget _buildLabel(BuildContext context) {
    return Text(
      label,
      style: AppTypography.labelSmall.copyWith(
        color: isSelected ? Colors.white : AppColors.getPrimary(context),
      ),
    );
  }
}
