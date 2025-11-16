import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/extensions/i18n_extension.dart';

/// 通用空状态提示组件
///
/// 一个可定制的空状态提示组件，支持以下特性：
/// - 自定义图标
/// - 主标题和副标题（支持国际化）
/// - 可选的操作按钮
/// - 自动适应主题样式
/// - 统一的视觉样式
///
/// 使用示例:
/// ```dart
/// EmptyStateWidget(
///   icon: Icons.inbox,
///   titleKey: 'component.empty_no_data',
///   subtitleKey: 'component.empty_add_content',
///   action: ElevatedButton(
///     onPressed: () => controller.addItem(),
///     child: Text('component.button_add'.t),
///   ),
/// )
/// ```
class EmptyStateWidget extends StatelessWidget {
  /// 空状态图标
  final IconData icon;

  /// 主标题文本（直接使用文本）
  final String? title;

  /// 主标题国际化key
  final String? titleKey;

  /// 副标题文本（直接使用文本）
  final String? subtitle;

  /// 副标题国际化key
  final String? subtitleKey;

  /// 操作按钮
  final Widget? action;

  /// 图标大小
  final double? iconSize;

  /// 是否使用紧凑布局
  final bool compact;

  const EmptyStateWidget({
    super.key,
    required this.icon,
    this.title,
    this.titleKey,
    this.subtitle,
    this.subtitleKey,
    this.action,
    this.iconSize,
    this.compact = false,
  });

  /// 创建一个标准空状态组件
  factory EmptyStateWidget.standard({
    required IconData icon,
    required String titleKey,
    String? subtitleKey,
    Widget? action,
    bool compact = false,
  }) {
    return EmptyStateWidget(
      icon: icon,
      titleKey: titleKey,
      subtitleKey: subtitleKey,
      action: action,
      compact: compact,
    );
  }

  /// 获取主标题文本
  String get _titleText {
    if (title != null) return title!;
    if (titleKey != null) return titleKey!.t;
    return 'component.empty_no_data'.t;
  }

  /// 获取副标题文本
  String? get _subtitleText {
    if (subtitle != null) return subtitle;
    if (subtitleKey != null) return subtitleKey!.t;
    return null;
  }
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: compact
          ? Dimensions.paddingCard
          : Dimensions.paddingPage,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            _buildIcon(context),
            Dimensions.verticalSpacerM,
            _buildTitle(context),
            if (_subtitleText != null) _buildSubtitle(context),
            if (action != null) _buildAction(),
          ],
        ),
      ),
    );
  }

  /// 构建图标
  Widget _buildIcon(BuildContext context) {
    return Icon(
      icon,
      size: iconSize ?? (compact ? Dimensions.iconSizeXxl : 64),
      color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.5),
    );
  }

  /// 构建主标题
  Widget _buildTitle(BuildContext context) {
    return Text(
      _titleText,
      style: compact
        ? AppTypography.titleMedium
        : AppTypography.titleLarge,
      textAlign: TextAlign.center,
    );
  }

  /// 构建副标题
  Widget _buildSubtitle(BuildContext context) {
    return Column(
      children: [
        Dimensions.verticalSpacerS,
        Text(
          _subtitleText!,
          style: compact
            ? AppTypography.bodySmall
            : AppTypography.bodyMedium,
          textAlign: TextAlign.center,
        ),
      ],
    );
  }

  /// 构建操作按钮
  Widget _buildAction() {
    return Column(
      children: [
        Dimensions.verticalSpacerL,
        action!,
      ],
    );
  }
}