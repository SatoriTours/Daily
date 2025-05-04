import 'package:flutter/material.dart';

/// 应用阴影样式常量
/// 提供统一的阴影相关样式定义，遵循 shadcn/ui 的设计风格
class AppShadows {
  // 私有构造函数，防止实例化
  AppShadows._();

  // 浅色模式阴影
  /// 极小阴影 - 用于微妙的高度表现
  static List<BoxShadow> xsLight = [
    BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 1, offset: const Offset(0, 1)),
  ];

  /// 小阴影 - 用于UI元素的基本高度
  static List<BoxShadow> sLight = [
    BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 2, offset: const Offset(0, 1)),
    BoxShadow(color: Colors.black.withValues(alpha: 0.1), blurRadius: 3, offset: const Offset(0, 1)),
  ];

  /// 中等阴影 - 用于卡片、按钮等
  static List<BoxShadow> mLight = [
    BoxShadow(color: Colors.black.withValues(alpha: 0.07), blurRadius: 6, offset: const Offset(0, 2)),
    BoxShadow(color: Colors.black.withValues(alpha: 0.13), blurRadius: 4, offset: const Offset(0, 3)),
  ];

  /// 大阴影 - 用于弹出框、对话框等
  static List<BoxShadow> lLight = [
    BoxShadow(color: Colors.black.withValues(alpha: 0.07), blurRadius: 8, offset: const Offset(0, 4)),
    BoxShadow(color: Colors.black.withValues(alpha: 0.12), blurRadius: 10, offset: const Offset(0, 8)),
  ];

  /// 特大阴影 - 用于模态框、抽屉等
  static List<BoxShadow> xlLight = [
    BoxShadow(color: Colors.black.withValues(alpha: 0.08), blurRadius: 12, offset: const Offset(0, 8)),
    BoxShadow(color: Colors.black.withValues(alpha: 0.15), blurRadius: 25, offset: const Offset(0, 15)),
  ];

  // 暗色模式阴影
  /// 极小阴影 - 用于微妙的高度表现（暗色模式）
  static List<BoxShadow> xsDark = [
    BoxShadow(color: Colors.black.withValues(alpha: 0.15), blurRadius: 1, offset: const Offset(0, 1)),
  ];

  /// 小阴影 - 用于UI元素的基本高度（暗色模式）
  static List<BoxShadow> sDark = [
    BoxShadow(color: Colors.black.withValues(alpha: 0.2), blurRadius: 2, offset: const Offset(0, 1)),
    BoxShadow(color: Colors.black.withValues(alpha: 0.25), blurRadius: 3, offset: const Offset(0, 1)),
  ];

  /// 中等阴影 - 用于卡片、按钮等（暗色模式）
  static List<BoxShadow> mDark = [
    BoxShadow(color: Colors.black.withValues(alpha: 0.25), blurRadius: 6, offset: const Offset(0, 2)),
    BoxShadow(color: Colors.black.withValues(alpha: 0.3), blurRadius: 4, offset: const Offset(0, 3)),
  ];

  /// 大阴影 - 用于弹出框、对话框等（暗色模式）
  static List<BoxShadow> lDark = [
    BoxShadow(color: Colors.black.withValues(alpha: 0.3), blurRadius: 8, offset: const Offset(0, 4)),
    BoxShadow(color: Colors.black.withValues(alpha: 0.35), blurRadius: 10, offset: const Offset(0, 8)),
  ];

  /// 特大阴影 - 用于模态框、抽屉等（暗色模式）
  static List<BoxShadow> xlDark = [
    BoxShadow(color: Colors.black.withValues(alpha: 0.35), blurRadius: 12, offset: const Offset(0, 8)),
    BoxShadow(color: Colors.black.withValues(alpha: 0.4), blurRadius: 25, offset: const Offset(0, 15)),
  ];

  /// 获取基于主题的阴影 - 极小
  static List<BoxShadow> getXsShadow(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? xsDark : xsLight;
  }

  /// 获取基于主题的阴影 - 小
  static List<BoxShadow> getSShadow(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? sDark : sLight;
  }

  /// 获取基于主题的阴影 - 中等
  static List<BoxShadow> getMShadow(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? mDark : mLight;
  }

  /// 获取基于主题的阴影 - 大
  static List<BoxShadow> getLShadow(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? lDark : lLight;
  }

  /// 获取基于主题的阴影 - 特大
  static List<BoxShadow> getXlShadow(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return isDark ? xlDark : xlLight;
  }

  /// 获取卡片阴影
  static List<BoxShadow> getCardShadow(BuildContext context) {
    return getMShadow(context);
  }

  /// 获取按钮阴影
  static List<BoxShadow> getButtonShadow(BuildContext context) {
    return getSShadow(context);
  }

  /// 获取对话框阴影
  static List<BoxShadow> getDialogShadow(BuildContext context) {
    return getLShadow(context);
  }

  /// 获取底部表单阴影
  static List<BoxShadow> getBottomSheetShadow(BuildContext context) {
    return getLShadow(context);
  }

  /// 获取浮动按钮阴影
  static List<BoxShadow> getFabShadow(BuildContext context) {
    return getMShadow(context);
  }

  /// 获取弹出菜单阴影
  static List<BoxShadow> getPopupShadow(BuildContext context) {
    return getLShadow(context);
  }
}
