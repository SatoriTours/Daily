import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/styles/font_style.dart';

/// 组件样式类
/// 提供应用中各种组件的样式定义
class ComponentStyle {
  // 私有构造函数，防止实例化
  ComponentStyle._();

  /// 获取卡片主题
  static CardTheme cardTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return CardTheme(
      color: isDark ? AppColors.cardBackgroundDark : AppColors.cardBackgroundLight,
      elevation: isDark ? 1 : 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      margin: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
      shadowColor: Colors.black.withOpacity(isDark ? 0.3 : 0.2),
      clipBehavior: Clip.antiAlias,
    );
  }

  /// 获取ElevatedButton主题
  static ElevatedButtonThemeData elevatedButtonTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: isDark ? AppColors.primaryDark : AppColors.primaryLight,
        foregroundColor: Colors.white,
        elevation: isDark ? 2 : 3,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        textStyle: MyFontStyle.buttonTextStyle,
      ),
    );
  }

  /// 获取OutlinedButton主题
  static OutlinedButtonThemeData outlinedButtonTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: isDark ? AppColors.primaryDark : AppColors.primaryLight,
        side: BorderSide(color: isDark ? AppColors.primaryDark : AppColors.primaryLight, width: 1.5),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        textStyle: MyFontStyle.buttonTextStyle.copyWith(color: isDark ? AppColors.primaryDark : AppColors.primaryLight),
      ),
    );
  }

  /// 获取TextButton主题
  static TextButtonThemeData textButtonTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: isDark ? AppColors.primaryDark : AppColors.primaryLight,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        textStyle: MyFontStyle.buttonTextStyle.copyWith(color: isDark ? AppColors.primaryDark : AppColors.primaryLight),
      ),
    );
  }

  /// 获取输入框装饰主题
  static InputDecorationTheme inputDecorationTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return InputDecorationTheme(
      filled: true,
      fillColor: isDark ? AppColors.cardBackgroundDark : Colors.grey.shade50,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: BorderSide(color: isDark ? AppColors.dividerDark : AppColors.dividerLight),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: BorderSide(color: isDark ? AppColors.dividerDark : AppColors.dividerLight),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: BorderSide(color: isDark ? AppColors.primaryDark : AppColors.primaryLight, width: 2),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: BorderSide(color: isDark ? AppColors.errorDark : AppColors.errorLight, width: 1),
      ),
      focusedErrorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: BorderSide(color: isDark ? AppColors.errorDark : AppColors.errorLight, width: 2),
      ),
      hintStyle: TextStyle(color: isDark ? AppColors.textHintDark : AppColors.textHintLight),
      helperStyle: TextStyle(color: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight),
      errorStyle: TextStyle(color: isDark ? AppColors.errorDark : AppColors.errorLight),
      labelStyle: TextStyle(color: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight),
      floatingLabelStyle: TextStyle(color: isDark ? AppColors.primaryDark : AppColors.primaryLight),
    );
  }

  /// 获取图标主题
  static IconThemeData iconTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return IconThemeData(
      color: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
      size: 24,
      opacity: 0.9,
    );
  }

  /// 获取主要图标主题
  static IconThemeData primaryIconTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return IconThemeData(color: isDark ? AppColors.primaryDark : AppColors.primaryLight, size: 24, opacity: 1.0);
  }

  /// 获取分隔线主题
  static DividerThemeData dividerTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return DividerThemeData(
      color: isDark ? AppColors.dividerDark : AppColors.dividerLight,
      thickness: 1,
      space: 16,
      indent: 0,
      endIndent: 0,
    );
  }

  /// 获取底部导航栏主题
  static BottomNavigationBarThemeData bottomNavigationBarTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return BottomNavigationBarThemeData(
      backgroundColor: isDark ? AppColors.cardBackgroundDark : Colors.white,
      selectedItemColor: isDark ? AppColors.primaryDark : AppColors.primaryLight,
      unselectedItemColor: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
      elevation: isDark ? 8 : 16,
      showSelectedLabels: true,
      showUnselectedLabels: true,
      type: BottomNavigationBarType.fixed,
      selectedLabelStyle: MyFontStyle.labelSmall,
      unselectedLabelStyle: MyFontStyle.labelSmall,
    );
  }

  /// 获取芯片主题
  static ChipThemeData chipTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return ChipThemeData(
      backgroundColor: isDark ? AppColors.primaryDark.withOpacity(0.2) : AppColors.primaryLight.withOpacity(0.1),
      disabledColor: isDark ? AppColors.dividerDark : AppColors.dividerLight,
      selectedColor: isDark ? AppColors.primaryDark : AppColors.primaryLight,
      secondarySelectedColor: isDark ? AppColors.primaryDarkVariantLight : AppColors.primaryLightVariant,
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 0),
      labelStyle: MyFontStyle.chipTextStyle.copyWith(
        color: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
      ),
      secondaryLabelStyle: MyFontStyle.chipTextStyle.copyWith(color: Colors.white),
      brightness: brightness,
      shape: StadiumBorder(),
      labelPadding: const EdgeInsets.symmetric(horizontal: 4),
    );
  }

  /// 获取浮动按钮主题
  static FloatingActionButtonThemeData floatingActionButtonTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return FloatingActionButtonThemeData(
      backgroundColor: isDark ? AppColors.primaryDark : AppColors.primaryLight,
      foregroundColor: Colors.white,
      elevation: 4,
      focusElevation: 6,
      hoverElevation: 8,
      splashColor: isDark ? AppColors.primaryDarkVariantLight : AppColors.primaryLightVariant,
      shape: const CircleBorder(),
    );
  }

  /// 获取列表磁贴主题
  static ListTileThemeData listTileTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return ListTileThemeData(
      dense: false,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      tileColor: Colors.transparent,
      selectedTileColor: isDark ? AppColors.primaryDark.withOpacity(0.2) : AppColors.primaryLight.withOpacity(0.1),
      selectedColor: isDark ? AppColors.primaryDark : AppColors.primaryLight,
      iconColor: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
      textColor: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
      style: ListTileStyle.list,
    );
  }

  /// 获取搜索栏样式
  static InputDecoration searchInputDecoration(
    BuildContext context, {
    String hintText = '搜索',
    VoidCallback? onClear,
    TextEditingController? controller,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return InputDecoration(
      hintText: hintText,
      hintStyle: TextStyle(fontSize: 14, color: AppColors.textSecondary(context)),
      prefixIcon: Icon(Icons.search, size: 20, color: AppColors.textSecondary(context)),
      suffixIcon: IconButton(
        icon: Icon(Icons.clear, size: 18, color: AppColors.textSecondary(context)),
        onPressed: () {
          if (controller != null) {
            controller.clear();
          }
          if (onClear != null) {
            onClear();
          }
        },
      ),
      filled: true,
      fillColor: isDark ? AppColors.cardBackgroundDark.withOpacity(0.8) : Colors.grey.shade100,
      contentPadding: const EdgeInsets.symmetric(vertical: 0, horizontal: 16),
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(22), borderSide: BorderSide.none),
      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(22), borderSide: BorderSide.none),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(22),
        borderSide: BorderSide(color: AppColors.primary(context), width: 1),
      ),
    );
  }

  /// 获取文章列表项样式
  static BoxDecoration articleCardDecoration(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return BoxDecoration(
      color: AppColors.cardBackground(context),
      borderRadius: BorderRadius.circular(10),
      boxShadow: [
        BoxShadow(
          color: Colors.black.withOpacity(isDark ? 0.2 : 0.1),
          blurRadius: isDark ? 4 : 8,
          offset: const Offset(0, 2),
        ),
      ],
    );
  }

  /// 获取自定义分隔线样式
  static Divider customDivider(
    BuildContext context, {
    double height = 1.0,
    double indent = 0.0,
    double endIndent = 0.0,
  }) {
    return Divider(
      height: height,
      thickness: 1,
      color: AppColors.divider(context),
      indent: indent,
      endIndent: endIndent,
    );
  }

  /// 获取图片容器样式
  static BoxDecoration imageContainerDecoration(BuildContext context) {
    return BoxDecoration(
      borderRadius: BorderRadius.circular(8),
      border: Border.all(color: AppColors.divider(context), width: 0.5),
    );
  }
}
