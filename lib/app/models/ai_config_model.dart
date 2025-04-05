import 'package:daily_satori/app/objectbox/ai_config.dart';

/// AI配置模型类
///
/// 对ObjectBox实体类AIConfig的封装
class AIConfigModel {
  /// 内部AIConfig实例
  final AIConfig _aiConfig;

  /// 构造函数
  AIConfigModel(this._aiConfig);

  /// 从AIConfig创建模型
  factory AIConfigModel.fromConfig(AIConfig config) {
    return AIConfigModel(config);
  }

  /// 获取内部AIConfig实例
  AIConfig get config => _aiConfig;

  /// ID
  int get id => _aiConfig.id;
  set id(int value) => _aiConfig.id = value;

  /// 配置名称
  String get name => _aiConfig.name;
  set name(String value) => _aiConfig.name = value;

  /// API地址
  String get apiAddress => _aiConfig.apiAddress;
  set apiAddress(String value) => _aiConfig.apiAddress = value;

  /// API令牌
  String get apiToken => _aiConfig.apiToken;
  set apiToken(String value) => _aiConfig.apiToken = value;

  /// 模型名称
  String get modelName => _aiConfig.modelName;
  set modelName(String value) => _aiConfig.modelName = value;

  /// 功能类型
  int get functionType => _aiConfig.functionType;
  set functionType(int value) => _aiConfig.functionType = value;

  /// 是否继承自通用配置
  bool get inheritFromGeneral => _aiConfig.inheritFromGeneral;
  set inheritFromGeneral(bool value) => _aiConfig.inheritFromGeneral = value;

  /// 是否为默认配置
  bool get isDefault => _aiConfig.isDefault;
  set isDefault(bool value) => _aiConfig.isDefault = value;

  /// 创建时间
  DateTime get createdAt => _aiConfig.createdAt;
  set createdAt(DateTime value) => _aiConfig.createdAt = value;

  /// 更新时间
  DateTime get updatedAt => _aiConfig.updatedAt;
  set updatedAt(DateTime value) => _aiConfig.updatedAt = value;

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
