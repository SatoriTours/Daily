import 'package:daily_satori/app/models/models.dart';
import 'package:daily_satori/app/objectbox/ai_config.dart';
import 'package:daily_satori/app/repositories/ai_config_repository.dart';
import 'package:daily_satori/app/services/ai_config_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// AI配置控制器
///
/// 负责管理AI配置的CRUD操作，包括：
/// - 加载配置列表
/// - 创建新配置
/// - 编辑现有配置
/// - 删除配置
/// - 设置默认配置
class AIConfigController extends GetxController {
  // MARK: - 可观察属性

  /// 配置列表
  final RxList<AIConfigModel> configs = <AIConfigModel>[].obs;

  /// 选中的功能类型
  final RxInt selectedFunctionType = 0.obs;

  /// 是否正在加载
  final RxBool isLoading = false.obs;

  // MARK: - 表单控制器

  /// 配置名称输入控制器
  final TextEditingController nameController = TextEditingController();

  /// API地址输入控制器
  final TextEditingController apiAddressController = TextEditingController();

  /// API令牌输入控制器
  final TextEditingController apiTokenController = TextEditingController();

  /// 模型名称输入控制器
  final TextEditingController modelNameController = TextEditingController();

  /// 是否继承通用配置
  final RxBool inheritFromGeneralController = false.obs;

  // MARK: - 生命周期方法

  @override
  void onInit() {
    super.onInit();
    loadConfigs();
  }

  @override
  void onClose() {
    nameController.dispose();
    apiAddressController.dispose();
    apiTokenController.dispose();
    modelNameController.dispose();
    super.onClose();
  }

  // MARK: - 功能方法

  /// 加载配置列表
  ///
  /// 从数据库加载所有AI配置，并更新[configs]列表。
  /// 如果加载失败，会显示错误提示。
  void loadConfigs() {
    isLoading.value = true;
    try {
      final configsList = AIConfigRepository.getAllAIConfigs();
      configs.value = configsList;
      logger.i("[AI配置控制器] 加载配置列表成功: ${configsList.length}个配置");
    } catch (e, stackTrace) {
      logger.e("[AI配置控制器] 加载配置列表失败: $e", stackTrace: stackTrace);
      UIUtils.showError("加载配置失败: $e");
    } finally {
      isLoading.value = false;
    }
  }

  /// 获取特定功能类型的配置
  ///
  /// [type] 功能类型ID
  /// 返回指定类型的配置列表
  List<AIConfigModel> getConfigsByType(int type) {
    return configs.where((config) => config.functionType == type).toList();
  }

  /// 保存配置
  ///
  /// [config] 要保存的配置
  /// 返回是否保存成功
  Future<bool> saveConfig(AIConfigModel config) async {
    try {
      if (config.id == 0) {
        // 新建配置
        final id = AIConfigRepository.addAIConfig(config);
        config.id = id;
        configs.add(config);
      } else {
        // 更新配置
        AIConfigRepository.updateAIConfig(config);
        final index = configs.indexWhere((c) => c.id == config.id);
        if (index >= 0) {
          configs[index] = config;
        }
      }

      configs.refresh();
      return true;
    } catch (e, stackTrace) {
      logger.e("[AI配置控制器] 保存配置失败: $e", stackTrace: stackTrace);
      UIUtils.showError("保存配置失败: $e");
      return false;
    }
  }

  /// 删除配置
  ///
  /// [config] 要删除的配置
  /// 返回是否删除成功
  Future<bool> deleteConfig(AIConfigModel config) async {
    try {
      // 不允许删除最后一个指定类型的配置
      final typeConfigs = getConfigsByType(config.functionType);
      if (typeConfigs.length <= 1 && config.functionType != 0) {
        UIUtils.showError("不能删除最后一个${AIConfigService.i.getFunctionTypeName(config.functionType)}配置");
        return false;
      }

      final result = AIConfigRepository.removeAIConfig(config.id);
      if (result) {
        configs.removeWhere((c) => c.id == config.id);
        configs.refresh();
      }
      return result;
    } catch (e, stackTrace) {
      logger.e("[AI配置控制器] 删除配置失败: $e", stackTrace: stackTrace);
      UIUtils.showError("删除配置失败: $e");
      return false;
    }
  }

  /// 设置默认配置
  ///
  /// [config] 要设置为默认的配置
  /// 返回是否设置成功
  Future<bool> setAsDefault(AIConfigModel config) async {
    try {
      AIConfigRepository.setDefaultConfig(config.id, config.functionType);
      loadConfigs();
      return true;
    } catch (e, stackTrace) {
      logger.e("[AI配置控制器] 设置默认配置失败: $e", stackTrace: stackTrace);
      UIUtils.showError("设置默认配置失败: $e");
      return false;
    }
  }

  // MARK: - UI相关方法

  /// 创建新配置
  Future<void> createNewConfig() async {
    _resetForm();
    inheritFromGeneralController.value = (selectedFunctionType.value != 0);

    final result = await _showConfigDialog(
      "新建${AIConfigService.i.getFunctionTypeName(selectedFunctionType.value)}配置",
      _getTypeIcon(selectedFunctionType.value),
    );

    if (result == true) {
      final config = AIConfigModel(
        AIConfig(
          name: nameController.text,
          apiAddress: apiAddressController.text,
          apiToken: apiTokenController.text,
          modelName: modelNameController.text,
          functionType: selectedFunctionType.value,
          inheritFromGeneral: inheritFromGeneralController.value,
        ),
      );

      final success = await saveConfig(config);
      if (success) {
        UIUtils.showSuccess("创建配置成功");
      }
    }
  }

