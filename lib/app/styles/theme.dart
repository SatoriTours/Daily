import 'package:flutter/material.dart';

import 'package:google_fonts/google_fonts.dart';

import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/styles/font_style.dart';

class MyTheme {
  // 私有构造函数,防止实例化
  MyTheme._();

  // 获取字体系列
  static String get _fontFamily => GoogleFonts.lato().fontFamily!;

  // 创建基础主题
  static ThemeData _createBaseTheme(ThemeData base) {
    return base.copyWith(
      // 主色调
      primaryColor: AppColors.primary,
      primaryColorLight: AppColors.primaryLight,
      primaryColorDark: AppColors.primaryDark,
      colorScheme: ColorScheme.fromSwatch().copyWith(
        primary: AppColors.primary,
        secondary: AppColors.accent,
        background: AppColors.background,
        error: AppColors.error,
      ),

      // 背景色
      scaffoldBackgroundColor: AppColors.background,

      // 文本主题
      textTheme: base.textTheme.apply(
        fontFamily: _fontFamily,
        bodyColor: AppColors.textPrimary,
        displayColor: AppColors.textPrimary,
      ),

      // AppBar 主题
      appBarTheme: AppBarTheme(
        backgroundColor: AppColors.primary,
        foregroundColor: Colors.white,
        elevation: 2,
        centerTitle: true,
        titleTextStyle: MyFontStyle.appBarTitleStyle,
        iconTheme: const IconThemeData(color: Colors.white),
      ),

      // 卡片主题
      cardTheme: CardTheme(
        color: AppColors.cardBackground,
        elevation: 2,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        margin: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
      ),

      // 按钮主题
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.primary,
          foregroundColor: Colors.white,
          elevation: 2,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        ),
      ),

      // 输入框主题
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: BorderSide(color: AppColors.divider),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: BorderSide(color: AppColors.divider),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: BorderSide(color: AppColors.primary, width: 2),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: BorderSide(color: AppColors.error, width: 1),
        ),
        hintStyle: TextStyle(color: AppColors.textHint),
      ),

      // 图标主题
      iconTheme: IconThemeData(color: AppColors.primary, size: 24),

      // 分隔线主题
      dividerTheme: DividerThemeData(color: AppColors.divider, thickness: 1, space: 16),

      // 底部导航栏主题
      bottomNavigationBarTheme: BottomNavigationBarThemeData(
        backgroundColor: Colors.white,
        selectedItemColor: AppColors.primary,
        unselectedItemColor: AppColors.textSecondary,
        elevation: 8,
      ),

      // 芯片主题
      chipTheme: ChipThemeData(
        backgroundColor: AppColors.primary.withOpacity(0.1),
        disabledColor: AppColors.divider,
        selectedColor: AppColors.primary,
        secondarySelectedColor: AppColors.primaryLight,
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 0),
        labelStyle: TextStyle(color: AppColors.textPrimary),
        secondaryLabelStyle: TextStyle(color: Colors.white),
        brightness: Brightness.light,
      ),
    );
  }

  // 亮色主题
  static ThemeData get light => _createBaseTheme(ThemeData.light());

  // 暗色主题
  static ThemeData get dark => _createBaseTheme(ThemeData.dark());
}
