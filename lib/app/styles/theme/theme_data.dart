import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';
import 'package:daily_satori/app/styles/base/typography.dart';

/// 应用主题数据
/// 提供应用的主题配置，遵循统一的设计风格
class AppThemeData {
  // 私有构造函数，防止实例化
  AppThemeData._();

  /// 获取亮色主题
  static ThemeData getLightTheme() {
    const brightness = Brightness.light;
    final textTheme = AppTypography.getTextTheme();

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      // 颜色主题
      colorScheme: ColorScheme(
        brightness: brightness,
        primary: AppColors.primary,
        onPrimary: Colors.white,
        primaryContainer: AppColors.primary.withValues(alpha: 0.1),
        onPrimaryContainer: AppColors.primary,
        secondary: Colors.teal,
        onSecondary: Colors.white,
        secondaryContainer: Colors.teal.withValues(alpha: 0.1),
        onSecondaryContainer: Colors.teal,
        tertiary: Colors.amber,
        onTertiary: Colors.black,
        tertiaryContainer: Colors.amber.withValues(alpha: 0.1),
        onTertiaryContainer: Colors.amber.shade900,
        error: AppColors.error,
        onError: Colors.white,
        errorContainer: AppColors.error.withValues(alpha: 0.1),
        onErrorContainer: AppColors.error,
        background: AppColors.background,
        onBackground: AppColors.onBackground,
        surface: AppColors.surface,
        onSurface: AppColors.onSurface,
        surfaceVariant: AppColors.surfaceContainer,
        onSurfaceVariant: AppColors.onSurfaceVariant,
        outline: AppColors.outline,
        outlineVariant: AppColors.outlineVariant,
        shadow: Colors.black,
        scrim: Colors.black.withValues(alpha: 0.4),
        inverseSurface: Colors.grey.shade900,
        onInverseSurface: Colors.white,
        inversePrimary: AppColors.primaryLight,
        surfaceTint: Colors.transparent,
      ),

      // 文本主题
      textTheme: textTheme,

      // 应用栏主题
      appBarTheme: AppBarTheme(
        backgroundColor: AppColors.primary,
        foregroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
        titleTextStyle: AppTypography.appBarTitle.copyWith(color: Colors.white),
        iconTheme: const IconThemeData(color: Colors.white, size: Dimensions.iconSizeM),
        actionsIconTheme: const IconThemeData(color: Colors.white, size: Dimensions.iconSizeM),
        systemOverlayStyle: SystemUiOverlayStyle.light,
      ),

      // 悬浮按钮主题
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: AppColors.primary,
        foregroundColor: Colors.white,
        elevation: 2,
        shape: const CircleBorder(),
        extendedPadding: const EdgeInsets.all(Dimensions.spacingM),
      ),

      // 底部导航栏主题
      bottomNavigationBarTheme: BottomNavigationBarThemeData(
        backgroundColor: AppColors.surface,
        selectedItemColor: AppColors.primary,
        unselectedItemColor: AppColors.onSurfaceVariant,
        elevation: 8,
        type: BottomNavigationBarType.fixed,
        showSelectedLabels: true,
        showUnselectedLabels: true,
        selectedLabelStyle: AppTypography.navigationLabel,
        unselectedLabelStyle: AppTypography.navigationLabel,
      ),

      // 滑块主题
      sliderTheme: SliderThemeData(
        activeTrackColor: AppColors.primary,
        inactiveTrackColor: AppColors.primary.withValues(alpha: 0.3),
        thumbColor: AppColors.primary,
        overlayColor: AppColors.primary.withValues(alpha: 0.2),
        valueIndicatorColor: AppColors.primary,
        valueIndicatorTextStyle: const TextStyle(color: Colors.white),
      ),

      // 开关主题
      switchTheme: SwitchThemeData(
        thumbColor: MaterialStateProperty.resolveWith((states) {
          if (states.contains(MaterialState.selected)) {
            return AppColors.primary;
          }
          return Colors.grey.shade400;
        }),
        trackColor: MaterialStateProperty.resolveWith((states) {
          if (states.contains(MaterialState.selected)) {
            return AppColors.primary.withValues(alpha: 0.5);
          }
          return Colors.grey.shade300;
        }),
      ),

      // 复选框主题
      checkboxTheme: CheckboxThemeData(
        fillColor: MaterialStateProperty.resolveWith((states) {
          if (states.contains(MaterialState.selected)) {
            return AppColors.primary;
          }
          return Colors.transparent;
        }),
        checkColor: MaterialStateProperty.all(Colors.white),
        side: BorderSide(color: AppColors.onSurfaceVariant, width: 1.5),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
      ),

