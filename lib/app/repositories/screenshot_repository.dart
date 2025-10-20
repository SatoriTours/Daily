import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/models/screenshot_model.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/repositories/base_repository.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 截图仓储类
///
/// 继承 BaseRepository 获取通用 CRUD 功能
/// 使用单例模式，通过 ScreenshotRepository.instance 访问
class ScreenshotRepository extends BaseRepository<Screenshot> {
  // 私有构造函数
  ScreenshotRepository._();

  // 单例
  static final ScreenshotRepository instance = ScreenshotRepository._();

  // 获取Box实例
  @override
  Box<Screenshot> get box => ObjectboxService.i.box<Screenshot>();

  // 每页数量
  @override
  int get pageSize => 50;

  // ==================== 特定业务方法 ====================

  /// 查找所有截图（返回Model）
  List<ScreenshotModel> allModels() {
    return all().map((e) => ScreenshotModel(e)).toList();
  }

  /// 根据ID查找截图（返回Model）
  ScreenshotModel? findModel(int id) {
    final screenshot = find(id);
    return screenshot != null ? ScreenshotModel(screenshot) : null;
  }

  /// 根据路径查找截图
  ScreenshotModel? findByPath(String path) {
    final screenshot = findFirstByStringEquals(Screenshot_.path, path);
    return screenshot != null ? ScreenshotModel(screenshot) : null;
  }

  /// 使用数据创建截图模型
  ScreenshotModel createWithData(Map<String, dynamic> data, ArticleModel articleModel) {
    final screenshot = Screenshot(path: data['path']);
    screenshot.article.target = articleModel.entity;
    return ScreenshotModel(screenshot);
  }

  /// 保存截图Model
  Future<int> createModel(ScreenshotModel screenshotModel) async {
    return await box.putAsync(screenshotModel.entity);
  }

  /// 更新截图Model
  Future<int> updateModel(ScreenshotModel screenshotModel) async {
    return await box.putAsync(screenshotModel.entity);
  }

  /// 删除截图（旧方法名兼容）
  bool destroy(int id) {
    return remove(id);
  }

  /// 根据文章ID删除截图
  int deleteByArticleId(int articleId) {
    final query = box.query(Screenshot_.article.equals(articleId)).build();
    try {
      final screenshots = query.find();
      if (screenshots.isEmpty) return 0;
      return removeMany(screenshots.map((s) => s.id).toList());
    } finally {
      query.close();
    }
  }
}
