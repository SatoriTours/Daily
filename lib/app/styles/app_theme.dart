import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/styles/font_style.dart';
import 'package:daily_satori/app/styles/component_style.dart';

/// 应用主题管理类
/// 提供统一的亮色和暗色主题定义，并支持各种组件的样式
class AppTheme {
  // 私有构造函数，防止实例化
  AppTheme._();

  /// 获取亮色主题
  static ThemeData get light => _buildTheme(Brightness.light);

  /// 获取暗色主题
  static ThemeData get dark => _buildTheme(Brightness.dark);

  /// 根据亮度构建主题
  static ThemeData _buildTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;
    final colorScheme = _buildColorScheme(brightness);

    final theme = ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme,
      brightness: brightness,
      scaffoldBackgroundColor: isDark ? AppColors.backgroundDark : AppColors.backgroundLight,
      primaryColor: isDark ? AppColors.primaryDark : AppColors.primaryLight,
      visualDensity: VisualDensity.adaptivePlatformDensity,
      fontFamily: MyFontStyle.fontFamily,
    );

    return theme.copyWith(
      // 文本主题
      textTheme: _buildTextTheme(theme.textTheme, brightness),

      // AppBar 主题
      appBarTheme: _buildAppBarTheme(brightness),

      // 卡片主题
      cardTheme: ComponentStyle.cardTheme(brightness),

      // 按钮主题
      elevatedButtonTheme: ComponentStyle.elevatedButtonTheme(brightness),
      outlinedButtonTheme: ComponentStyle.outlinedButtonTheme(brightness),
      textButtonTheme: ComponentStyle.textButtonTheme(brightness),

      // 输入框主题
      inputDecorationTheme: ComponentStyle.inputDecorationTheme(brightness),

      // 图标主题
      iconTheme: ComponentStyle.iconTheme(brightness),
      primaryIconTheme: ComponentStyle.primaryIconTheme(brightness),

      // 分隔线主题
      dividerTheme: ComponentStyle.dividerTheme(brightness),

      // 底部导航栏主题
      bottomNavigationBarTheme: ComponentStyle.bottomNavigationBarTheme(brightness),

      // 芯片主题
      chipTheme: ComponentStyle.chipTheme(brightness),

      // 浮动按钮主题
      floatingActionButtonTheme: ComponentStyle.floatingActionButtonTheme(brightness),

      // 列表磁贴主题
      listTileTheme: ComponentStyle.listTileTheme(brightness),
    );
  }

  /// 构建色彩方案
  static ColorScheme _buildColorScheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return ColorScheme(
      brightness: brightness,
      primary: isDark ? AppColors.primaryDark : AppColors.primaryLight,
      onPrimary: Colors.white,
      primaryContainer: isDark ? AppColors.primaryDarkVariantDark : AppColors.primaryLightVariant,
      onPrimaryContainer: Colors.white,
      secondary: isDark ? AppColors.accentDark : AppColors.accentLight,
      onSecondary: Colors.white,
      secondaryContainer: isDark ? AppColors.accentDarkVariantLight : AppColors.accentLightVariant,
      onSecondaryContainer: Colors.white,
      error: isDark ? AppColors.errorDark : AppColors.errorLight,
      onError: Colors.white,
      surface: isDark ? AppColors.cardBackgroundDark : AppColors.cardBackgroundLight,
      onSurface: isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
      surfaceContainerHighest:
          isDark ? AppColors.cardBackgroundDark.withAlpha(77) : AppColors.cardBackgroundLight.withAlpha(77),
      onSurfaceVariant: isDark ? AppColors.textSecondaryDark : AppColors.textSecondaryLight,
      outline: isDark ? AppColors.dividerDark : AppColors.dividerLight,
      shadow: Colors.black.withAlpha(isDark ? 77 : 51),
      inverseSurface: isDark ? AppColors.backgroundLight : AppColors.backgroundDark,
      onInverseSurface: isDark ? AppColors.textPrimaryLight : AppColors.textPrimaryDark,
      inversePrimary: isDark ? AppColors.primaryLight : AppColors.primaryDark,
    );
  }

  /// 构建文本主题
  static TextTheme _buildTextTheme(TextTheme base, Brightness brightness) {
    final isDark = brightness == Brightness.dark;
    final textColor = isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight;

    return base.copyWith(
      displayLarge: MyFontStyle.displayLarge.copyWith(color: textColor),
      displayMedium: MyFontStyle.displayMedium.copyWith(color: textColor),
      displaySmall: MyFontStyle.displaySmall.copyWith(color: textColor),
      headlineLarge: MyFontStyle.headlineLarge.copyWith(color: textColor),
      headlineMedium: MyFontStyle.headlineMedium.copyWith(color: textColor),
      headlineSmall: MyFontStyle.headlineSmall.copyWith(color: textColor),
      titleLarge: MyFontStyle.titleLarge.copyWith(color: textColor),
      titleMedium: MyFontStyle.titleMedium.copyWith(color: textColor),
      titleSmall: MyFontStyle.titleSmall.copyWith(color: textColor),
      bodyLarge: MyFontStyle.bodyLarge.copyWith(color: textColor),
      bodyMedium: MyFontStyle.bodyMedium.copyWith(color: textColor),
      bodySmall: MyFontStyle.bodySmall.copyWith(color: textColor),
      labelLarge: MyFontStyle.labelLarge.copyWith(color: textColor),
      labelMedium: MyFontStyle.labelMedium.copyWith(color: textColor),
      labelSmall: MyFontStyle.labelSmall.copyWith(color: textColor),
    );
  }

  /// 构建AppBar主题
  static AppBarTheme _buildAppBarTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return AppBarTheme(
      backgroundColor: isDark ? AppColors.appBarBackgroundDark : AppColors.primaryLight,
      foregroundColor: Colors.white,
      elevation: 0,
      centerTitle: true,
      titleTextStyle: MyFontStyle.appBarTitleStyle,
      iconTheme: const IconThemeData(color: Colors.white),
      scrolledUnderElevation: isDark ? 2 : 4,
      systemOverlayStyle: _buildSystemUiOverlayStyle(brightness),
    );
  }

  /// 构建系统UI样式
  static SystemUiOverlayStyle _buildSystemUiOverlayStyle(Brightness brightness) {
    final isDark = brightness == Brightness.dark;

    return SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: isDark ? Brightness.light : Brightness.dark,
      statusBarBrightness: isDark ? Brightness.dark : Brightness.light,
      systemNavigationBarColor: isDark ? AppColors.appBarBackgroundDark : AppColors.primaryLight,
      systemNavigationBarIconBrightness: Brightness.light,
    );
  }

  /// 获取当前主题下的ThemeData
  static ThemeData getTheme(BuildContext context) {
    return Theme.of(context);
  }

  /// 获取当前主题是否为暗色模式
  static bool isDarkMode(BuildContext context) {
    return Theme.of(context).brightness == Brightness.dark;
  }

  /// 获取当前主题的ColorScheme
  static ColorScheme getColorScheme(BuildContext context) {
    return Theme.of(context).colorScheme;
  }

  /// 获取当前主题的TextTheme
  static TextTheme getTextTheme(BuildContext context) {
    return Theme.of(context).textTheme;
  }
}
