import 'package:flutter/material.dart';
import 'package:flutter_html/flutter_html.dart';
import 'package:daily_satori/app/styles/app_theme.dart';

class HtmlStyles {
  static Map<String, Style> getStyles(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return {
      "body": Style(
        fontSize: FontSize(16.0),
        margin: Margins.zero,
        padding: HtmlPaddings.zero,
        lineHeight: LineHeight.number(1.5),
      ),
      "img": Style(
        width: Width(MediaQuery.of(context).size.width - 32),
        margin: Margins.only(top: 10, bottom: 10),
        alignment: Alignment.center,
      ),
      "a": Style(color: colorScheme.primary, textDecoration: TextDecoration.none, fontWeight: FontWeight.w500),
      "p": Style(margin: Margins.only(bottom: 16), lineHeight: LineHeight.number(1.6)),
      "h1,h2,h3,h4,h5,h6": Style(fontWeight: FontWeight.bold, margin: Margins.only(top: 24, bottom: 16)),
      "h1": Style(fontSize: FontSize(24.0)),
      "h2": Style(fontSize: FontSize(22.0)),
      "h3": Style(fontSize: FontSize(20.0)),
      "blockquote": Style(
        backgroundColor: colorScheme.surfaceContainerLow,
        padding: HtmlPaddings.all(16),
        margin: Margins.only(bottom: 16),
        border: Border(left: BorderSide(color: colorScheme.primary, width: 4)),
      ),
      "ul,ol": Style(margin: Margins.only(bottom: 16, left: 20)),
      "li": Style(margin: Margins.only(bottom: 8)),
      "code": Style(
        backgroundColor: colorScheme.surfaceContainerHighest,
        padding: HtmlPaddings.all(4),
        fontFamily: 'monospace',
      ),
      "pre": Style(
        backgroundColor: colorScheme.surfaceContainerHighest,
        padding: HtmlPaddings.all(16),
        margin: Margins.only(bottom: 16),
      ),
    };
  }
}
