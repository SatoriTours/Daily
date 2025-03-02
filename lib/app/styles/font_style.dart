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
      color: color ?? AppColors.textPrimary,
      decoration: decoration,
    );
  }

  // 标题相关样式
  static final appBarTitleStyle = _createBaseStyle(
    fontSize: 20,
    fontWeight: FontWeight.w600,
    color: Colors.white,
    letterSpacing: 0.8,
  );

  static final articleTitleStyle = _createBaseStyle(fontSize: 20, fontWeight: FontWeight.bold, height: 1.3);

  static final listTitleStyle = _createBaseStyle(fontSize: 16, fontWeight: FontWeight.w600, height: 1.4);

  static final settingGroupTitle = _createBaseStyle(
    fontSize: 18,
    fontWeight: FontWeight.w600,
    color: AppColors.primary,
    letterSpacing: 0.8,
  );

  // 内容相关样式
  static final articleBodyStyle = _createBaseStyle(fontSize: 16, fontWeight: FontWeight.normal, height: 1.6);

  static final commentStyle = _createBaseStyle(
    fontSize: 14,
    fontWeight: FontWeight.normal,
    color: AppColors.textSecondary,
  );

  static final tagStyle = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w500, color: AppColors.primary);

  static final tagsListContent = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w500);

  // 功能性文本样式
  static final loadingTipsStyle = _createBaseStyle(
    fontSize: 16,
    fontWeight: FontWeight.w500,
    color: AppColors.textSecondary,
    decoration: TextDecoration.none,
  );

  // 新增样式
  static final cardSubtitleStyle = _createBaseStyle(
    fontSize: 13,
    fontWeight: FontWeight.normal,
    color: AppColors.textSecondary,
  );

  static final buttonTextStyle = _createBaseStyle(
    fontSize: 15,
    fontWeight: FontWeight.w600,
    color: Colors.white,
    letterSpacing: 0.8,
  );

  static final chipTextStyle = _createBaseStyle(fontSize: 13, fontWeight: FontWeight.w500, color: AppColors.primary);

  static final tabLabelStyle = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w600, letterSpacing: 0.8);

  static final emptyStateStyle = _createBaseStyle(
    fontSize: 16,
    fontWeight: FontWeight.w500,
    color: AppColors.textSecondary,
  );
}
