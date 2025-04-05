import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../controllers/ai_config_edit_controller.dart';

/// AI配置编辑页面
class AIConfigEditView extends GetView<AIConfigEditController> {
  const AIConfigEditView({super.key});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Scaffold(
      backgroundColor: colorScheme.surface,
      appBar: AppBar(
        title: Obx(() => Text(controller.isEditMode.value ? '编辑配置' : '新建配置')),
        centerTitle: true,
        elevation: 0,
        backgroundColor: colorScheme.surface,
        actions: [
          // 保存按钮
          TextButton(
            onPressed: () async {
              final success = await controller.saveConfig();
              if (success) {
                Get.back(result: true);
              }
            },
            child: Text('保存', style: TextStyle(color: colorScheme.primary, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
      body: _buildContent(context),
    );
  }

  /// 构建内容区域
  Widget _buildContent(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // 配置名称
        _buildSection(title: "配置名称", icon: Icons.text_fields, child: _buildNameField()),

        const SizedBox(height: 16),

        // AI提供商选择
        _buildSection(title: "AI服务提供商", icon: Icons.cloud, child: _buildApiProviderSelection()),

        const SizedBox(height: 16),

        // 模型名称
        _buildSection(
          title: "模型名称",
          icon: Icons.smart_toy,
          child: Obx(
            () =>
                controller.selectedApiPresetIndex.value == controller.apiPresets.length - 1 ||
                        controller.availableModels.isEmpty
                    ? _buildModelNameTextField()
                    : _buildModelNameDropdown(),
          ),
        ),

        const SizedBox(height: 16),

        // API令牌
        _buildSection(title: "API令牌", icon: Icons.vpn_key, child: _buildApiTokenField()),

        const SizedBox(height: 16),

        // 自定义API地址
        Obx(() {
          if (controller.selectedApiPresetIndex.value == controller.apiPresets.length - 1) {
            return Column(
              children: [
                _buildSection(title: "自定义API地址", icon: Icons.link, child: _buildCustomApiAddressField()),
                const SizedBox(height: 16),
              ],
            );
          }
          return const SizedBox.shrink();
        }),

        // 继承通用配置选项（仅非通用配置显示）
        Obx(() {
          if (controller.selectedFunctionType.value != 0) {
            return _buildInheritFromGeneralSection();
          }
          return const SizedBox.shrink();
        }),
      ],
    );
  }

  /// 构建名称输入框
  Widget _buildNameField() {
    return TextField(
      controller: controller.nameController,
      decoration: InputDecoration(
        hintText: "输入配置名称",
        hintStyle: TextStyle(color: Get.theme.colorScheme.onSurfaceVariant.withAlpha(150), fontSize: 14),
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

  /// 构建继承通用配置部分
  Widget _buildInheritFromGeneralSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSectionHeader("继承通用配置", Icons.settings_backup_restore),
        const SizedBox(height: 8),
        Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: () => controller.inheritFromGeneralController.toggle(),
            borderRadius: BorderRadius.circular(12),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              decoration: BoxDecoration(
                border: Border.all(color: Get.theme.colorScheme.outline.withAlpha(77)),
                borderRadius: BorderRadius.circular(12),
                color: Get.theme.colorScheme.surface,
              ),
              child: Row(
                children: [
                  const Expanded(child: Text("启用此选项将从通用配置继承设置", style: TextStyle(fontSize: 14))),
                  Obx(
                    () => Switch(
                      value: controller.inheritFromGeneralController.value,
                      onChanged: (value) => controller.inheritFromGeneralController.value = value,
                      activeColor: Get.theme.colorScheme.primary,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),
      ],
    );
  }

  /// 构建区域
  Widget _buildSection({required String title, required IconData icon, required Widget child}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [_buildSectionHeader(title, icon), const SizedBox(height: 8), child],
    );
  }

  /// 构建区域头部
  Widget _buildSectionHeader(String title, IconData icon) {
    return Row(
      children: [
        Icon(icon, size: 18, color: Get.theme.colorScheme.primary),
        const SizedBox(width: 8),
        Text(
          title,
          style: TextStyle(fontSize: 15, fontWeight: FontWeight.w500, color: Get.theme.colorScheme.onSurface),
        ),
      ],
    );
  }

  /// 构建API提供商选择框
  Widget _buildApiProviderSelection() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 0),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Get.theme.colorScheme.outline.withAlpha(77)),
        color: Get.theme.colorScheme.surface,
      ),
      child: Obx(
        () => DropdownButtonHideUnderline(
          child: ButtonTheme(
            alignedDropdown: true,
            child: DropdownButton<int>(
              value: controller.selectedApiPresetIndex.value,
              isExpanded: true,
              borderRadius: BorderRadius.circular(12),
              padding: const EdgeInsets.symmetric(horizontal: 16),
              dropdownColor: Get.theme.colorScheme.surface,
              items: List.generate(controller.apiPresets.length, (index) {
                return DropdownMenuItem(value: index, child: Text(controller.apiPresets[index]['name']));
              }),
              onChanged: (value) {
                if (value != null) {
                  controller.selectedApiPresetIndex.value = value;
                }
              },
            ),
          ),
        ),
      ),
    );
  }

  /// 构建模型名称文本输入框
  Widget _buildModelNameTextField() {
    return TextField(
      controller: controller.modelNameController,
      decoration: InputDecoration(
        hintText: "输入模型名称",
        hintStyle: TextStyle(color: Get.theme.colorScheme.onSurfaceVariant.withAlpha(150), fontSize: 14),
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

  /// 构建模型名称下拉框
  Widget _buildModelNameDropdown() {
    // 确保有有效的选中索引
    int currentIndex = 0;
    if (controller.selectedModelIndex.value >= 0 &&
        controller.selectedModelIndex.value < controller.availableModels.length) {
      currentIndex = controller.selectedModelIndex.value;
    }

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 0),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Get.theme.colorScheme.outline.withAlpha(77)),
        color: Get.theme.colorScheme.surface,
      ),
      child: DropdownButtonHideUnderline(
        child: ButtonTheme(
          alignedDropdown: true,
          child: DropdownButton<int>(
            value: currentIndex,
            isExpanded: true,
            borderRadius: BorderRadius.circular(12),
            padding: const EdgeInsets.symmetric(horizontal: 16),
            dropdownColor: Get.theme.colorScheme.surface,
            items: List.generate(controller.availableModels.length, (index) {
              return DropdownMenuItem(value: index, child: Text(controller.availableModels[index]));
            }),
            onChanged: (value) {
              if (value != null) {
                controller.selectedModelIndex.value = value;
              }
            },
          ),
        ),
      ),
    );
  }

  /// 构建API令牌输入框
  Widget _buildApiTokenField() {
    return TextField(
      controller: controller.apiTokenController,
      obscureText: true,
      decoration: InputDecoration(
        hintText: "输入API密钥",
        hintStyle: TextStyle(color: Get.theme.colorScheme.onSurfaceVariant.withAlpha(150), fontSize: 14),
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

  /// 构建自定义API地址输入框
  Widget _buildCustomApiAddressField() {
    return TextField(
      controller: controller.apiAddressController,
      onChanged: (value) => controller.customApiAddress.value = value,
      decoration: InputDecoration(
        hintText: "例如: https://api.yourservice.com",
        hintStyle: TextStyle(color: Get.theme.colorScheme.onSurfaceVariant.withAlpha(150), fontSize: 14),
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
}