  /// 编辑配置
  ///
  /// [config] 要编辑的配置
  Future<void> editConfig(AIConfigModel config) async {
    _fillForm(config);

    final result = await _showConfigDialog("编辑 ${config.name}", Icons.edit);

    if (result == true) {
      config
        ..name = nameController.text
        ..apiAddress = apiAddressController.text
        ..apiToken = apiTokenController.text
        ..modelName = modelNameController.text
        ..inheritFromGeneral = inheritFromGeneralController.value;

      final success = await saveConfig(config);
      if (success) {
        UIUtils.showSuccess("更新配置成功");
      }
    }
  }

  // MARK: - 私有辅助方法

  /// 重置表单
  void _resetForm() {
    nameController.text = "";
    apiAddressController.text = "";
    apiTokenController.text = "";
    modelNameController.text = "";
  }

  /// 填充表单
  ///
  /// [config] 用于填充表单的配置
  void _fillForm(AIConfigModel config) {
    nameController.text = config.name;
    apiAddressController.text = config.apiAddress;
    apiTokenController.text = config.apiToken;
    modelNameController.text = config.modelName;
    inheritFromGeneralController.value = config.inheritFromGeneral;
  }

  /// 显示配置对话框
  ///
  /// [title] 对话框标题
  /// [icon] 对话框图标
  Future<bool?> _showConfigDialog(String title, IconData icon) {
    return Get.dialog<bool>(
      Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: Container(
          width: Get.width * 0.9,
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildDialogHeader(title, icon),
              const SizedBox(height: 24),
              _buildDialogContent(),
              const SizedBox(height: 32),
              _buildDialogActions(),
            ],
          ),
        ),
      ),
    );
  }

  /// 构建对话框头部
  Widget _buildDialogHeader(String title, IconData icon) {
    return Row(
      children: [
        Icon(icon, color: Get.theme.colorScheme.primary, size: 24),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            title,
            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ],
    );
  }

  /// 构建对话框内容
  Widget _buildDialogContent() {
    return Column(
      children: [
        _buildTextField(controller: nameController, icon: Icons.title, label: "配置名称", hintText: "输入配置名称"),
        const SizedBox(height: 16),
        if (selectedFunctionType.value != 0) _buildInheritFromGeneralSwitch(),
        const SizedBox(height: 16),
        _buildTextField(
          controller: apiAddressController,
          icon: Icons.link,
          label: "API地址",
          hintText: "例如: https://api.openai.com/v1",
        ),
        const SizedBox(height: 16),
        _buildTextField(
          controller: apiTokenController,
          icon: Icons.vpn_key,
          label: "API令牌",
          hintText: "输入API密钥",
          isPassword: true,
        ),
        const SizedBox(height: 16),
        _buildTextField(
          controller: modelNameController,
          icon: Icons.smart_toy,
          label: "模型名称",
          hintText: "例如: gpt-3.5-turbo",
        ),
      ],
    );
  }

  /// 构建对话框按钮
  Widget _buildDialogActions() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        TextButton(
          onPressed: () => Get.back(result: false),
          child: Text("取消", style: TextStyle(color: Get.theme.colorScheme.onSurface.withAlpha(179))),
        ),
        const SizedBox(width: 12),
        ElevatedButton(
          onPressed: () => Get.back(result: true),
          style: ElevatedButton.styleFrom(
            backgroundColor: Get.theme.colorScheme.primary,
            foregroundColor: Get.theme.colorScheme.onPrimary,
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
          child: const Text("保存"),
        ),
      ],
    );
  }

  /// 构建继承通用配置开关
  Widget _buildInheritFromGeneralSwitch() {
    return Obx(
      () => Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: () => inheritFromGeneralController.value = !inheritFromGeneralController.value,
          borderRadius: BorderRadius.circular(12),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
            decoration: BoxDecoration(
              border: Border.all(color: Get.theme.colorScheme.outline.withAlpha(77)),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                Icon(Icons.settings_backup_restore, color: Get.theme.colorScheme.primary, size: 22),
                const SizedBox(width: 12),
                const Expanded(child: Text("继承通用配置", style: TextStyle(fontSize: 16))),
                Checkbox(
                  value: inheritFromGeneralController.value,
                  onChanged: (value) => inheritFromGeneralController.value = value ?? false,
                  activeColor: Get.theme.colorScheme.primary,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// 构建表单输入框
  Widget _buildTextField({
    required TextEditingController controller,
    required IconData icon,
    required String label,
    required String hintText,
    bool isPassword = false,
  }) {
    return TextField(
      controller: controller,
      obscureText: isPassword,
      decoration: InputDecoration(
        labelText: label,
        hintText: hintText,
        prefixIcon: Icon(icon),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: Get.theme.colorScheme.outline.withAlpha(77)),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: Get.theme.colorScheme.outline.withAlpha(77)),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: Get.theme.colorScheme.primary, width: 2),
        ),
        filled: true,
        fillColor: Get.theme.colorScheme.surface,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      ),
    );
  }

  /// 获取类型图标
  IconData _getTypeIcon(int type) {
    switch (type) {
      case 0:
        return Icons.settings;
      case 1:
        return Icons.article;
      case 2:
        return Icons.book;
      case 3:
        return Icons.edit_note;
      default:
        return Icons.settings;
    }
  }

  /// 克隆配置
  Future<void> cloneConfig(AIConfigModel config) async {
    try {
      // 克隆配置
      final newConfig = config.clone();

      // 保存配置
      final success = await saveConfig(newConfig);
      if (success) {
        UIUtils.showSuccess("克隆配置成功");
      }
    } catch (e, stackTrace) {
      logger.e("[AI配置控制器] 克隆配置失败: $e", stackTrace: stackTrace);
      UIUtils.showError("克隆配置失败: $e");
    }
  }
}
