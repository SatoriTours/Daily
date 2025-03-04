import 'package:daily_satori/app/models/base_model.dart';
import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 图片模型类
///
/// 采用Rails风格的Model设计，同时作为领域模型和数据访问层
/// 包含实体属性访问和数据操作方法，遵循活动记录模式(Active Record Pattern)
class ImageModel extends BaseModel<Image> {
  // 单例实现
  static final ImageModel _instance = ImageModel._internal();
  static ImageModel get i => _instance;
  factory ImageModel() => _instance;
  ImageModel._internal() : super.withEntity(null);

  /// 构造函数，接收一个Image实体
  ImageModel.withEntity(Image? image) : super.withEntity(image);

  /// 获取图片ID
  @override
  int get id => entity?.id ?? 0;

  /// 图片URL
  String? get url => entity?.url;

  /// 图片本地路径
  String? get path => entity?.path;

  /// 静态方法 - 从实体创建模型实例
  static ImageModel fromEntity(Image image) {
    return ImageModel.withEntity(image);
  }

  /// 实现基类的抽象方法，从实体创建模型实例
  @override
  BaseModel<Image> createFromEntity(Image entity) {
    return ImageModel.fromEntity(entity);
  }

  /// 实现基类的抽象方法，保存实体
  @override
  Future<int> saveEntity(Image entity) async {
    return await box.putAsync(entity);
  }

  /// 静态方法 - 查找所有图片
  static List<ImageModel> all() {
    return i.findAll().cast<ImageModel>();
  }

  /// 静态方法 - 根据ID查找图片
  static ImageModel? find(int id) {
    return i.findById(id) as ImageModel?;
  }

  /// 静态方法 - 根据路径查找图片
  static ImageModel? findByPath(String path) {
    return i._findByPath(path);
  }

  /// 静态方法 - 保存图片
  static Future<int> create(ImageModel model) async {
    return await i.saveModel(model);
  }

  /// 静态方法 - 删除图片
  static bool destroy(int id) {
    return i.deleteById(id);
  }

  // 私有方法 - 根据路径查找图片
  ImageModel? _findByPath(String path) {
    final query = box.query(Image_.path.equals(path)).build();
    final image = query.findFirst();
    query.close();
    return image != null ? ImageModel.fromEntity(image) : null;
  }
}
