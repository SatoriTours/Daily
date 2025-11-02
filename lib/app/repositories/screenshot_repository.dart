import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/models/screenshot_model.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/repositories/base_repository.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 截图仓储类
///
/// 继承 BaseRepository 获取通用 CRUD 功能
/// 使用单例模式，通过 ScreenshotRepository.i 访问
class ScreenshotRepository extends BaseRepository<Screenshot, ScreenshotModel> {
  // 私有构造函数
  ScreenshotRepository._();

  // 单例
  static final i = ScreenshotRepository._();

  // ==================== BaseRepository 必须实现的方法 ====================

  @override
  ScreenshotModel toModel(Screenshot entity) {
    return ScreenshotModel(entity);
  }

  // ==================== 特定业务方法 ====================

  /// 根据路径查找截图
  ScreenshotModel? findByPath(String path) {
    return findFirstByStringEquals(Screenshot_.path, path);
  }

  /// 使用数据创建截图模型
  ScreenshotModel createWithData(Map<String, dynamic> data, ArticleModel articleModel) {
    final screenshot = Screenshot(path: data['path']);
    screenshot.article.target = articleModel.entity;
    return ScreenshotModel(screenshot);
  }

  /// 删除截图（旧方法名兼容）
  bool destroy(int id) {
    return remove(id);
  }

  /// 根据文章ID删除截图
  int deleteByArticleId(int articleId) {
    final query = box.query(Screenshot_.article.equals(articleId)).build();
    final screenshots = query.find();
    query.close();
    if (screenshots.isEmpty) return 0;
    return removeMany(screenshots.map((s) => s.id).toList());
  }
}
