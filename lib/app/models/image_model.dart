import 'package:daily_satori/app/models/mixins/entity_model_mixin.dart';
import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/repositories/image_repository.dart';

/// 图片数据模型类
///
/// 封装Image实体类，提供属性访问方法
class ImageModel with EntityModelMixin<Image> {
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

  /// 底层实体对象
  @override
  Image get entity => _entity;

  /// ID
  @override
  int get id => entity.id;

  /// 路径
  String? get path => entity.path;
  set path(String? value) => entity.path = value;

  /// 所属文章ID
  int? get articleId => entity.article.targetId;

  /// 创建时间 - Image实体不包含此字段
  @override
  DateTime? get createdAt => null;
  @override
  set createdAt(DateTime? value) {}

  /// 更新时间 - Image实体不包含此字段
  @override
  DateTime? get updatedAt => null;
  @override
  set updatedAt(DateTime? value) {}

  /// 保存模型
  Future<void> save() async {
    ImageRepository.instance.save(entity);
  }
}
