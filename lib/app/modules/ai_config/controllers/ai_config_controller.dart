import 'package:daily_satori/app/models/models.dart';
import 'package:daily_satori/app/objectbox/ai_config.dart';
import 'package:daily_satori/app/repositories/ai_config_repository.dart';
import 'package:daily_satori/app/services/ai_config_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// AI配置控制器
class AIConfigController extends GetxController {
  // MARK: - 可观察属性

  /// 配置列表
  final configs = <AIConfigModel>[].obs;

  /// 选中的功能类型
  final selectedFunctionType = 0.obs;

  /// 是否正在加载
  final isLoading = false.obs;

  // MARK: - 表单控制器
  final nameController = TextEditingController();
  final apiAddressController = TextEditingController();
  final apiTokenController = TextEditingController();
  final modelNameController = TextEditingController();
  final inheritFromGeneralController = false.obs;

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
  void loadConfigs() {
    isLoading.value = true;
    try {
      // 获取配置列表
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
  List<AIConfigModel> getConfigsByType(int type) {
    return configs.where((config) => config.functionType == type).toList();
  }

  /// 保存配置
  Future<bool> saveConfig(AIConfigModel config) async {
    try {
      // 保存配置
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

      // 刷新列表
      configs.refresh();
      return true;
    } catch (e, stackTrace) {
      logger.e("[AI配置控制器] 保存配置失败: $e", stackTrace: stackTrace);
      UIUtils.showError("保存配置失败: $e");
      return false;
    }
  }

  /// 删除配置
  Future<bool> deleteConfig(AIConfigModel config) async {
    try {
      // 不允许删除最后一个指定类型的配置
      final typeConfigs = getConfigsByType(config.functionType);
      if (typeConfigs.length <= 1 && config.functionType != 0) {
        UIUtils.showError("不能删除最后一个${AIConfigService.i.getFunctionTypeName(config.functionType)}配置");
        return false;
      }

      // 删除配置
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
  Future<bool> setAsDefault(AIConfigModel config) async {
    try {
      // 设置默认配置
      AIConfigRepository.setDefaultConfig(config.id, config.functionType);

      // 更新列表
      loadConfigs();
      return true;
    } catch (e, stackTrace) {
      logger.e("[AI配置控制器] 设置默认配置失败: $e", stackTrace: stackTrace);
      UIUtils.showError("设置默认配置失败: $e");
      return false;
    }
  }

  /// 创建新配置
  Future<void> createNewConfig() async {
    // 清空表单
    nameController.text = "";
    apiAddressController.text = "";
    apiTokenController.text = "";
    modelNameController.text = "";
    inheritFromGeneralController.value = (selectedFunctionType.value != 0);

    // 显示编辑对话框
    final result = await Get.dialog(
      Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: Container(
          width: Get.width * 0.9,
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(_getTypeIcon(selectedFunctionType.value), color: Get.theme.colorScheme.primary, size: 24),
                  const SizedBox(width: 12),
                  Text(
                    "新建${AIConfigService.i.getFunctionTypeName(selectedFunctionType.value)}配置",
                    style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                  ),
                ],
              ),
              const SizedBox(height: 24),
              _buildTextField(controller: nameController, icon: Icons.title, label: "配置名称", hintText: "输入配置名称"),
              const SizedBox(height: 16),
              if (selectedFunctionType.value != 0)
                Obx(
                  () => Material(
                    color: Colors.transparent,
                    child: InkWell(
                      onTap: () => inheritFromGeneralController.value = !inheritFromGeneralController.value,
                      borderRadius: BorderRadius.circular(12),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                        decoration: BoxDecoration(
                          border: Border.all(color: Get.theme.colorScheme.outline.withOpacity(0.3)),
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
                ),
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
              const SizedBox(height: 32),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: () => Get.back(result: false),
                    child: Text("取消", style: TextStyle(color: Get.theme.colorScheme.onSurface.withOpacity(0.7))),
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
                    child: const Text("保存配置"),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );

    if (result == true) {
      // 创建新配置
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

      // 保存配置
      final success = await saveConfig(config);
      if (success) {
        UIUtils.showSuccess("创建配置成功");
      }
    }
  }

  /// 编辑配置
  Future<void> editConfig(AIConfigModel config) async {
    // 填充表单
    nameController.text = config.name;
    apiAddressController.text = config.apiAddress;
    apiTokenController.text = config.apiToken;
    modelNameController.text = config.modelName;
    inheritFromGeneralController.value = config.inheritFromGeneral;

    // 显示编辑对话框
    final result = await Get.dialog(
      Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: Container(
          width: Get.width * 0.9,
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(Icons.edit, color: Get.theme.colorScheme.primary, size: 24),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      "编辑 ${config.name}",
                      style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),
              _buildTextField(controller: nameController, icon: Icons.title, label: "配置名称", hintText: "输入配置名称"),
              const SizedBox(height: 16),
              if (config.functionType != 0)
                Obx(
                  () => Material(
                    color: Colors.transparent,
                    child: InkWell(
                      onTap: () => inheritFromGeneralController.value = !inheritFromGeneralController.value,
                      borderRadius: BorderRadius.circular(12),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                        decoration: BoxDecoration(
                          border: Border.all(color: Get.theme.colorScheme.outline.withOpacity(0.3)),
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
                ),
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
              const SizedBox(height: 32),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: () => Get.back(result: false),
                    child: Text("取消", style: TextStyle(color: Get.theme.colorScheme.onSurface.withOpacity(0.7))),
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
                    child: const Text("保存修改"),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );

    if (result == true) {
      // 更新配置
      config.name = nameController.text;
      config.apiAddress = apiAddressController.text;
      config.apiToken = apiTokenController.text;
      config.modelName = modelNameController.text;
      config.inheritFromGeneral = inheritFromGeneralController.value;

      // 保存配置
      final success = await saveConfig(config);
      if (success) {
        UIUtils.showSuccess("更新配置成功");
      }
    }
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
          borderSide: BorderSide(color: Get.theme.colorScheme.outline.withOpacity(0.3)),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: Get.theme.colorScheme.outline.withOpacity(0.3)),
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
