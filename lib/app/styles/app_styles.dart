import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/dimensions.dart';
import 'package:daily_satori/app/styles/app_theme.dart';

/// 应用样式工具类
/// 提供统一的样式生成方法，用于创建卡片、容器、阴影等
class AppStyles {
  // 私有构造函数，防止实例化
  AppStyles._();

  /// 获取卡片装饰
  static BoxDecoration cardDecoration(BuildContext context, {double? radius}) {
    final colorScheme = AppTheme.getColorScheme(context);
    final isDark = AppTheme.isDarkMode(context);

    return BoxDecoration(
      color: colorScheme.surface,
      borderRadius: BorderRadius.circular(radius ?? Dimensions.radiusM),
      boxShadow: [cardShadow(context)],
    );
  }

  /// 获取卡片阴影
  static BoxShadow cardShadow(BuildContext context) {
    final isDark = AppTheme.isDarkMode(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return BoxShadow(
      color: colorScheme.shadow.withOpacity(isDark ? 0.3 : 0.1),
      blurRadius: isDark ? 4 : 6,
      offset: const Offset(0, 2),
    );
  }

  /// 获取浅色容器装饰
  static BoxDecoration lightContainerDecoration(BuildContext context, {double? radius}) {
    final colorScheme = AppTheme.getColorScheme(context);
    final isDark = AppTheme.isDarkMode(context);

    return BoxDecoration(
      color: isDark ? colorScheme.surfaceVariant : colorScheme.surface,
      borderRadius: BorderRadius.circular(radius ?? Dimensions.radiusS),
    );
  }

  /// 获取突出容器装饰（使用主色调）
  static BoxDecoration primaryContainerDecoration(BuildContext context, {double? radius}) {
    final colorScheme = AppTheme.getColorScheme(context);

    return BoxDecoration(
      color: colorScheme.primaryContainer,
      borderRadius: BorderRadius.circular(radius ?? Dimensions.radiusS),
    );
  }

  /// 获取输入框边框装饰
  static InputDecoration inputDecoration(
    BuildContext context, {
    String? hintText,
    String? labelText,
    Widget? prefixIcon,
    Widget? suffixIcon,
    String? helperText,
  }) {
    final colorScheme = AppTheme.getColorScheme(context);

    return InputDecoration(
      hintText: hintText,
      labelText: labelText,
      prefixIcon: prefixIcon,
      suffixIcon: suffixIcon,
      helperText: helperText,
      contentPadding: Dimensions.paddingForm,
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(Dimensions.radiusS)),
    );
  }

  /// 获取搜索框装饰
  static InputDecoration searchInputDecoration(
    BuildContext context, {
    String hintText = '搜索',
    VoidCallback? onClear,
    TextEditingController? controller,
    Color? fillColor,
  }) {
    final colorScheme = AppTheme.getColorScheme(context);
    final isDark = AppTheme.isDarkMode(context);

    return InputDecoration(
      hintText: hintText,
      hintStyle: TextStyle(fontSize: 14, color: colorScheme.onSurfaceVariant),
      prefixIcon: Icon(Icons.search, size: Dimensions.iconSizeS, color: colorScheme.onSurfaceVariant),
      suffixIcon:
          controller != null && controller.text.isNotEmpty
              ? IconButton(
                icon: Icon(Icons.clear, size: Dimensions.iconSizeS, color: colorScheme.onSurfaceVariant),
                onPressed: () {
                  controller.clear();
                  if (onClear != null) onClear();
                },
              )
              : null,
      filled: true,
      fillColor: fillColor ?? (isDark ? colorScheme.surfaceVariant : colorScheme.surface),
      contentPadding: Dimensions.paddingSearchBar,
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(Dimensions.radiusXl), borderSide: BorderSide.none),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusXl),
        borderSide: BorderSide.none,
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusXl),
        borderSide: BorderSide(color: colorScheme.primary, width: 1),
      ),
    );
  }

  /// 获取图标样式
  static Icon getIcon(IconData icon, BuildContext context, {double? size, Color? color, bool primary = false}) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Icon(
      icon,
      size: size ?? Dimensions.iconSizeM,
      color: color ?? (primary ? colorScheme.primary : colorScheme.onSurface),
    );
  }

  /// 获取标签样式
  static BoxDecoration chipDecoration(BuildContext context, {bool isSelected = false}) {
    final colorScheme = AppTheme.getColorScheme(context);

    return BoxDecoration(
      color: isSelected ? colorScheme.primary : colorScheme.surfaceVariant,
      borderRadius: BorderRadius.circular(Dimensions.radiusCircular),
    );
  }

  /// 获取列表项装饰
  static BoxDecoration listItemDecoration(BuildContext context, {bool isSelected = false}) {
    final colorScheme = AppTheme.getColorScheme(context);

    return BoxDecoration(
      color: isSelected ? colorScheme.primaryContainer : Colors.transparent,
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
    );
  }

  /// 获取分隔线
  static Widget divider(BuildContext context, {double? indent, double? endIndent}) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Divider(
      height: Dimensions.dividerHeight,
      thickness: Dimensions.dividerHeight,
      color: colorScheme.outline.withOpacity(0.5),
      indent: indent ?? Dimensions.dividerIndent,
      endIndent: endIndent ?? Dimensions.dividerIndent,
    );
  }

  /// 获取空状态样式
  static Widget emptyState(
    BuildContext context, {
    IconData icon = Icons.inbox_outlined,
    String message = '暂无数据',
    VoidCallback? onRetry,
  }) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Center(
      child: Padding(
        padding: Dimensions.paddingM,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: Dimensions.iconSizePlaceholder, color: colorScheme.onSurfaceVariant),
            Dimensions.verticalSpacerM,
            Text(message, style: textTheme.bodyMedium?.copyWith(color: colorScheme.onSurfaceVariant)),
            if (onRetry != null) ...[
              Dimensions.verticalSpacerM,
              TextButton.icon(onPressed: onRetry, icon: Icon(Icons.refresh), label: Text('重试')),
            ],
          ],
        ),
      ),
    );
  }

  /// 获取错误状态样式
  static Widget errorState(BuildContext context, {String message = '出错了', VoidCallback? onRetry}) {
    return emptyState(context, icon: Icons.error_outline, message: message, onRetry: onRetry);
  }

  /// 获取加载中状态样式
  static Widget loadingState(BuildContext context, {String message = '加载中...'}) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const CircularProgressIndicator(),
          Dimensions.verticalSpacerM,
          Text(message, style: textTheme.bodyMedium?.copyWith(color: colorScheme.onSurfaceVariant)),
        ],
      ),
    );
  }
}
