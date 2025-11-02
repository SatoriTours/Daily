/// Daily Satori 仓储层导出文件
///
/// 本库集中导出所有仓储类，提供统一的数据访问接口。
/// 包含以下仓储：
/// - 基础仓储 [BaseRepository]
/// - AI配置仓储 [AIConfigRepository]
/// - 文章仓储 [ArticleRepository]
/// - 书籍仓储 [BookRepository]
/// - 书籍观点仓储 [BookViewpointRepository]
/// - 日记仓储 [DiaryRepository]
/// - 图片仓储 [ImageRepository]
/// - 截图仓储 [ScreenshotRepository]
/// - 会话仓储 [SessionRepository]
/// - 设置仓储 [SettingRepository]
/// - 标签仓储 [TagRepository]
library;

export 'ai_config_repository.dart';
export 'article_repository.dart';
export 'base_repository.dart';
export 'book_repository.dart';
export 'book_viewpoint_repository.dart';
export 'diary_repository.dart';
export 'image_repository.dart';
export 'screenshot_repository.dart';
export 'session_repository.dart';
export 'setting_repository.dart';
export 'tag_repository.dart';
