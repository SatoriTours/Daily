import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/repositories/screenshot_repository.dart';

/// 截图数据模型类
///
/// 封装Screenshot实体类，提供属性访问方法
class ScreenshotModel {
  /// 底层实体对象
  final Screenshot _entity;

  /// 构造函数
  ScreenshotModel(this._entity);

  /// 从ID创建实例
  factory ScreenshotModel.fromId(int id) {
    final screenshot = ScreenshotRepository.find(id);
    if (screenshot == null) {
      throw Exception('找不到ID为$id的截图');
    }
    return screenshot;
  }

  /// 获取底层实体
  Screenshot get entity => _entity;

  /// ID
  int get id => _entity.id;

  /// 路径
  String? get path => _entity.path;
  set path(String? value) => _entity.path = value;

  /// 所属文章ID
  int? get articleId => _entity.article.targetId;

  /// 保存模型
  Future<void> save() async {
    await ScreenshotRepository.update(this);
  }
}
