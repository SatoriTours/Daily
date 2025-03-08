/// 模块集中导出文件
///
/// 用于集中导出所有模块控制器，方便其他地方导入
library modules;

// 文章模块
export 'articles/controllers/articles_controller.dart';
export 'article_detail/controllers/article_detail_controller.dart';

// 设置模块
export 'settings/controllers/settings_controller.dart';

// 分享对话框模块
export 'share_dialog/controllers/share_dialog_controller.dart';

// 备份恢复模块
export 'backup_restore/controllers/backup_restore_controller.dart';

// 左侧栏模块
export 'left_bar/controllers/left_bar_controller.dart';

// 首页模块
export 'home/controllers/home_controller.dart';
