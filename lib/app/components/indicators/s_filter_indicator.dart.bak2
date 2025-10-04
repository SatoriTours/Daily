import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/app_theme.dart';

/// 通用过滤指示器组件
/// 用于展示当前已应用的过滤条件，并提供清除入口
class SFilterIndicator extends StatelessWidget {
  final String title;
  final VoidCallback onClear;

  /// 前缀文案，例如：已过滤:
  final String prefix;

  /// 自定义清除按钮文案
  final String clearText;

  /// 外边距
  final EdgeInsetsGeometry? margin;

  /// 内边距
  final EdgeInsetsGeometry? padding;

  const SFilterIndicator({
    super.key,
    required this.title,
    required this.onClear,
    this.prefix = '已过滤: ',
    this.clearText = '清除',
    this.margin = const EdgeInsets.fromLTRB(12, 8, 12, 8),
    this.padding = const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Container(
      margin: margin,
      padding: padding,
      decoration: BoxDecoration(color: colorScheme.surfaceContainerHighest, borderRadius: BorderRadius.circular(8)),
      child: Row(
        children: [
          Expanded(
            child: Text(
              '$prefix$title',
              style: textTheme.labelMedium?.copyWith(color: colorScheme.onSurface),
              overflow: TextOverflow.ellipsis,
              maxLines: 1,
            ),
          ),
          InkWell(
            onTap: onClear,
            borderRadius: BorderRadius.circular(4),
            child: Padding(
              padding: const EdgeInsets.all(4),
              child: Text(clearText, style: textTheme.labelMedium?.copyWith(color: colorScheme.primary)),
            ),
          ),
        ],
      ),
    );
  }
}
