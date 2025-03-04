import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 截图模型类
///
/// 采用Rails风格的Model设计，同时作为领域模型和数据访问层
/// 包含实体属性访问和数据操作方法，遵循活动记录模式(Active Record Pattern)
class ScreenshotModel {
  // 单例实现
  static final ScreenshotModel _instance = ScreenshotModel._internal();
  static ScreenshotModel get i => _instance;
  factory ScreenshotModel() => _instance;
  ScreenshotModel._internal();

  // ObjectBox服务和Box访问
  final _objectboxService = ObjectboxService.i;
  Box<Screenshot> get _screenshotBox => _objectboxService.box<Screenshot>();

  // 实体对象
  Screenshot? _screenshot;

  /// 构造函数，接收一个Screenshot实体
  ScreenshotModel.withEntity(this._screenshot);

  /// 获取原始Screenshot实体
  Screenshot? get entity => _screenshot;

  /// 截图ID
  int get id => _screenshot?.id ?? 0;

  /// 截图本地路径
  String? get path => _screenshot?.path;

  /// 静态方法 - 从实体创建模型实例
  static ScreenshotModel fromEntity(Screenshot screenshot) {
    return ScreenshotModel.withEntity(screenshot);
  }

  /// 静态方法 - 查找所有截图
  static List<ScreenshotModel> all() {
    return i._findAll();
  }

  /// 静态方法 - 根据ID查找截图
  static ScreenshotModel? find(int id) {
    return i._findById(id);
  }

  /// 静态方法 - 根据路径查找截图
  static ScreenshotModel? findByPath(String path) {
    return i._findByPath(path);
  }

  /// 静态方法 - 保存截图
  static Future<int> create(ScreenshotModel model) async {
    return await i._save(model);
  }

  /// 静态方法 - 删除截图
  static bool destroy(int id) {
    return i._delete(id);
  }

  /// 实例方法 - 保存当前模型
  Future<int> save() async {
    if (_screenshot == null) return 0;
    return await ScreenshotModel.create(this);
  }

  /// 实例方法 - 删除当前模型
  bool delete() {
    if (_screenshot == null) return false;
    return ScreenshotModel.destroy(id);
  }

  // 私有方法 - 查找所有截图
  List<ScreenshotModel> _findAll() {
    final screenshots = _screenshotBox.getAll();
    return _fromEntityList(screenshots);
  }

  // 私有方法 - 根据ID查找截图
  ScreenshotModel? _findById(int id) {
    final screenshot = _screenshotBox.get(id);
    return screenshot != null ? ScreenshotModel.fromEntity(screenshot) : null;
  }

  // 私有方法 - 根据路径查找截图
  ScreenshotModel? _findByPath(String path) {
    final query = _screenshotBox.query(Screenshot_.path.equals(path)).build();
    final screenshot = query.findFirst();
    query.close();
    return screenshot != null ? ScreenshotModel.fromEntity(screenshot) : null;
  }

  // 私有方法 - 保存截图
  Future<int> _save(ScreenshotModel model) async {
    if (model._screenshot == null) return 0;
    return await _screenshotBox.putAsync(model._screenshot!);
  }

  // 私有方法 - 删除截图
  bool _delete(int id) {
    return _screenshotBox.remove(id);
  }

  // 私有方法 - 将实体列表转换为模型列表
  List<ScreenshotModel> _fromEntityList(List<Screenshot> screenshots) {
    return screenshots.map((screenshot) => ScreenshotModel.fromEntity(screenshot)).toList();
  }
}
