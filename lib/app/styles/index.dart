/// 样式导出文件
///
/// 这个文件集中导出所有样式相关的类和常量，按照以下分类组织：
/// 1. 基础样式：颜色、字体、尺寸等
/// 2. 组件样式：按钮、卡片、输入框等UI组件的样式
/// 3. 主题相关：应用主题设置
library;

// 基础样式
export 'base/colors.dart';
export 'base/dimensions.dart';
export 'base/typography.dart';
export 'base/borders.dart';
export 'base/shadows.dart';

// 组件样式
export 'components/button_styles.dart';
export 'components/card_styles.dart';
export 'components/input_styles.dart';
export 'components/list_styles.dart';
export 'components/dialog_styles.dart';
export 'components/accordion_styles.dart';

// 页面特定样式
export 'pages/diary_styles.dart';
export 'pages/articles_styles.dart';

// 主题
export 'theme/app_theme.dart';
export 'theme/theme_data.dart';

// 特殊样式
export 'markdown_styles.dart';
export 'html_styles.dart';
