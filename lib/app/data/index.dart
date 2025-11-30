/// Daily Satori 数据层导出文件
///
/// 本库集中导出所有数据模型和仓储类，提供统一的数据访问接口。
///
/// ## 包含的模块
/// - 基础类：[BaseRepository], [EntityModel]
/// - AI配置：[AIConfigModel], [AIConfigRepository]
/// - 文章：[ArticleModel], [ArticleRepository], [ArticleStatus]
/// - 书籍：[Book], [BookRepository], [BookSearchResult]
/// - 书籍观点：[BookViewpoint], [BookViewpointRepository]
/// - 日记：[DiaryModel], [DiaryRepository]
/// - 图片：[ImageModel], [ImageRepository]
/// - 截图：[ScreenshotModel], [ScreenshotRepository]
/// - 会话：[SessionModel], [SessionRepository]
/// - 设置：[SettingModel], [SettingRepository]
/// - 标签：[TagModel], [TagRepository]
library;

// ========================================================================
// 基础类
// ========================================================================

export 'base/base_repository.dart';
export 'base/entity_model.dart';

// ========================================================================
// AI配置
// ========================================================================

export 'ai_config/ai_config_model.dart';
export 'ai_config/ai_config_repository.dart';

// ========================================================================
// 文章
// ========================================================================

export 'article/article_model.dart';
export 'article/article_repository.dart';
export 'article/article_status.dart';

// ========================================================================
// 书籍
// ========================================================================

export 'book/book.dart';
export 'book/book_repository.dart';
export 'book/book_search_result.dart';

// ========================================================================
// 书籍观点
// ========================================================================

export 'book_viewpoint/book_viewpoint.dart';
export 'book_viewpoint/book_viewpoint_repository.dart';

// ========================================================================
// 日记
// ========================================================================

export 'diary/diary_model.dart';
export 'diary/diary_repository.dart';

// ========================================================================
// 图片
// ========================================================================

export 'image/image_model.dart';
export 'image/image_repository.dart';

// ========================================================================
// 截图
// ========================================================================

export 'screenshot/screenshot_model.dart';
export 'screenshot/screenshot_repository.dart';

// ========================================================================
// 会话
// ========================================================================

export 'session/session_model.dart';
export 'session/session_repository.dart';

// ========================================================================
// 设置
// ========================================================================

export 'setting/setting_model.dart';
export 'setting/setting_repository.dart';

// ========================================================================
// 标签
// ========================================================================

export 'tag/tag_model.dart';
export 'tag/tag_repository.dart';

// ========================================================================
// 周报
// ========================================================================

export 'weekly_summary/weekly_summary_model.dart';
export 'weekly_summary/weekly_summary_repository.dart';
