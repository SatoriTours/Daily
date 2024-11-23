import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class MyFontStyle {
  // 定义静态字体样式
  static const double _height = 1.6;
  static const double _letterSpacing = 1.5;

  static final appBarTitleStyle = GoogleFonts.lato(
    fontSize: 20,
    height: _height,
    letterSpacing: _letterSpacing,
  );

  static final listTitleStyle = GoogleFonts.lato(
    fontSize: 16,
    fontWeight: FontWeight.bold,
    height: _height,
    letterSpacing: _letterSpacing,
  );

  static final articleTitleStyle = GoogleFonts.lato(
    fontSize: 18,
    fontWeight: FontWeight.bold,
    height: _height,
    letterSpacing: _letterSpacing,
  );

  static final articleBodyStyle = GoogleFonts.lato(
    fontSize: 15,
    fontWeight: FontWeight.w500,
    height: _height,
    letterSpacing: _letterSpacing,
  );

  static final commentStyle = GoogleFonts.lato(
    fontSize: 14,
    fontWeight: FontWeight.w500,
    height: _height,
    letterSpacing: _letterSpacing,
    color: Colors.grey[700],
  );

  static final tagStyle = GoogleFonts.lato(
    fontSize: 14,
    fontWeight: FontWeight.w500,
    height: _height,
    letterSpacing: _letterSpacing,
    color: Colors.blue[700],
  );

  static final settingGroupTitle = GoogleFonts.lato(
    fontSize: 20,
    fontWeight: FontWeight.w500,
    height: _height,
    letterSpacing: _letterSpacing,
    color: Colors.blue,
  );

  static final tagsListContent = GoogleFonts.lato(
    fontSize: 14,
    fontWeight: FontWeight.w400,
    letterSpacing: _letterSpacing,
    height: _height,
  );

  static final loadingTipsStyle = GoogleFonts.lato(
      fontSize: 18,
      fontWeight: FontWeight.w500,
      height: _height,
      letterSpacing: _letterSpacing,
      color: Colors.grey[600],
      decoration: TextDecoration.none);
}
