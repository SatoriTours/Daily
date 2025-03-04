import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 图片模型类
///
/// 采用Rails风格的Model设计，同时作为领域模型和数据访问层
/// 包含实体属性访问和数据操作方法，遵循活动记录模式(Active Record Pattern)
class ImageModel {
  // 单例实现
  static final ImageModel _instance = ImageModel._internal();
  static ImageModel get i => _instance;
  factory ImageModel() => _instance;
  ImageModel._internal();

  // ObjectBox服务和Box访问
  final _objectboxService = ObjectboxService.i;
  Box<Image> get _imageBox => _objectboxService.box<Image>();

  // 实体对象
  Image? _image;

  /// 构造函数，接收一个Image实体
  ImageModel.withEntity(this._image);

  /// 获取原始Image实体
  Image? get entity => _image;

  /// 图片ID
  int get id => _image?.id ?? 0;

  /// 图片URL
  String? get url => _image?.url;

  /// 图片本地路径
  String? get path => _image?.path;

  /// 静态方法 - 从实体创建模型实例
  static ImageModel fromEntity(Image image) {
    return ImageModel.withEntity(image);
  }

  /// 静态方法 - 查找所有图片
  static List<ImageModel> all() {
    return i._findAll();
  }

  /// 静态方法 - 根据ID查找图片
  static ImageModel? find(int id) {
    return i._findById(id);
  }

  /// 静态方法 - 根据路径查找图片
  static ImageModel? findByPath(String path) {
    return i._findByPath(path);
  }

  /// 静态方法 - 保存图片
  static Future<int> create(ImageModel model) async {
    return await i._save(model);
  }

  /// 静态方法 - 删除图片
  static bool destroy(int id) {
    return i._delete(id);
  }

  /// 实例方法 - 保存当前模型
  Future<int> save() async {
    if (_image == null) return 0;
    return await ImageModel.create(this);
  }

  /// 实例方法 - 删除当前模型
  bool delete() {
    if (_image == null) return false;
    return ImageModel.destroy(id);
  }

  // 私有方法 - 查找所有图片
  List<ImageModel> _findAll() {
    final images = _imageBox.getAll();
    return _fromEntityList(images);
  }

  // 私有方法 - 根据ID查找图片
  ImageModel? _findById(int id) {
    final image = _imageBox.get(id);
    return image != null ? ImageModel.fromEntity(image) : null;
  }

  // 私有方法 - 根据路径查找图片
  ImageModel? _findByPath(String path) {
    final query = _imageBox.query(Image_.path.equals(path)).build();
    final image = query.findFirst();
    query.close();
    return image != null ? ImageModel.fromEntity(image) : null;
  }

  // 私有方法 - 保存图片
  Future<int> _save(ImageModel model) async {
    if (model._image == null) return 0;
    return await _imageBox.putAsync(model._image!);
  }

  // 私有方法 - 删除图片
  bool _delete(int id) {
    return _imageBox.remove(id);
  }

  // 私有方法 - 将实体列表转换为模型列表
  List<ImageModel> _fromEntityList(List<Image> images) {
    return images.map((image) => ImageModel.fromEntity(image)).toList();
  }
}
