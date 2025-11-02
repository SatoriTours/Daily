import 'package:daily_satori/app/models/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/ai_config.dart';

/// AI配置模型类
class AIConfigModel extends EntityModel<AIConfig> {
  AIConfigModel(super.entity);

  factory AIConfigModel.create({
    int id = 0,
    required String name,
    required String apiAddress,
    required String apiToken,
    required String modelName,
    int functionType = 0,
    bool inheritFromGeneral = false,
    bool isDefault = false,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return AIConfigModel(
      AIConfig(
        id: id,
        name: name,
        apiAddress: apiAddress,
        apiToken: apiToken,
        modelName: modelName,
        functionType: functionType,
        inheritFromGeneral: inheritFromGeneral,
        isDefault: isDefault,
        createdAt: createdAt ?? DateTime.now(),
        updatedAt: updatedAt ?? DateTime.now(),
      ),
    );
  }

  AIConfig get config => entity;

  String get name => entity.name;
  set name(String value) => entity.name = value;

  String get apiAddress => entity.apiAddress;
  set apiAddress(String value) => entity.apiAddress = value;

  String get apiToken => entity.apiToken;
  set apiToken(String value) => entity.apiToken = value;

  String get modelName => entity.modelName;
  set modelName(String value) => entity.modelName = value;

  int get functionType => entity.functionType;
  set functionType(int value) => entity.functionType = value;

  bool get inheritFromGeneral => entity.inheritFromGeneral;
  set inheritFromGeneral(bool value) => entity.inheritFromGeneral = value;

  bool get isDefault => entity.isDefault;
  set isDefault(bool value) => entity.isDefault = value;

  void inheritFromConfig(AIConfigModel general) {
    if (!inheritFromGeneral) return;

    if (apiAddress.isEmpty) apiAddress = general.apiAddress;
    if (apiToken.isEmpty) apiToken = general.apiToken;
    if (modelName.isEmpty) modelName = general.modelName;
  }

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
