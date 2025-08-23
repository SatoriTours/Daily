import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// 应用排版样式类
/// 提供统一的文本样式定义，遵循 shadcn/ui 的设计风格
class AppTypography {
  // 私有构造函数，防止实例化
  AppTypography._();

  // 基础字体样式配置
  static const double _height = 1.5;
  static const double _letterSpacing = 0.5;

  /// 获取主要字体 (Lato)
  static String get fontFamily => GoogleFonts.lato().fontFamily ?? 'Lato';

  /// 获取备用字体 (Inter)
  static String get fontFamilyAlternate => GoogleFonts.inter().fontFamily ?? 'Inter';

  /// 创建基础文本样式
  static TextStyle _createBaseStyle({
    required double fontSize,
    FontWeight fontWeight = FontWeight.normal,
    Color? color,
    TextDecoration? decoration,
    double? height,
    double? letterSpacing,
    String? fontFamily,
  }) {
    return TextStyle(
      fontFamily: fontFamily ?? AppTypography.fontFamily,
      fontSize: fontSize,
      fontWeight: fontWeight,
      height: height ?? _height,
      letterSpacing: letterSpacing ?? _letterSpacing,
      color: color,
      decoration: decoration,
    );
  }

  /// 获取当前主题下的文本样式
  static TextStyle getThemedStyle(BuildContext context, TextStyle baseStyle, {Color? lightColor, Color? darkColor}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textColor = isDark
        ? (darkColor ?? Theme.of(context).textTheme.bodyLarge?.color)
        : (lightColor ?? Theme.of(context).textTheme.bodyLarge?.color);

    return baseStyle.copyWith(color: textColor);
  }

  /// 标题样式 - 特大号
  static final displayLarge = _createBaseStyle(fontSize: 57, fontWeight: FontWeight.w400, height: 1.12);

  /// 标题样式 - 大号
  static final displayMedium = _createBaseStyle(fontSize: 45, fontWeight: FontWeight.w400, height: 1.16);

  /// 标题样式 - 中号
  static final displaySmall = _createBaseStyle(fontSize: 36, fontWeight: FontWeight.w400, height: 1.22);

  /// 标题样式 - 大号
  static final headingLarge = _createBaseStyle(fontSize: 32, fontWeight: FontWeight.w600, height: 1.25);

  /// 标题样式 - 中号
  static final headingMedium = _createBaseStyle(fontSize: 24, fontWeight: FontWeight.w600, height: 1.3);

  /// 标题样式 - 小号
  static final headingSmall = _createBaseStyle(fontSize: 20, fontWeight: FontWeight.w600, height: 1.4);

  /// 副标题样式 - 大号
  static final titleLarge = _createBaseStyle(fontSize: 18, fontWeight: FontWeight.w600, height: 1.28);

  /// 副标题样式 - 中号
  static final titleMedium = _createBaseStyle(fontSize: 16, fontWeight: FontWeight.w600, height: 1.5);

  /// 副标题样式 - 小号
  static final titleSmall = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w500, height: 1.43);

  /// 正文样式 - 大号
  static final bodyLarge = _createBaseStyle(fontSize: 16, fontWeight: FontWeight.w400, height: 1.5);

  /// 正文样式 - 中号
  static final bodyMedium = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w400, height: 1.5);

  /// 正文样式 - 小号
  static final bodySmall = _createBaseStyle(fontSize: 12, fontWeight: FontWeight.w400, height: 1.5);

  /// 标签样式 - 大号
  static final labelLarge = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w500, height: 1.43);

  /// 标签样式 - 中号
  static final labelMedium = _createBaseStyle(fontSize: 12, fontWeight: FontWeight.w500, height: 1.33);

  /// 标签样式 - 小号
  static final labelSmall = _createBaseStyle(fontSize: 11, fontWeight: FontWeight.w500, height: 1.45);

  /// 按钮文本样式
  static final buttonText = _createBaseStyle(
    fontSize: 14,
    fontWeight: FontWeight.w600,
    height: 1.4,
    letterSpacing: 0.5,
  );

  /// 小按钮文本样式
  static final buttonTextSmall = _createBaseStyle(
    fontSize: 12,
    fontWeight: FontWeight.w600,
    height: 1.4,
    letterSpacing: 0.4,
  );

  /// 应用栏标题样式
  static final appBarTitle = _createBaseStyle(
    fontSize: 18,
    fontWeight: FontWeight.w600,
    height: 1.3,
    letterSpacing: 0.4,
  );

  /// 搜索栏文本样式
  static final searchText = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w400, height: 1.4);

  /// 导航菜单文本样式
  static final navigationLabel = _createBaseStyle(fontSize: 12, fontWeight: FontWeight.w500, height: 1.2);

  /// 标签文本样式
  static final chipText = _createBaseStyle(fontSize: 12, fontWeight: FontWeight.w500, height: 1.33);

  /// 列表项标题样式
  static final listItemTitle = _createBaseStyle(fontSize: 16, fontWeight: FontWeight.w600, height: 1.4);

  /// 列表项副标题样式
  static final listItemSubtitle = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w400, height: 1.4);

  /// 卡片标题样式
  static final cardTitle = _createBaseStyle(fontSize: 16, fontWeight: FontWeight.w600, height: 1.4);

  /// 卡片副标题样式
  static final cardSubtitle = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w400, height: 1.4);

  /// 卡片内容样式
  static final cardContent = _createBaseStyle(fontSize: 14, fontWeight: FontWeight.w400, height: 1.6);

  /// 对话框标题样式
  static final dialogTitle = _createBaseStyle(fontSize: 20, fontWeight: FontWeight.w600, height: 1.3);

  /// 对话框内容样式
  static final dialogContent = _createBaseStyle(fontSize: 16, fontWeight: FontWeight.w400, height: 1.5);

  /// 小提示文本样式
  static final captionText = _createBaseStyle(
    fontSize: 12,
    fontWeight: FontWeight.w400,
    height: 1.33,
    letterSpacing: 0.4,
  );

  /// 错误文本样式
  static final errorText = _createBaseStyle(fontSize: 12, fontWeight: FontWeight.w400, height: 1.33);

  /// 获取文本样式主题
  static TextTheme getTextTheme() {
    return TextTheme(
      displayLarge: displayLarge,
      displayMedium: displayMedium,
      displaySmall: displaySmall,
      headlineLarge: headingLarge,
      headlineMedium: headingMedium,
      headlineSmall: headingSmall,
      titleLarge: titleLarge,
      titleMedium: titleMedium,
      titleSmall: titleSmall,
      bodyLarge: bodyLarge,
      bodyMedium: bodyMedium,
      bodySmall: bodySmall,
      labelLarge: labelLarge,
      labelMedium: labelMedium,
      labelSmall: labelSmall,
    );
  }
}
