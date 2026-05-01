import 'package:flutter/material.dart';
import 'package:flutter_html/flutter_html.dart';
import 'package:daily_satori/app/styles/app_theme.dart';

class HtmlStyles {
  static Map<String, Style> getStyles(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return {
      "body": Style(
        fontSize: FontSize(15.0),
        margin: Margins.zero,
        padding: HtmlPaddings.zero,
        lineHeight: LineHeight.number(1.8),
      ),
      "div": Style(
        margin: Margins.only(bottom: 8),
        lineHeight: LineHeight.number(1.8),
      ),
      "img": Style(
        width: Width(MediaQuery.of(context).size.width - 48),
        margin: Margins.only(top: 16, bottom: 16),
        alignment: Alignment.center,
      ),
      "a": Style(
        color: colorScheme.primary,
        textDecoration: TextDecoration.none,
        fontWeight: FontWeight.w500,
        fontSize: FontSize(15.0),
      ),
      "p": Style(
        margin: Margins.only(bottom: 20),
        lineHeight: LineHeight.number(1.8),
        fontSize: FontSize(15.0),
      ),
      "h1,h2,h3,h4,h5,h6": Style(
        fontWeight: FontWeight.bold,
        margin: Margins.only(top: 28, bottom: 20),
      ),
      "h1": Style(fontSize: FontSize(22.0)),
      "h2": Style(fontSize: FontSize(20.0)),
      "h3": Style(fontSize: FontSize(18.0)),
      "h4": Style(fontSize: FontSize(16.0)),
      "h5": Style(fontSize: FontSize(15.0)),
      "h6": Style(fontSize: FontSize(14.0)),
      "blockquote": Style(
        backgroundColor: colorScheme.surfaceContainerLow,
        padding: HtmlPaddings.all(16),
        margin: Margins.only(bottom: 20, top: 8),
        border: Border(left: BorderSide(color: colorScheme.primary, width: 4)),
        fontSize: FontSize(15.0),
        lineHeight: LineHeight.number(1.7),
      ),
      "ul,ol": Style(
        margin: Margins.only(bottom: 20, left: 24, top: 8),
        lineHeight: LineHeight.number(1.8),
      ),
      "li": Style(
        margin: Margins.only(bottom: 12),
        lineHeight: LineHeight.number(1.8),
        fontSize: FontSize(15.0),
      ),
      "code": Style(
        backgroundColor: colorScheme.surfaceContainerHighest,
        padding: HtmlPaddings.all(6),
        fontFamily: 'monospace',
        fontSize: FontSize(13.0),
        lineHeight: LineHeight.number(1.6),
      ),
      "pre": Style(
        backgroundColor: colorScheme.surfaceContainerHighest,
        padding: HtmlPaddings.all(16),
        margin: Margins.only(bottom: 20, top: 8),
        fontSize: FontSize(13.0),
        lineHeight: LineHeight.number(1.6),
      ),
      "span": Style(
        fontSize: FontSize(15.0),
        lineHeight: LineHeight.number(1.8),
      ),
      "strong,b": Style(fontWeight: FontWeight.bold, fontSize: FontSize(15.0)),
      "em,i": Style(fontStyle: FontStyle.italic, fontSize: FontSize(15.0)),
    };
  }
}
