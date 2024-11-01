import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class MyFontStyle {
  // 定义静态字体样式
  static const double _height = 1.8;
  static const double _letterSpacing = 1.6;

  static get appBarTitleStyle {
    return GoogleFonts.lato(
      fontSize: 20,
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

  static get articleTitleStyle {
    return GoogleFonts.lato(
      fontSize: 18,
      fontWeight: FontWeight.w500,
      height: _height,
      letterSpacing: _letterSpacing,
    );
  }

  static TextStyle get articleBodyStyle {
    return GoogleFonts.lato(
      fontSize: 15,
      fontWeight: FontWeight.w500,
      height: _height,
      letterSpacing: _letterSpacing,
    );
  }

  static get settingGroupTitle {
    return GoogleFonts.lato(
      fontSize: 20,
      fontWeight: FontWeight.w500,
      height: _height,
      letterSpacing: _letterSpacing,
      color: Colors.blue,
    );
  }
}
