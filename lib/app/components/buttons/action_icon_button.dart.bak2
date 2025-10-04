import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/app_theme.dart';

/// 操作按钮组件
///
/// 一个简单的图标按钮组件，支持以下特性：
/// - 自定义图标
/// - 自定义颜色
/// - 自定义大小
/// - 可选的工具提示
///
/// 使用示例:
/// ```dart
/// ActionButton(
///   icon: Icons.edit,
///   onTap: () => print('编辑按钮被点击'),
///   tooltip: '编辑',
/// )
/// ```
class ActionIconButton extends StatelessWidget {
  /// 按钮图标
  final IconData icon;

  /// 按钮点击回调
  final VoidCallback onTap;

  /// 自定义颜色
  ///
  /// 如果为 null，将使用主题的 onSurfaceVariant 颜色
  final Color? color;

  /// 图标大小
  ///
  /// 默认为 16
  final double size;

  /// 工具提示文本
  ///
  /// 当鼠标悬停在按钮上时显示
  final String? tooltip;

  /// 创建一个操作按钮
  ///
  /// [icon] 按钮图标
  /// [onTap] 按钮点击回调
  /// [color] 自定义颜色，可选
  /// [size] 图标大小，默认为 16
  /// [tooltip] 工具提示文本，可选
  const ActionIconButton({
    super.key,
    required this.icon,
    required this.onTap,
    this.color,
    this.size = 16,
    this.tooltip,
  });

  @override
  Widget build(BuildContext context) {
    final button = _buildButton(context);

    if (tooltip != null) {
      return Tooltip(message: tooltip!, child: button);
    }

    return button;
  }

  /// 构建按钮
  Widget _buildButton(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        width: 28,
        height: 24,
        alignment: Alignment.center,
        child: Icon(icon, size: size, color: _getButtonColor(context)),
      ),
    );
  }

  /// 获取按钮颜色
  Color _getButtonColor(BuildContext context) {
    return color ?? AppTheme.getColorScheme(context).onSurfaceVariant.withAlpha(179);
  }
}
