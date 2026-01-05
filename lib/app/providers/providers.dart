/// Riverpod Providers 统一导出
///
/// 本文件只导出全局状态 providers。
/// 页面级 controller providers 已移动到各自的 pages/*/providers/ 目录中。
library;

// State Providers (全局状态服务)

export 'app_state_provider.dart';
export 'article_state_provider.dart';
export 'diary_state_provider.dart';
export 'books_state_provider.dart';
export 'first_launch_provider.dart';

// 页面级 Controller Providers
// 页面级 providers 现在位于各自的页面目录下:
//
// - pages/articles/providers/articles_controller_provider.dart
// - pages/article_detail/providers/article_detail_controller_provider.dart
// - pages/ai_chat/providers/ai_chat_controller_provider.dart
// - pages/ai_config/providers/ai_config_controller_provider.dart
// - pages/ai_config_edit/providers/ai_config_edit_controller_provider.dart
// - pages/backup_restore/providers/backup_restore_controller_provider.dart
// - pages/backup_settings/providers/backup_settings_controller_provider.dart
// - pages/books/providers/books_controller_provider.dart
// - pages/books/providers/book_search_controller_provider.dart
// - pages/diary/providers/diary_controller_provider.dart
// - pages/home/providers/home_controller_provider.dart
// - pages/left_bar/providers/left_bar_controller_provider.dart
// - pages/plugin_center/providers/plugin_center_controller_provider.dart
// - pages/settings/providers/settings_controller_provider.dart
// - pages/share_dialog/providers/share_dialog_controller_provider.dart
// - pages/weekly_summary/providers/weekly_summary_controller_provider.dart
