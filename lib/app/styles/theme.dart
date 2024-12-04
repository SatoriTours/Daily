import 'package:flutter/material.dart';

import 'package:google_fonts/google_fonts.dart';

import 'package:daily_satori/app/styles/font_style.dart';

class MyTheme {
  // 私有构造函数,防止实例化
  MyTheme._();

  // 获取字体系列
  static String get _fontFamily => GoogleFonts.lato().fontFamily!;

  // 创建基础主题
  static ThemeData _createBaseTheme(ThemeData base) {
    return base.copyWith(
      textTheme: base.textTheme.apply(
        fontFamily: _fontFamily,
      ),
      appBarTheme: AppBarTheme(
        titleTextStyle: MyFontStyle.appBarTitleStyle,
      ),
    );
  }

  // 亮色主题
  static ThemeData get light => _createBaseTheme(ThemeData.light());

  // 暗色主题
  static ThemeData get dark => _createBaseTheme(ThemeData.dark());
}
