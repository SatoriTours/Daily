/// 服务集中导出文件
///
/// 导出所有服务，方便统一引用
library;

// 服务注册
export 'service_registry.dart';

// 核心服务
export 'logger_service.dart';
export 'flutter_service.dart';
export 'time_service.dart';
export 'objectbox_service.dart';
export 'file_service.dart';
export 'http_service.dart';

// 设置相关
export 'setting_service/setting_service.dart';
export 'ai_config_service.dart';
export 'font_service.dart';

// AI服务
export 'ai_service/ai_service.dart';
export 'ai_service/ai_article_processor.dart';

// 数据服务
export 'backup_service.dart';
export 'migration_service/migration_service.dart';
export 'webpage_parser_service.dart';

// 网络服务
export 'web_service/web_service.dart';

// 系统服务
export 'adblock_service.dart';
export 'freedisk_service.dart';
export 'app_upgrade_service.dart';
export 'plugin_service.dart';

// 分享和剪贴板
export 'share_receive_service.dart';
export 'clipboard_monitor_service.dart';

// Web内容通知
export 'web_content/web_content_notifier.dart';

// 周报服务
export 'weekly_summary_service.dart';

// 国际化
export 'i18n/i18n_service.dart';
