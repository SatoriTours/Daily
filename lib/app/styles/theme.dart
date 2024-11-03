import 'package:daily_satori/app/styles/font_style.dart';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class MyTheme {
  static get light => ThemeData.light().copyWith(
        textTheme: ThemeData.light().textTheme.apply(fontFamily: GoogleFonts.lato().fontFamily),
        appBarTheme: AppBarTheme(titleTextStyle: MyFontStyle.appBarTitleStyle),
      );

  static get dark => ThemeData.dark().copyWith(
        textTheme: ThemeData.dark().textTheme.apply(fontFamily: GoogleFonts.lato().fontFamily),
        appBarTheme: AppBarTheme(titleTextStyle: MyFontStyle.appBarTitleStyle),
      );
}
