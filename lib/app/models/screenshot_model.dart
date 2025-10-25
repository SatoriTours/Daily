import 'package:daily_satori/app/models/base_model.dart';
import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/repositories/screenshot_repository.dart';

/// 截图数据模型类
///
/// 封装Screenshot实体类，提供属性访问方法
class ScreenshotModel extends BaseModel<Screenshot> {
  /// 构造函数
  ScreenshotModel(super.entity);

  /// 从ID创建实例
  factory ScreenshotModel.fromId(int id) {
    final screenshot = ScreenshotRepository.instance.findModel(id);
    if (screenshot == null) {
      throw Exception('找不到ID为$id的截图');
    }
    return screenshot;
  }

  /// ID
  @override
  int get id => entity.id;

  /// 路径
  String? get path => entity.path;
  set path(String? value) => entity.path = value;

  /// 所属文章ID
  int? get articleId => entity.article.targetId;

  /// 创建时间 - Screenshot实体不包含此字段
  @override
  DateTime? get createdAt => null;
  @override
  set createdAt(DateTime? value) {}

  /// 更新时间 - Screenshot实体不包含此字段
  @override
  DateTime? get updatedAt => null;
  @override
  set updatedAt(DateTime? value) {}

  /// 保存模型
  Future<void> save() async {
    ScreenshotRepository.instance.save(entity);
  }
}
