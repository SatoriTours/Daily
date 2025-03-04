import 'package:daily_satori/app/models/base_model.dart';
import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 截图模型类
///
/// 采用Rails风格的Model设计，同时作为领域模型和数据访问层
/// 包含实体属性访问和数据操作方法，遵循活动记录模式(Active Record Pattern)
class ScreenshotModel extends BaseModel<Screenshot> {
  // 单例实现
  static final ScreenshotModel _instance = ScreenshotModel._internal();
  static ScreenshotModel get i => _instance;
  factory ScreenshotModel() => _instance;
  ScreenshotModel._internal() : super.withEntity(null);

  /// 构造函数，接收一个Screenshot实体
  ScreenshotModel.withEntity(Screenshot? screenshot) : super.withEntity(screenshot);

  /// 获取截图ID
  @override
  int get id => entity?.id ?? 0;

  /// 截图本地路径
  String? get path => entity?.path;

  /// 静态方法 - 从实体创建模型实例
  static ScreenshotModel fromEntity(Screenshot screenshot) {
    return ScreenshotModel.withEntity(screenshot);
  }

  @override
  BaseModel<Screenshot> createFromEntity(Screenshot entity) {
    return ScreenshotModel.fromEntity(entity);
  }

  @override
  Future<int> saveEntity(Screenshot entity) async {
    return await box.putAsync(entity);
  }

  /// 静态方法 - 查找所有截图
  static List<ScreenshotModel> all() {
    return i.findAll().cast<ScreenshotModel>();
  }

  /// 静态方法 - 根据ID查找截图
  static ScreenshotModel? find(int id) {
    return i.findById(id) as ScreenshotModel?;
  }

  /// 静态方法 - 根据路径查找截图
  static ScreenshotModel? findByPath(String path) {
    return i._findByPath(path);
  }

  /// 静态方法 - 保存截图
  static Future<int> create(ScreenshotModel model) async {
    return await i.saveModel(model);
  }

  /// 静态方法 - 删除截图
  static bool destroy(int id) {
    return i.deleteById(id);
  }

  // 私有方法 - 根据路径查找截图
  ScreenshotModel? _findByPath(String path) {
    final query = box.query(Screenshot_.path.equals(path)).build();
    final screenshot = query.findFirst();
    query.close();
    return screenshot != null ? ScreenshotModel.fromEntity(screenshot) : null;
  }
}