      // 单选按钮主题
      radioTheme: RadioThemeData(
        fillColor: MaterialStateProperty.resolveWith((states) {
          if (states.contains(MaterialState.selected)) {
            return AppColors.primary;
          }
          return AppColors.onSurfaceVariant;
        }),
      ),

      // 输入框主题
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.surfaceContainer,
        contentPadding: Dimensions.paddingInput,
        labelStyle: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant),
        hintStyle: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant.withValues(alpha: 0.7)),
        helperStyle: AppTypography.captionText.copyWith(color: AppColors.onSurfaceVariant),
        errorStyle: AppTypography.errorText.copyWith(color: AppColors.error),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: BorderSide(color: AppColors.outline, width: 1.0),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: BorderSide(color: AppColors.outline, width: 1.0),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: BorderSide(color: AppColors.primary, width: 2.0),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: BorderSide(color: AppColors.error, width: 1.5),
        ),
        focusedErrorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: BorderSide(color: AppColors.error, width: 1.5),
        ),
        disabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: BorderSide(color: AppColors.outline.withValues(alpha: 0.5), width: 1.0),
        ),
      ),

      // 按钮主题
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.primary,
          foregroundColor: Colors.white,
          elevation: 0,
          padding: Dimensions.paddingButton,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusS)),
          minimumSize: const Size.fromHeight(Dimensions.buttonHeight),
          textStyle: AppTypography.buttonText,
        ),
      ),

      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: AppColors.primary,
          side: BorderSide(color: AppColors.primary, width: 1.5),
          padding: Dimensions.paddingButton,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusS)),
          minimumSize: const Size.fromHeight(Dimensions.buttonHeight),
          textStyle: AppTypography.buttonText,
        ),
      ),

      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: AppColors.primary,
          padding: Dimensions.paddingButton,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusS)),
          minimumSize: const Size.fromHeight(Dimensions.buttonHeight),
          textStyle: AppTypography.buttonText,
        ),
      ),

      // 卡片主题
      cardTheme: CardTheme(
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusM),
          side: BorderSide(color: AppColors.outline, width: 1.0),
        ),
        color: AppColors.surface,
        margin: Dimensions.marginCard,
        clipBehavior: Clip.antiAlias,
        shadowColor: Colors.transparent,
      ),

      // 对话框主题
      dialogTheme: DialogTheme(
        backgroundColor: AppColors.surface,
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusL)),
        titleTextStyle: AppTypography.dialogTitle.copyWith(color: AppColors.onSurface),
        contentTextStyle: AppTypography.dialogContent.copyWith(color: AppColors.onSurfaceVariant),
      ),

      // 列表磁贴主题
      listTileTheme: ListTileThemeData(
        contentPadding: Dimensions.paddingListItem,
        minLeadingWidth: 24,
        minVerticalPadding: 12,
        dense: false,
        style: ListTileStyle.list,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusS)),
        iconColor: AppColors.onSurfaceVariant,
        textColor: AppColors.onSurface,
        selectedColor: AppColors.primary,
        selectedTileColor: AppColors.primary.withValues(alpha: 0.1),
      ),

      // 分隔线主题
      dividerTheme: DividerThemeData(color: AppColors.outline, thickness: 1, space: Dimensions.spacingM),

      // 标签主题
      chipTheme: ChipThemeData(
        backgroundColor: AppColors.primary.withValues(alpha: 0.1),
        deleteIconColor: AppColors.onSurfaceVariant,
        disabledColor: Colors.grey.shade200,
        selectedColor: AppColors.primary,
        secondarySelectedColor: AppColors.primary,
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 0),
        labelStyle: AppTypography.chipText.copyWith(color: AppColors.primary),
        secondaryLabelStyle: AppTypography.chipText.copyWith(color: Colors.white),
        shape: const StadiumBorder(),
      ),

      // 弹出菜单主题
      popupMenuTheme: PopupMenuThemeData(
        color: AppColors.surface,
        elevation: 4,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusM)),
      ),

      // 其他配置
      scaffoldBackgroundColor: AppColors.background,
      visualDensity: VisualDensity.adaptivePlatformDensity,
      pageTransitionsTheme: const PageTransitionsTheme(
        builders: {
          TargetPlatform.android: CupertinoPageTransitionsBuilder(),
          TargetPlatform.iOS: CupertinoPageTransitionsBuilder(),
        },
      ),
    );
  }

  /// 获取暗色主题
  static ThemeData getDarkTheme() {
    const brightness = Brightness.dark;
    final textTheme = AppTypography.getTextTheme();

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      // 颜色主题
      colorScheme: ColorScheme(
        brightness: brightness,
        primary: AppColors.primaryLight,
        onPrimary: Colors.black,
        primaryContainer: AppColors.primaryLight.withValues(alpha: 0.1),
        onPrimaryContainer: AppColors.primaryLight,
        secondary: Colors.tealAccent.shade100,
        onSecondary: Colors.black,
        secondaryContainer: Colors.tealAccent.shade100.withValues(alpha: 0.1),
        onSecondaryContainer: Colors.tealAccent.shade100,
        tertiary: Colors.amberAccent.shade100,
        onTertiary: Colors.black,
        tertiaryContainer: Colors.amberAccent.shade100.withValues(alpha: 0.1),
        onTertiaryContainer: Colors.amberAccent.shade100,
        error: AppColors.errorDark,
        onError: Colors.black,
        errorContainer: AppColors.errorDark.withValues(alpha: 0.1),
        onErrorContainer: AppColors.errorDark,
        background: AppColors.backgroundDark,
        onBackground: AppColors.onBackgroundDark,
        surface: AppColors.surfaceDark,
        onSurface: AppColors.onSurfaceDark,
        surfaceVariant: AppColors.surfaceContainerDark,
        onSurfaceVariant: AppColors.onSurfaceVariantDark,
        outline: AppColors.outlineDark,
        outlineVariant: AppColors.outlineVariantDark,
        shadow: Colors.black,
        scrim: Colors.black.withValues(alpha: 0.6),
        inverseSurface: Colors.grey.shade100,
        onInverseSurface: Colors.black,
        inversePrimary: AppColors.primary,
        surfaceTint: Colors.transparent,
      ),

      // 文本主题
      textTheme: textTheme,

      // 应用栏主题
      appBarTheme: AppBarTheme(
        backgroundColor: const Color(0xFF121212),
        foregroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
        titleTextStyle: AppTypography.appBarTitle.copyWith(color: Colors.white),
        iconTheme: const IconThemeData(color: Colors.white, size: Dimensions.iconSizeM),
        actionsIconTheme: const IconThemeData(color: Colors.white, size: Dimensions.iconSizeM),
        systemOverlayStyle: SystemUiOverlayStyle.light,
      ),

      // 悬浮按钮主题
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: AppColors.primaryLight,
        foregroundColor: Colors.black,
        elevation: 2,
        shape: const CircleBorder(),
        extendedPadding: const EdgeInsets.all(Dimensions.spacingM),
      ),

      // 底部导航栏主题
      bottomNavigationBarTheme: BottomNavigationBarThemeData(
        backgroundColor: AppColors.surfaceDark,
        selectedItemColor: AppColors.primaryLight,
        unselectedItemColor: AppColors.onSurfaceVariantDark,
        elevation: 8,
        type: BottomNavigationBarType.fixed,
        showSelectedLabels: true,
        showUnselectedLabels: true,
        selectedLabelStyle: AppTypography.navigationLabel,
        unselectedLabelStyle: AppTypography.navigationLabel,
      ),

      // 滑块主题
      sliderTheme: SliderThemeData(
        activeTrackColor: AppColors.primaryLight,
        inactiveTrackColor: AppColors.primaryLight.withValues(alpha: 0.3),
        thumbColor: AppColors.primaryLight,
        overlayColor: AppColors.primaryLight.withValues(alpha: 0.2),
        valueIndicatorColor: AppColors.primaryLight,
        valueIndicatorTextStyle: const TextStyle(color: Colors.black),
      ),

      // 开关主题
      switchTheme: SwitchThemeData(
        thumbColor: MaterialStateProperty.resolveWith((states) {
          if (states.contains(MaterialState.selected)) {
            return AppColors.primaryLight;
          }
          return Colors.grey.shade400;
        }),
        trackColor: MaterialStateProperty.resolveWith((states) {
          if (states.contains(MaterialState.selected)) {
            return AppColors.primaryLight.withValues(alpha: 0.5);
          }
          return Colors.grey.shade700;
        }),
      ),

      // 复选框主题
      checkboxTheme: CheckboxThemeData(
        fillColor: MaterialStateProperty.resolveWith((states) {
          if (states.contains(MaterialState.selected)) {
            return AppColors.primaryLight;
          }
          return Colors.transparent;
        }),
        checkColor: MaterialStateProperty.all(Colors.black),
        side: BorderSide(color: AppColors.onSurfaceVariantDark, width: 1.5),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
      ),

      // 单选按钮主题
      radioTheme: RadioThemeData(
        fillColor: MaterialStateProperty.resolveWith((states) {
          if (states.contains(MaterialState.selected)) {
            return AppColors.primaryLight;
          }
          return AppColors.onSurfaceVariantDark;
        }),
      ),

      // 输入框主题
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.surfaceContainerDark,
        contentPadding: Dimensions.paddingInput,
        labelStyle: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariantDark),
        hintStyle: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariantDark.withValues(alpha: 0.7)),
        helperStyle: AppTypography.captionText.copyWith(color: AppColors.onSurfaceVariantDark),
        errorStyle: AppTypography.errorText.copyWith(color: AppColors.errorDark),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: BorderSide(color: AppColors.outlineDark, width: 1.0),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: BorderSide(color: AppColors.outlineDark, width: 1.0),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: BorderSide(color: AppColors.primaryLight, width: 2.0),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: BorderSide(color: AppColors.errorDark, width: 1.5),
        ),
        focusedErrorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: BorderSide(color: AppColors.errorDark, width: 1.5),
        ),
        disabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: BorderSide(color: AppColors.outlineDark.withValues(alpha: 0.5), width: 1.0),
        ),
      ),

      // 按钮主题
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.primaryLight,
          foregroundColor: Colors.black,
          elevation: 0,
          padding: Dimensions.paddingButton,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusS)),
          minimumSize: const Size.fromHeight(Dimensions.buttonHeight),
          textStyle: AppTypography.buttonText,
        ),
      ),

      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: AppColors.primaryLight,
          side: BorderSide(color: AppColors.primaryLight, width: 1.5),
          padding: Dimensions.paddingButton,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusS)),
          minimumSize: const Size.fromHeight(Dimensions.buttonHeight),
          textStyle: AppTypography.buttonText,
        ),
      ),

      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: AppColors.primaryLight,
          padding: Dimensions.paddingButton,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusS)),
          minimumSize: const Size.fromHeight(Dimensions.buttonHeight),
          textStyle: AppTypography.buttonText,
        ),
      ),

      // 卡片主题
      cardTheme: CardTheme(
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusM),
          side: BorderSide(color: AppColors.outlineDark, width: 1.0),
        ),
        color: AppColors.surfaceDark,
        margin: Dimensions.marginCard,
        clipBehavior: Clip.antiAlias,
        shadowColor: Colors.transparent,
      ),

      // 对话框主题
      dialogTheme: DialogTheme(
        backgroundColor: AppColors.surfaceDark,
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusL)),
        titleTextStyle: AppTypography.dialogTitle.copyWith(color: AppColors.onSurfaceDark),
        contentTextStyle: AppTypography.dialogContent.copyWith(color: AppColors.onSurfaceVariantDark),
      ),

      // 列表磁贴主题
      listTileTheme: ListTileThemeData(
        contentPadding: Dimensions.paddingListItem,
        minLeadingWidth: 24,
        minVerticalPadding: 12,
        dense: false,
        style: ListTileStyle.list,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusS)),
        iconColor: AppColors.onSurfaceVariantDark,
        textColor: AppColors.onSurfaceDark,
        selectedColor: AppColors.primaryLight,
        selectedTileColor: AppColors.primaryLight.withValues(alpha: 0.1),
      ),

      // 分隔线主题
      dividerTheme: DividerThemeData(color: AppColors.outlineDark, thickness: 1, space: Dimensions.spacingM),

      // 标签主题
      chipTheme: ChipThemeData(
        backgroundColor: AppColors.primaryLight.withValues(alpha: 0.15),
        deleteIconColor: AppColors.onSurfaceVariantDark,
        disabledColor: Colors.grey.shade800,
        selectedColor: AppColors.primaryLight,
        secondarySelectedColor: AppColors.primaryLight,
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 0),
        labelStyle: AppTypography.chipText.copyWith(color: AppColors.primaryLight),
        secondaryLabelStyle: AppTypography.chipText.copyWith(color: Colors.black),
        shape: const StadiumBorder(),
      ),

      // 弹出菜单主题
      popupMenuTheme: PopupMenuThemeData(
        color: AppColors.surfaceDark,
        elevation: 4,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusM)),
      ),

      // 其他配置
      scaffoldBackgroundColor: AppColors.backgroundDark,
      visualDensity: VisualDensity.adaptivePlatformDensity,
      pageTransitionsTheme: const PageTransitionsTheme(
        builders: {
          TargetPlatform.android: CupertinoPageTransitionsBuilder(),
          TargetPlatform.iOS: CupertinoPageTransitionsBuilder(),
        },
      ),
    );
  }
}
