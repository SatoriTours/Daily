import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/styles/font_style.dart';

class MyTheme {
  // 私有构造函数,防止实例化
  MyTheme._();

  // 获取字体系列
  static String get _fontFamily => 'Lato';

  // 创建基础主题
  static ThemeData _createBaseTheme(ThemeData base, Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return base.copyWith(
      // 主色调
      primaryColor: isDark ? AppColors.primaryDark : AppColors.primaryLight,
      primaryColorLight: isDark ? AppColors.primaryDarkVariantLight : AppColors.primaryLightVariant,
      primaryColorDark: isDark ? AppColors.primaryDarkVariantDark : AppColors.primaryDarkVariant,
      colorScheme: ColorScheme.fromSwatch().copyWith(
        brightness: brightness,
        primary: isDark ? AppColors.primaryDark : AppColors.primaryLight,
        secondary: isDark ? AppColors.accentDark : AppColors.accentLight,
        surfaceContainerLowest: isDark ? AppColors.backgroundDark : AppColors.backgroundLight,
        error: isDark ? AppColors.errorDark : AppColors.errorLight,
        onPrimary: Colors.white,
        onSecondary: Colors.white,
        onSurfaceVariant: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
        onError: Colors.white,
        surface: isDark ? AppColors.cardBackgroundDark : AppColors.cardBackgroundLight,
        onSurface: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
      ),

      // 背景色
      scaffoldBackgroundColor: isDark ? AppColors.backgroundDark : AppColors.backgroundLight,

      // 文本主题
      textTheme: base.textTheme.apply(
        fontFamily: _fontFamily,
        bodyColor: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
        displayColor: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
      ),

      // AppBar 主题
      appBarTheme: AppBarTheme(
        backgroundColor: isDark ? AppColors.appBarBackgroundDark : AppColors.primaryLight,
        foregroundColor: Colors.white,
        elevation: isDark ? 1 : 2,
        centerTitle: true,
        titleTextStyle: isDark
            ? MyFontStyle.appBarTitleStyle.copyWith(color: Colors.white)
            : MyFontStyle.appBarTitleStyle,
        iconTheme: IconThemeData(color: Colors.white),
      ),

      // 卡片主题
      cardTheme: CardThemeData(
        color: isDark ? AppColors.cardBackgroundDark : AppColors.cardBackgroundLight,
        elevation: 2,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        margin: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
      ),

      // 按钮主题
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: isDark ? AppColors.primaryDark : AppColors.primaryLight,
          foregroundColor: Colors.white,
          elevation: 2,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        ),
      ),

      // 输入框主题
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: isDark ? AppColors.cardBackgroundDark : Colors.white,
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
        hintStyle: TextStyle(color: isDark ? AppColors.textHintDark : AppColors.textHintLight),
      ),

      // 图标主题
      iconTheme: IconThemeData(color: isDark ? Colors.white : AppColors.primaryLight, size: 24),

      // 分隔线主题
      dividerTheme: DividerThemeData(
        color: isDark ? AppColors.dividerDark : AppColors.dividerLight,
        thickness: 1,
        space: 16,
      ),

      // 底部导航栏主题
      bottomNavigationBarTheme: BottomNavigationBarThemeData(
        backgroundColor: isDark ? AppColors.cardBackgroundDark : Colors.white,
        selectedItemColor: isDark ? AppColors.primaryDark : AppColors.primaryLight,
        unselectedItemColor: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
        elevation: 8,
      ),

      // 芯片主题
      chipTheme: ChipThemeData(
        backgroundColor: isDark ? AppColors.primaryDark.withAlpha(51) : AppColors.primaryLight.withAlpha(26),
        disabledColor: isDark ? AppColors.dividerDark : AppColors.dividerLight,
        selectedColor: isDark ? AppColors.primaryDark : AppColors.primaryLight,
        secondarySelectedColor: isDark ? AppColors.primaryDarkVariantLight : AppColors.primaryLightVariant,
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 0),
        labelStyle: TextStyle(color: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight),
        secondaryLabelStyle: const TextStyle(color: Colors.white),
        brightness: brightness,
      ),
    );
  }

  // 亮色主题
  static ThemeData get light => _createBaseTheme(ThemeData.light(), Brightness.light);

  // 暗色主题
  static ThemeData get dark => _createBaseTheme(ThemeData.dark(), Brightness.dark);
}
