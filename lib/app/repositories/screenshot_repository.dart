import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/models/screenshot_model.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 截图仓储类
///
/// 提供操作截图实体的静态方法集合
class ScreenshotRepository {
  // 私有构造函数防止实例化
  ScreenshotRepository._();

  // 获取Box的静态方法
  static Box<Screenshot> get _box => ObjectboxService.i.box<Screenshot>();

  /// 查找所有截图
  static List<ScreenshotModel> all() {
    return _box.getAll().map((e) => ScreenshotModel(e)).toList();
  }

  /// 根据ID查找截图
  static ScreenshotModel? find(int id) {
    final screenshot = _box.get(id);
    return screenshot != null ? ScreenshotModel(screenshot) : null;
  }

  /// 根据路径查找截图
  static ScreenshotModel? findByPath(String path) {
    final query = _box.query(Screenshot_.path.equals(path)).build();
    final screenshot = query.findFirst();
    query.close();
    return screenshot != null ? ScreenshotModel(screenshot) : null;
  }

  /// 使用数据创建截图模型
  static ScreenshotModel createWithData(Map<String, dynamic> data, ArticleModel articleModel) {
    final screenshot = Screenshot(path: data['path']);

    // 设置关联
    screenshot.article.target = articleModel.entity;

    return ScreenshotModel(screenshot);
  }

  /// 保存截图
  static Future<int> create(ScreenshotModel screenshotModel) async {
    return await _box.putAsync(screenshotModel.entity);
  }

  /// 更新截图
  static Future<int> update(ScreenshotModel screenshotModel) async {
    return await _box.putAsync(screenshotModel.entity);
  }

  /// 删除截图
  static bool destroy(int id) {
    return _box.remove(id);
  }
}
