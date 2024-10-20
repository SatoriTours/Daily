import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class MyFontStyle {
  // 定义静态字体样式
  static const double _height = 1.5;
  static const double _letterSpacing = 1;

  static get appBarTitleStyle {
    return GoogleFonts.lato(
      fontSize: 20,
      fontWeight: FontWeight.bold,
      height: _height,
      letterSpacing: _letterSpacing,
    );
  }

  static get listTitleStyle {
    return GoogleFonts.lato(
      fontSize: 16,
      fontWeight: FontWeight.w500,
      height: _height,
      letterSpacing: _letterSpacing,
    );
  }

  static get titleStyle {
    return GoogleFonts.lato(
      fontSize: 18,
      fontWeight: FontWeight.w500,
      height: _height,
      letterSpacing: _letterSpacing,
    );
  }

  static TextStyle get bodyStyle {
    return GoogleFonts.lato(
      fontSize: 15,
      fontWeight: FontWeight.w500,
      height: _height,
      letterSpacing: _letterSpacing,
    );
  }
}
