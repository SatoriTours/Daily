import 'package:flutter/material.dart';

import 'package:google_fonts/google_fonts.dart';

class MyFontStyle {
  // 私有构造函数,防止实例化
  MyFontStyle._();

  // 基础字体样式配置
  static const double _height = 1.6;
  static const double _letterSpacing = 1.5;
  static const String _fontFamily = 'Lato';

  // 创建基础文本样式
  static TextStyle _createBaseStyle({
    required double fontSize,
    FontWeight fontWeight = FontWeight.normal,
    Color? color,
    TextDecoration? decoration,
  }) {
    return GoogleFonts.getFont(
      _fontFamily,
      fontSize: fontSize,
      fontWeight: fontWeight,
      height: _height,
      letterSpacing: _letterSpacing,
      color: color,
      decoration: decoration,
    );
  }

  // 标题相关样式
  static final appBarTitleStyle = _createBaseStyle(fontSize: 20);
  static final articleTitleStyle = _createBaseStyle(
    fontSize: 18,
    fontWeight: FontWeight.bold,
  );
  static final listTitleStyle = _createBaseStyle(
    fontSize: 16,
    fontWeight: FontWeight.bold,
  );
  static final settingGroupTitle = _createBaseStyle(
    fontSize: 20,
    fontWeight: FontWeight.w500,
    color: Colors.blue,
  );

  // 内容相关样式
  static final articleBodyStyle = _createBaseStyle(
    fontSize: 15,
    fontWeight: FontWeight.w500,
  );
  static final commentStyle = _createBaseStyle(
    fontSize: 14,
    fontWeight: FontWeight.w500,
    color: Colors.grey[700],
  );
  static final tagStyle = _createBaseStyle(
    fontSize: 14,
    fontWeight: FontWeight.w500,
    color: Colors.blue[700],
  );
  static final tagsListContent = _createBaseStyle(
    fontSize: 14,
    fontWeight: FontWeight.w400,
  );

  // 功能性文本样式
  static final loadingTipsStyle = _createBaseStyle(
    fontSize: 18,
    fontWeight: FontWeight.w500,
    color: Colors.grey[600],
    decoration: TextDecoration.none,
  );
}
