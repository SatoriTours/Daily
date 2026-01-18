/// AI Config Edit Controller Provider
///
/// AI配置编辑控制器，负责AI配置的编辑和创建操作。

library;

import 'package:freezed_annotation/freezed_annotation.dart';

import 'package:daily_satori/app_exports.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/objectbox/ai_config.dart';
import 'package:daily_satori/app/pages/ai_config/models/ai_config_types.dart';
import 'package:daily_satori/app/pages/ai_config/providers/ai_config_controller_provider.dart';

part 'ai_config_edit_controller_provider.freezed.dart';
part 'ai_config_edit_controller_provider.g.dart';

/// AIConfigEditController 状态
@freezed
abstract class AIConfigEditControllerState with _$AIConfigEditControllerState {
  const factory AIConfigEditControllerState({
    @Default(false) bool isLoading,
    @Default(false) bool isSaving,
    @Default('') String errorMessage,
    AIConfig? config,
    @Default('') String name,
    @Default('') String apiAddress,
    @Default('') String apiToken,
    @Default('') String modelName,
    @Default(0) int functionType,
    @Default(false) bool inheritFromGeneral,
    @Default(false) bool isDefault,
  }) = _AIConfigEditControllerState;
}

/// AIConfigEditController Provider
@riverpod
class AIConfigEditController extends _$AIConfigEditController {
  @override
  AIConfigEditControllerState build() => const AIConfigEditControllerState();

  void loadConfig(AIConfig? config) {
    if (config == null) {
      state = const AIConfigEditControllerState();
    } else {
      state = state.copyWith(
        config: config,
        name: config.name,
        apiAddress: config.apiAddress,
        apiToken: config.apiToken,
        modelName: config.modelName,
        functionType: config.functionType,
        inheritFromGeneral: config.inheritFromGeneral,
        isDefault: config.isDefault,
      );
    }
  }

  void updateName(String name) => state = state.copyWith(name: name);
  void updateApiAddress(String address) => state = state.copyWith(apiAddress: address);
  void updateApiToken(String token) => state = state.copyWith(apiToken: token);
  void updateModelName(String modelName) => state = state.copyWith(modelName: modelName);
  void updateFunctionType(int type) => state = state.copyWith(functionType: type);

  void setInheritFromGeneral(bool inherit) {
    if (inherit) {
      // 切换到继承模式时，清空独立配置字段
      state = state.copyWith(inheritFromGeneral: true, apiAddress: '', apiToken: '', modelName: '');
    } else {
      state = state.copyWith(inheritFromGeneral: false);
    }
  }

  void resetConfig() => loadConfig(state.config);

  Future<bool> saveConfig() async {
    if (state.name.trim().isEmpty) {
      UIUtils.showError('ai_config.name_required'.t);
      return false;
    }

    if (!state.inheritFromGeneral) {
      if (state.apiAddress.trim().isEmpty) {
        UIUtils.showError('ai_config.api_address_required'.t);
        return false;
      }
      if (state.apiToken.trim().isEmpty) {
        UIUtils.showError('ai_config.api_token_required'.t);
        return false;
      }
    }

    state = state.copyWith(isSaving: true, errorMessage: '');
    try {
      final configModel = AIConfigModel.create(
        id: state.config?.id ?? 0,
        name: state.name.trim(),
        apiAddress: state.apiAddress.trim(),
        apiToken: state.apiToken.trim(),
        modelName: state.modelName.trim(),
        functionType: state.functionType,
        inheritFromGeneral: state.inheritFromGeneral,
        isDefault: state.isDefault,
      );

      AIConfigRepository.i.save(configModel);
      state = state.copyWith(isSaving: false);
      ref.invalidate(aIConfigControllerProvider);
      AppNavigation.back();
      return true;
    } catch (e) {
      logger.e('[AIConfigEditController] 保存配置失败', error: e);
      state = state.copyWith(isSaving: false, errorMessage: e.toString());
      UIUtils.showError('ai_config.save_failed'.t);
      return false;
    }
  }

  String get pageTitle => state.config == null ? 'ai_config.create_title'.t : 'ai_config.edit_title'.t;
  bool get isSystemConfig => state.functionType == AIConfigTypes.general;
  bool get isSpecialConfig => state.functionType != AIConfigTypes.general;
  bool get isCustomApiAddress => state.apiAddress.isNotEmpty;

  bool get isFormValid {
    if (state.name.trim().isEmpty) return false;
    if (!state.inheritFromGeneral) {
      if (state.apiAddress.trim().isEmpty || state.apiToken.trim().isEmpty) {
        return false;
      }
    }
    return true;
  }

  /// 从 PluginService 获取 AI 模型配置
  List<AiModel> get _aiModels => PluginService.i.aiModels;

  /// 获取当前选中服务商的可用模型列表
  List<String> get availableModels {
    // 根据当前选中的 apiAddress 查找对应的模型列表
    final currentAddress = state.apiAddress;
    if (currentAddress.isEmpty) return [];

    for (final model in _aiModels) {
      if (model.apiAddress == currentAddress) {
        return model.models;
      }
    }
    return []; // 自定义地址时返回空列表，用户需要手动输入
  }

  /// 获取所有 AI 服务提供商名称
  List<String> get apiProviderNames => _aiModels.map((m) => m.name).toList();

  /// 获取所有 AI 服务提供商的 API 地址
  List<String> get apiPresets => _aiModels.map((m) => m.apiAddress).toList();

  String getProviderNameByUrl(String url) {
    if (url.isEmpty) return '选择供应商';
    for (final model in _aiModels) {
      if (model.apiAddress == url) {
        return model.name;
      }
    }
    return '自定义';
  }

  bool get isCustomApiUrl {
    if (state.apiAddress.isEmpty) return false;
    // 检查是否是 "自定义" 供应商（apiAddress 为空的那个）
    for (final model in _aiModels) {
      if (model.name == '自定义' && model.apiAddress.isEmpty) {
        continue; // 跳过 "自定义" 项
      }
      if (model.apiAddress == state.apiAddress) {
        return false; // 找到匹配的预设，不是自定义
      }
    }
    return true; // 没有匹配的预设，是自定义地址
  }
}
