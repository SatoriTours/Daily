import 'package:daily_satori/app/models/base_model.dart';
import 'package:daily_satori/app/objectbox/ai_config.dart';

/// AI配置模型类
///
/// 对ObjectBox实体类AIConfig的封装
class AIConfigModel extends BaseModel<AIConfig> {
  /// 构造函数
  AIConfigModel(super.entity);

  /// 从AIConfig创建模型
  factory AIConfigModel.fromConfig(AIConfig config) {
    return AIConfigModel(config);
  }

  /// 获取内部AIConfig实例（为了向后兼容）
  AIConfig get config => entity;

  /// ID
  @override
  int get id => entity.id;
  set id(int value) => entity.id = value;

  /// 配置名称
  String get name => entity.name;
  set name(String value) => entity.name = value;

  /// API地址
  String get apiAddress => entity.apiAddress;
  set apiAddress(String value) => entity.apiAddress = value;

  /// API令牌
  String get apiToken => entity.apiToken;
  set apiToken(String value) => entity.apiToken = value;

  /// 模型名称
  String get modelName => entity.modelName;
  set modelName(String value) => entity.modelName = value;

  /// 功能类型
  int get functionType => entity.functionType;
  set functionType(int value) => entity.functionType = value;

  /// 是否继承自通用配置
  bool get inheritFromGeneral => entity.inheritFromGeneral;
  set inheritFromGeneral(bool value) => entity.inheritFromGeneral = value;

  /// 是否为默认配置
  bool get isDefault => entity.isDefault;
  set isDefault(bool value) => entity.isDefault = value;

  /// 创建时间
  @override
  DateTime? get createdAt => entity.createdAt;
  @override
  set createdAt(DateTime? value) {
    if (value != null) entity.createdAt = value;
  }

  /// 更新时间
  @override
  DateTime? get updatedAt => entity.updatedAt;
  @override
  set updatedAt(DateTime? value) {
    if (value != null) entity.updatedAt = value;
  }

  /// 从另一个配置继承属性
  void inheritFromConfig(AIConfigModel general) {
    if (!inheritFromGeneral) return;

    if (apiAddress.isEmpty) apiAddress = general.apiAddress;
    if (apiToken.isEmpty) apiToken = general.apiToken;
    if (modelName.isEmpty) modelName = general.modelName;
  }

  /// 克隆配置
  AIConfigModel clone() {
    final newConfig = AIConfig(
      id: 0, // 新对象ID为0
      name: "$name - 副本",
      apiAddress: apiAddress,
      apiToken: apiToken,
      modelName: modelName,
      functionType: functionType,
      inheritFromGeneral: inheritFromGeneral,
      isDefault: false, // 副本不作为默认
      createdAt: DateTime.now(),
      updatedAt: DateTime.now(),
    );
    return AIConfigModel(newConfig);
  }

  /// 获取功能类型名称
  String get functionTypeName {
    switch (functionType) {
      case 0:
        return "通用配置";
      case 1:
        return "文章总结";
      case 2:
        return "书本解读";
      case 3:
        return "日记总结";

      default:
        return "未知";
    }
  }

  @override
  String toString() {
    return 'AIConfigModel{id: $id, name: $name, apiAddress: $apiAddress, modelName: $modelName, functionType: $functionType}';
  }
}
