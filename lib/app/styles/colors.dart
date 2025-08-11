import 'package:flutter/material.dart';

class AppColors {
  // 私有构造函数，防止实例化
  AppColors._();

  // 获取当前主题颜色
  static Color getColor(BuildContext context, Color lightColor, Color darkColor) {
    return Theme.of(context).brightness == Brightness.light ? lightColor : darkColor;
  }

  // 主要颜色
  static const Color primaryLight = Color(0xFF3F51B5); // 靛蓝色
  static const Color primaryLightVariant = Color(0xFF757DE8); // 浅靛蓝色
  static const Color primaryDarkVariant = Color(0xFF002984); // 深靛蓝色

  static const Color primaryDark = Color(0xFF5C6BC0); // 暗模式下的靛蓝色
  static const Color primaryDarkVariantLight = Color(0xFF8E99F3); // 暗模式下的浅靛蓝色
  static const Color primaryDarkVariantDark = Color(0xFF26418F); // 暗模式下的深靛蓝色

  // 新增暗黑模式下的AppBar背景色
  static const Color appBarBackgroundDark = Color(0xFF1A1A2E); // 暗模式下的AppBar背景色，更深的蓝黑色调

  // 强调色
  static const Color accentLight = Color(0xFFFF9800); // 橙色
  static const Color accentLightVariant = Color(0xFFFFC947); // 浅橙色
  static const Color accentDarkVariant = Color(0xFFC66900); // 深橙色

  static const Color accentDark = Color(0xFFFFB74D); // 暗模式下的橙色
  static const Color accentDarkVariantLight = Color(0xFFFFD180); // 暗模式下的浅橙色
  static const Color accentDarkVariantDark = Color(0xFFFF8F00); // 暗模式下的深橙色

  // 文本颜色
  static const Color textPrimaryLight = Color(0xFF212121); // 主要文本
  static const Color textSecondaryLight = Color(0xFF757575); // 次要文本
  static const Color textHintLight = Color(0xFFBDBDBD); // 提示文本

  static const Color textPrimaryDark = Color(0xFFF5F5F5); // 暗模式下的主要文本
  static const Color textSecondaryDark = Color(0xFFB0B0B0); // 暗模式下的次要文本
  static const Color textHintDark = Color(0xFF757575); // 暗模式下的提示文本

  // 背景颜色
  static const Color backgroundLight = Color(0xFFF5F5F5); // 主背景
  static const Color cardBackgroundLight = Color(0xFFFFFFFF); // 卡片背景
  static const Color dividerLight = Color(0xFFE0E0E0); // 分隔线

  static const Color backgroundDark = Color(0xFF121212); // 暗模式下的主背景
  static const Color cardBackgroundDark = Color(0xFF1E1E1E); // 暗模式下的卡片背景
  static const Color dividerDark = Color(0xFF323232); // 暗模式下的分隔线

  // 功能性颜色
  static const Color successLight = Color(0xFF4CAF50); // 成功
  static const Color errorLight = Color(0xFFF44336); // 错误
  static const Color warningLight = Color(0xFFFF9800); // 警告
  static const Color infoLight = Color(0xFF2196F3); // 信息

  static const Color successDark = Color(0xFF66BB6A); // 暗模式下的成功
  static const Color errorDark = Color(0xFFE57373); // 暗模式下的错误
  static const Color warningDark = Color(0xFFFFB74D); // 暗模式下的警告
  static const Color infoDark = Color(0xFF64B5F6); // 暗模式下的信息

  // 标签颜色 - 亮色模式
  static const List<Color> tagColorsLight = [
    Color(0xFF3F51B5), // 靛蓝
    Color(0xFF009688), // 青色
    Color(0xFF4CAF50), // 绿色
    Color(0xFF8BC34A), // 浅绿
    Color(0xFFCDDC39), // 酸橙
    Color(0xFFFFEB3B), // 黄色
    Color(0xFFFFC107), // 琥珀
    Color(0xFFFF9800), // 橙色
    Color(0xFFFF5722), // 深橙
    Color(0xFFF44336), // 红色
  ];

  // 标签颜色 - 暗色模式
  static const List<Color> tagColorsDark = [
    Color(0xFF5C6BC0), // 靛蓝
    Color(0xFF26A69A), // 青色
    Color(0xFF66BB6A), // 绿色
    Color(0xFF9CCC65), // 浅绿
    Color(0xFFD4E157), // 酸橙
    Color(0xFFFFEE58), // 黄色
    Color(0xFFFFCA28), // 琥珀
    Color(0xFFFFB74D), // 橙色
    Color(0xFFFF8A65), // 深橙
    Color(0xFFE57373), // 红色
  ];

  // 获取当前主题下的颜色
  static Color primary(BuildContext context) => getColor(context, primaryLight, primaryDark);

  static Color primaryVariantLight(BuildContext context) =>
      getColor(context, primaryLightVariant, primaryDarkVariantLight);

  static Color primaryVariantDark(BuildContext context) =>
      getColor(context, primaryDarkVariant, primaryDarkVariantDark);

  static Color accent(BuildContext context) => getColor(context, accentLight, accentDark);

  static Color accentVariantLight(BuildContext context) =>
      getColor(context, accentLightVariant, accentDarkVariantLight);

  static Color accentVariantDark(BuildContext context) => getColor(context, accentDarkVariant, accentDarkVariantDark);

  static Color textPrimary(BuildContext context) => getColor(context, textPrimaryLight, textPrimaryDark);

  static Color textSecondary(BuildContext context) => getColor(context, textSecondaryLight, textSecondaryDark);

  static Color textHint(BuildContext context) => getColor(context, textHintLight, textHintDark);

  static Color background(BuildContext context) => getColor(context, backgroundLight, backgroundDark);

  static Color cardBackground(BuildContext context) => getColor(context, cardBackgroundLight, cardBackgroundDark);

  static Color divider(BuildContext context) => getColor(context, dividerLight, dividerDark);

  static Color success(BuildContext context) => getColor(context, successLight, successDark);

  static Color error(BuildContext context) => getColor(context, errorLight, errorDark);

  static Color warning(BuildContext context) => getColor(context, warningLight, warningDark);

  static Color info(BuildContext context) => getColor(context, infoLight, infoDark);

  static List<Color> tagColors(BuildContext context) =>
      Theme.of(context).brightness == Brightness.light ? tagColorsLight : tagColorsDark;

  // 获取AppBar背景色
  static Color appBarBackground(BuildContext context) => getColor(context, primaryLight, appBarBackgroundDark);

  // === 兼容旧代码的辅助方法 ===
  // 旧代码使用 AppColors.border / secondary / searchBackground
  static Color border(BuildContext context) => divider(context);
  static Color secondary(BuildContext context) => textSecondary(context);
  static Color searchBackground(BuildContext context) => cardBackground(context);
}
