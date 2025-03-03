import 'package:flutter/material.dart';

import 'package:google_fonts/google_fonts.dart';

import 'package:daily_satori/app/styles/colors.dart';

class MyFontStyle {
  // 私有构造函数,防止实例化
  MyFontStyle._();

  // 基础字体样式配置
  static const double _height = 1.5;
  static const double _letterSpacing = 0.5;
  static const String _fontFamily = 'Lato';

  /// 字体系列
  static String get fontFamily => GoogleFonts.lato().fontFamily!;

  // 创建基础文本样式
  static TextStyle _createBaseStyle({
    required double fontSize,
    FontWeight fontWeight = FontWeight.normal,
    Color? color,
    TextDecoration? decoration,
    double? height,
    double? letterSpacing,
  }) {
    return GoogleFonts.getFont(
      _fontFamily,
      fontSize: fontSize,
      fontWeight: fontWeight,
      height: height ?? _height,
      letterSpacing: letterSpacing ?? _letterSpacing,
      color: color,
      decoration: decoration,
    );
  }

  // 获取当前主题下的文本样式
  static TextStyle getThemedStyle(BuildContext context, TextStyle baseStyle, {Color? lightColor, Color? darkColor}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    Color? textColor;

    if (lightColor != null || darkColor != null) {
      textColor = isDark ? (darkColor ?? AppColors.textPrimaryDark) : (lightColor ?? AppColors.textPrimaryLight);
    }

    return baseStyle.copyWith(color: textColor);
  }

  /// Material 3 文本主题样式
  // Display 样式
  static final displayLarge = _createBaseStyle(fontSize: 57, fontWeight: FontWeight.w400, height: 1.12);
  static final displayMedium = _createBaseStyle(fontSize: 45, fontWeight: FontWeight.w400, height: 1.16);
  static final displaySmall = _createBaseStyle(fontSize: 36, fontWeight: FontWeight.w400, height: 1.22);

  // Headline 样式
  static final headlineLarge = _createBaseStyle(fontSize: 32, fontWeight: FontWeight.w600, height: 1.25);
  static final headlineMedium = _createBaseStyle(fontSize: 28, fontWeight: FontWeight.w600, height: 1.29);
  static final headlineSmall = _createBaseStyle(fontSize: 24, fontWeight: FontWeight.w600, height: 1.33);

