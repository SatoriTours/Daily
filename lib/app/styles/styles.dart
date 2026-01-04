/// Daily Satori 样式系统
///
/// 统一的样式导出文件，提供应用所有样式相关的类和常量。
///
/// ## 使用方法
///
/// ```dart
/// import 'package:daily_satori/app/styles/index.dart';
/// ```
///
/// ## 样式分层
///
/// ### 1. 基础样式 (Base)
/// - [AppColors] - 颜色系统，使用主题感知方法
/// - [Dimensions] - 尺寸、间距、圆角常量
/// - [AppTypography] - 字体样式系统
/// - [AppBorders] - 边框样式
/// - [AppShadows] - 阴影样式
/// - [Opacities] - 透明度常量
/// - [Animations] - 动画时长和曲线
///
/// ### 2. 组件样式 (Components)
/// - [ButtonStyles] - 按钮样式
/// - [CardStyles] - 卡片样式
/// - [InputStyles] - 输入框样式
/// - [ListStyles] - 列表样式
/// - [DialogStyles] - 对话框样式
/// - [AccordionStyles] - 手风琴/折叠面板样式
/// - [SnackbarStyles] - Snackbar 样式
///
/// ### 3. 高级样式 (StyleGuide)
/// - [StyleGuide] - 统一的风格指南，提供高级样式方法
///
/// ### 4. 页面样式 (Pages)
/// - [DiaryStyles] - 日记模块特有样式
///
/// ## 使用优先级
///
/// 1. 优先使用 `StyleGuide` 高级方法
/// 2. 其次使用组件样式类 (`ButtonStyles`, `InputStyles` 等)
/// 3. 再次使用基础 Tokens (`Dimensions`, `AppColors`, `AppTypography`)
/// 4. 最后才使用 `.copyWith()` 微调
library;

// ============================================================================
// 基础样式 - 设计系统的核心 Token
// ============================================================================

export 'base/colors.dart';
export 'base/dimensions.dart';
export 'base/typography.dart';
export 'base/borders.dart';
export 'base/shadows.dart';
export 'base/opacities.dart';
export 'base/animations.dart';

// ============================================================================
// 组件样式 - 可复用的 UI 组件样式
// ============================================================================

export 'components/button_styles.dart';
export 'components/card_styles.dart';
export 'components/input_styles.dart';
export 'components/list_styles.dart';
export 'components/dialog_styles.dart';
export 'components/accordion_styles.dart';
export 'components/snackbar_styles.dart';

// ============================================================================
// 页面样式 - 特定页面的独有样式
// ============================================================================

export 'pages/diary_styles.dart';

// ============================================================================
// 主题 - 应用主题配置
// ============================================================================

export 'theme/app_theme.dart';
export 'theme/theme_data.dart';

// ============================================================================
// 特殊样式 - Markdown/HTML 渲染样式
// ============================================================================

export 'markdown_styles.dart';
export 'html_styles.dart';

// ============================================================================
// 风格指南 - 高级样式封装
// ============================================================================

export 'style_guide.dart';
