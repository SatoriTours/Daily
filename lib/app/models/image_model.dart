import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/repositories/image_repository.dart';

/// 图片数据模型类
///
/// 封装Image实体类，提供属性访问方法
class ImageModel {
  /// 底层实体对象
  final Image _entity;

  /// 构造函数
  ImageModel(this._entity);

  /// 从ID创建实例
  factory ImageModel.fromId(int id) {
    final image = ImageRepository.instance.findModel(id);
    if (image == null) {
      throw Exception('找不到ID为$id的图片');
    }
    return image;
  }

  /// 获取底层实体
  Image get entity => _entity;

  /// ID
  int get id => _entity.id;

  /// 路径
  String? get path => _entity.path;
  set path(String? value) => _entity.path = value;

  /// 所属文章ID
  int? get articleId => _entity.article.targetId;

  /// 保存模型
  Future<void> save() async {
    ImageRepository.instance.save(_entity);
  }
}