  // Title 样式
  static final titleLarge = _createBaseStyle(fontSize: 22, fontWeight: FontWeight.w600, height: 1.27);
  static final titleMedium = _createBaseStyle(fontSize: 16, fontWeight: FontWeight.w600, height: 1.5);
  static final titleSmall = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w500, height: 1.43);

  // Body 样式
  static final bodyLarge = _createBaseStyle(fontSize: 16, fontWeight: FontWeight.w400, height: 1.5);
  static final bodyMedium = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w400, height: 1.43);
  static final bodySmall = _createBaseStyle(fontSize: 12, fontWeight: FontWeight.w400, height: 1.33);

  // Label 样式
  static final labelLarge = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w500, height: 1.43);
  static final labelMedium = _createBaseStyle(fontSize: 12, fontWeight: FontWeight.w500, height: 1.33);
  static final labelSmall = _createBaseStyle(fontSize: 11, fontWeight: FontWeight.w500, height: 1.45);

  // 标题相关样式
  static final appBarTitleStyle = _createBaseStyle(
    fontSize: 20,
    fontWeight: FontWeight.w600,
    color: Colors.white,
    letterSpacing: 0.8,
  );

  // 添加一个新的方法来获取适合当前主题的AppBar标题样式
  static TextStyle appBarTitleStyleThemed(BuildContext context) {
    return _createBaseStyle(fontSize: 20, fontWeight: FontWeight.w600, color: Colors.white, letterSpacing: 0.8);
  }

  // 添加一个新的方法来获取适合当前主题的标题样式
  static TextStyle headerTitleStyleThemed(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    // 在暗黑模式下使用更柔和的颜色
    final color = isDark ? AppColors.textPrimaryDark : AppColors.primaryLight;

    return _createBaseStyle(fontSize: 20, fontWeight: FontWeight.bold, height: 1.3, color: color);
  }

  static final articleTitleStyle = _createBaseStyle(fontSize: 20, fontWeight: FontWeight.bold, height: 1.3);

  static TextStyle articleTitleStyleThemed(BuildContext context) {
    return getThemedStyle(
      context,
      articleTitleStyle,
      lightColor: AppColors.textPrimaryLight,
      darkColor: AppColors.textPrimaryDark,
    );
  }

  static final listTitleStyle = _createBaseStyle(fontSize: 16, fontWeight: FontWeight.w600, height: 1.4);

  static TextStyle listTitleStyleThemed(BuildContext context) {
    return getThemedStyle(
      context,
      listTitleStyle,
      lightColor: AppColors.textPrimaryLight,
      darkColor: AppColors.textPrimaryDark,
    );
  }

  static final settingGroupTitle = _createBaseStyle(fontSize: 18, fontWeight: FontWeight.w600, letterSpacing: 0.8);

  static TextStyle settingGroupTitleThemed(BuildContext context) {
    return getThemedStyle(
      context,
      settingGroupTitle,
      lightColor: AppColors.primaryLight,
      darkColor: AppColors.primaryDark,
    );
  }

  // 内容相关样式
  static final articleBodyStyle = _createBaseStyle(fontSize: 16, fontWeight: FontWeight.normal, height: 1.6);

  static TextStyle articleBodyStyleThemed(BuildContext context) {
    return getThemedStyle(
      context,
      articleBodyStyle,
      lightColor: AppColors.textPrimaryLight,
      darkColor: AppColors.textPrimaryDark,
    );
  }

  static final commentStyle = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.normal);

  static TextStyle commentStyleThemed(BuildContext context) {
    return getThemedStyle(
      context,
      commentStyle,
      lightColor: AppColors.textSecondaryLight,
      darkColor: AppColors.textSecondaryDark,
    );
  }

  static final tagStyle = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w500);

  static TextStyle tagStyleThemed(BuildContext context) {
    return getThemedStyle(context, tagStyle, lightColor: AppColors.primaryLight, darkColor: AppColors.primaryDark);
  }

  static final tagsListContent = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w500);

  static TextStyle tagsListContentThemed(BuildContext context) {
    return getThemedStyle(
      context,
      tagsListContent,
      lightColor: AppColors.textPrimaryLight,
      darkColor: AppColors.textPrimaryDark,
    );
  }

  // 功能性文本样式
  static final loadingTipsStyle = _createBaseStyle(
    fontSize: 16,
    fontWeight: FontWeight.w500,
    decoration: TextDecoration.none,
  );

  static TextStyle loadingTipsStyleThemed(BuildContext context) {
    return getThemedStyle(
      context,
      loadingTipsStyle,
      lightColor: AppColors.textSecondaryLight,
      darkColor: AppColors.textSecondaryDark,
    );
  }

  // 新增样式
  static final cardSubtitleStyle = _createBaseStyle(fontSize: 13, fontWeight: FontWeight.normal);

  static TextStyle cardSubtitleStyleThemed(BuildContext context) {
    return getThemedStyle(
      context,
      cardSubtitleStyle,
      lightColor: AppColors.textSecondaryLight,
      darkColor: AppColors.textSecondaryDark,
    );
  }

  static final buttonTextStyle = _createBaseStyle(
    fontSize: 15,
    fontWeight: FontWeight.w600,
    color: Colors.white,
    letterSpacing: 0.8,
  );

  static final chipTextStyle = _createBaseStyle(fontSize: 13, fontWeight: FontWeight.w500);

  static TextStyle chipTextStyleThemed(BuildContext context) {
    return getThemedStyle(context, chipTextStyle, lightColor: AppColors.primaryLight, darkColor: AppColors.primaryDark);
  }

  static final tabLabelStyle = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w600, letterSpacing: 0.8);

  static TextStyle tabLabelStyleThemed(BuildContext context) {
    return getThemedStyle(context, tabLabelStyle, lightColor: AppColors.primaryLight, darkColor: AppColors.primaryDark);
  }

  static final emptyStateStyle = _createBaseStyle(fontSize: 16, fontWeight: FontWeight.w500);

  static TextStyle emptyStateStyleThemed(BuildContext context) {
    return getThemedStyle(
      context,
      emptyStateStyle,
      lightColor: AppColors.textSecondaryLight,
      darkColor: AppColors.textSecondaryDark,
    );
  }
}
