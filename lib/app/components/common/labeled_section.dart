import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/styles/dimensions.dart';
import 'package:daily_satori/app/styles/font_style.dart';

/// 带标签的内容区域组件
///
/// 一个通用组件，包含图标、标签以及自定义内容
/// 适用于需要在表单字段或内容块前显示标签的情况
///
/// 使用示例:
/// ```dart
/// LabeledSection(
///   icon: Icons.article_outlined,
///   label: '文章标题',
///   content: Text('这是文章标题内容'),
/// )
/// ```
class LabeledSection extends StatelessWidget {
  /// 图标数据
  final IconData icon;

  /// 标签文本
  final String label;

  /// 内容组件
  final Widget child;

  /// 是否显示卡片背景
  final bool showCardBackground;

  /// 标签文本样式
  final TextStyle? labelStyle;

  /// 图标颜色，默认使用主题色
  final Color? iconColor;

  /// 图标大小
  final double? iconSize;

  /// 内容区域外边距
  final EdgeInsetsGeometry? contentPadding;

  /// 创建一个带标签的内容区域
  ///
  /// [icon] 显示的图标
  /// [label] 显示的标签文本
  /// [child] 主体内容组件
  /// [showCardBackground] 是否显示卡片背景，默认为false
  /// [labelStyle] 标签文本样式，默认使用次要文本样式
  /// [iconColor] 图标颜色，默认使用主题色
  /// [iconSize] 图标大小，默认使用小图标尺寸
  /// [contentPadding] 内容区域外边距
  const LabeledSection({
    super.key,
    required this.icon,
    required this.label,
    required this.child,
    this.showCardBackground = false,
    this.labelStyle,
    this.iconColor,
    this.iconSize,
    this.contentPadding,
  });

  @override
  Widget build(BuildContext context) {
    final mainContent = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildHeader(context),
        if (contentPadding != null) Padding(padding: contentPadding!, child: child) else child,
      ],
    );

    if (!showCardBackground) {
      return mainContent;
    }

    // 如果需要卡片背景，使用Container包装内容
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Container(
      padding: Dimensions.paddingCard,
      decoration: BoxDecoration(
        color:
            isDark
                ? theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.2)
                : theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
      ),
      child: mainContent,
    );
  }

  /// 构建头部（图标+标签）
  Widget _buildHeader(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 4, bottom: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Icon(icon, size: iconSize ?? Dimensions.iconSizeS, color: iconColor ?? AppColors.primary(context)),
          Dimensions.horizontalSpacerS,
          Text(
            label,
            style:
                labelStyle ??
                MyFontStyle.bodyLarge.copyWith(color: AppColors.textSecondary(context), fontWeight: FontWeight.w500),
          ),
        ],
      ),
    );
  }
}
