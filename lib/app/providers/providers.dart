/// Riverpod Providers 统一导出
///
/// 本文件导出所有 Riverpod providers，方便统一导入使用。
library;

// ========================================================================
// State Providers (状态服务 - 替换原 StateServices)
// ========================================================================

export 'app_state_provider.dart';
export 'article_state_provider.dart';
export 'diary_state_provider.dart';
export 'books_state_provider.dart';

// ========================================================================
// Controller Providers (页面级 providers - 替换原 Controllers)
// ========================================================================

// 1. home_controller.dart - 主页控制器
export 'home_controller_provider.dart';

// 2. settings_controller.dart - 设置管理
export 'settings_controller_provider.dart';

// 3. articles_controller.dart - 文章列表管理
export 'articles_controller_provider.dart';

// 4. article_detail_controller.dart - 文章详情
export 'article_detail_controller_provider.dart';

// 5. diary_controller.dart - 日记管理
export 'diary_controller_provider.dart';

// 6. books_controller.dart - 读书管理
export 'books_controller_provider.dart';

// 7. ai_chat_controller.dart - AI聊天
export 'ai_chat_controller_provider.dart';

// 8. weekly_summary_controller.dart - 周报总结
export 'weekly_summary_controller_provider.dart';

// 9. left_bar_controller.dart - 侧边栏
export 'left_bar_controller_provider.dart';

// 10. share_dialog_controller.dart - 分享对话框
export 'share_dialog_controller_provider.dart';

// 11. backup_restore_controller.dart - 备份恢复
export 'backup_restore_controller_provider.dart';

// 12. plugin_center_controller.dart - 插件中心
export 'plugin_center_controller_provider.dart';

// 13. ai_config_controller.dart - AI配置
export 'ai_config_controller_provider.dart';

// 14. ai_config_edit_controller.dart - AI配置编辑
export 'ai_config_edit_controller_provider.dart';

// 15. book_search_controller.dart - 书籍搜索
export 'book_search_controller_provider.dart';

// 16. backup_settings_controller.dart - 备份设置
export 'backup_settings_controller_provider.dart';
