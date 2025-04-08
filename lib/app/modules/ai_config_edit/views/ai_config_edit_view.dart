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
        backgroundColor: colorScheme.surface,
        actions: [
          // 保存按钮
          TextButton(
            onPressed: () async {
              final success = await controller.saveConfig();
              if (success) Get.back(result: true);
            },
            child: Text('保存'),
          ),
        ],
      ),
      body: SafeArea(child: _buildContent(context)),
    );
  }

  /// 构建内容区域
  Widget _buildContent(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(26, 16, 26, 16),
      children: [
        // 配置名称
        _buildSection(title: "配置名称", icon: Icons.text_fields, child: _buildNameField(context)),

        const SizedBox(height: 16),

        // AI提供商选择
        _buildSection(title: "AI服务提供商", icon: Icons.cloud, child: _buildApiProviderSelection(context)),

        const SizedBox(height: 16),

        // 模型名称
        _buildSection(
          title: "模型名称",
          icon: Icons.smart_toy,
          child: Obx(
            () =>
                controller.selectedApiPresetIndex.value == controller.apiPresets.length - 1 ||
                        controller.availableModels.isEmpty
                    ? _buildModelNameTextField(context)
                    : _buildModelNameDropdown(context),
          ),
        ),

        const SizedBox(height: 16),

        // API令牌
        _buildSection(title: "API令牌", icon: Icons.vpn_key, child: _buildApiTokenField(context)),

        const SizedBox(height: 16),

        // 自定义API地址
        Obx(() {
          if (controller.selectedApiPresetIndex.value == controller.apiPresets.length - 1) {
            return Column(
              children: [
                _buildSection(title: "自定义API地址", icon: Icons.link, child: _buildCustomApiAddressField(context)),
                const SizedBox(height: 16),
              ],
            );
          }
          return const SizedBox.shrink();
        }),

        // 继承通用配置选项（仅非通用配置显示）
        Obx(() {
          if (controller.selectedFunctionType.value != 0) {
            return _buildInheritFromGeneralSection(context);
          }
          return const SizedBox.shrink();
        }),
      ],
    );
  }

  /// 构建名称输入框
  Widget _buildNameField(BuildContext context) {
    return TextField(controller: controller.nameController, decoration: InputDecoration(hintText: "输入配置名称"));
  }

  /// 构建继承通用配置部分
  Widget _buildInheritFromGeneralSection(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSectionHeader("继承通用配置", Icons.settings_backup_restore, context),
        const SizedBox(height: 8),
        Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: () => controller.inheritFromGeneralController.toggle(),
            borderRadius: BorderRadius.circular(12),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              decoration: BoxDecoration(
                border: Border.all(color: colorScheme.outline.withAlpha(77)),
                borderRadius: BorderRadius.circular(12),
                color: colorScheme.surface,
              ),
              child: Row(
                children: [
                  Expanded(child: Text("启用此选项将从通用配置继承设置", style: textTheme.bodyMedium)),
                  Obx(
                    () => Switch(
                      value: controller.inheritFromGeneralController.value,
                      onChanged: (value) => controller.inheritFromGeneralController.value = value,
                      activeColor: colorScheme.primary,
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
      children: [_buildSectionHeader(title, icon, Get.context!), const SizedBox(height: 8), child],
    );
  }

  /// 构建区域头部
  Widget _buildSectionHeader(String title, IconData icon, BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Row(
      children: [
        Icon(icon, size: 18, color: colorScheme.primary),
        const SizedBox(width: 8),
        Text(title, style: textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w500)),
      ],
    );
  }

  /// 构建API提供商选择框
  Widget _buildApiProviderSelection(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return InkWell(
      onTap: () => _showApiProviderBottomSheet(context),
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: colorScheme.outline.withAlpha(77)),
          color: colorScheme.surface,
        ),
        child: Obx(
          () => Row(
            children: [
              Expanded(
                child: Text(
                  controller.apiPresets[controller.selectedApiPresetIndex.value]['name'],
                  style: textTheme.bodyMedium,
                ),
              ),
              Icon(Icons.arrow_drop_down, color: colorScheme.onSurface),
            ],
          ),
        ),
      ),
    );
  }

  /// 显示API提供商底部选择器
  void _showApiProviderBottomSheet(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder:
          (context) => Container(
            padding: const EdgeInsets.fromLTRB(16, 24, 16, 24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(left: 16, bottom: 16),
                  child: Text('选择AI服务提供商', style: textTheme.titleMedium),
                ),
                const Divider(),
                ListView.builder(
                  shrinkWrap: true,
                  itemCount: controller.apiPresets.length,
                  itemBuilder: (context, index) {
                    return ListTile(
                      title: Text(controller.apiPresets[index]['name']),
                      leading: Radio<int>(
                        value: index,
                        groupValue: controller.selectedApiPresetIndex.value,
                        onChanged: (value) {
                          controller.selectedApiPresetIndex.value = value!;
                          // 确保选中了可用的模型
                          if (controller.availableModels.isNotEmpty && controller.selectedModelIndex.value < 0) {
                            controller.selectedModelIndex.value = 0;
                          }
                          Navigator.pop(context);
                        },
                        activeColor: colorScheme.primary,
                      ),
                      onTap: () {
                        controller.selectedApiPresetIndex.value = index;
                        // 确保选中了可用的模型
                        if (controller.availableModels.isNotEmpty && controller.selectedModelIndex.value < 0) {
                          controller.selectedModelIndex.value = 0;
                        }
                        Navigator.pop(context);
                      },
                    );
                  },
                ),
              ],
            ),
          ),
    );
  }

  /// 构建模型名称文本输入框
  Widget _buildModelNameTextField(BuildContext context) {
    return TextField(controller: controller.modelNameController, decoration: InputDecoration(hintText: "输入模型名称"));
  }

  /// 构建模型名称下拉框
  Widget _buildModelNameDropdown(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    // 确保有有效的选中索引
    if (controller.availableModels.isNotEmpty &&
        (controller.selectedModelIndex.value < 0 ||
            controller.selectedModelIndex.value >= controller.availableModels.length)) {
      // 修复：如果模型索引无效但有可用模型，设置为第一个模型
      controller.selectedModelIndex.value = 0;
    }

    return InkWell(
      onTap: () => _showModelSelectionBottomSheet(context),
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: colorScheme.outline.withAlpha(77)),
          color: colorScheme.surface,
        ),
        child: Obx(
          () => Row(
            children: [
              Expanded(
                child: Text(
                  controller.selectedModelIndex.value >= 0 && controller.availableModels.isNotEmpty
                      ? controller.availableModels[controller.selectedModelIndex.value]
                      : '请选择模型',
                  style: textTheme.bodyMedium,
                ),
              ),
              Icon(Icons.arrow_drop_down, color: colorScheme.onSurface),
            ],
          ),
        ),
      ),
    );
  }

  /// 显示模型选择底部弹窗
  void _showModelSelectionBottomSheet(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    if (controller.availableModels.isEmpty) {
      Get.snackbar(
        '提示',
        '当前API提供商没有可用模型',
        snackPosition: SnackPosition.top,
        backgroundColor: Colors.orange.withAlpha(200),
      );
      return;
    }

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder:
          (context) => Container(
            padding: const EdgeInsets.fromLTRB(16, 24, 16, 24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(left: 16, bottom: 16),
                  child: Text('选择模型', style: textTheme.titleMedium),
                ),
                const Divider(),
                ListView.builder(
                  shrinkWrap: true,
                  physics: const ClampingScrollPhysics(),
                  itemCount: controller.availableModels.length,
                  itemBuilder: (context, index) {
                    return ListTile(
                      title: Text(controller.availableModels[index]),
                      leading: Radio<int>(
                        value: index,
                        groupValue: controller.selectedModelIndex.value,
                        onChanged: (value) {
                          controller.selectedModelIndex.value = value!;
                          Navigator.pop(context);
                        },
                        activeColor: colorScheme.primary,
                      ),
                      onTap: () {
                        controller.selectedModelIndex.value = index;
                        Navigator.pop(context);
                      },
                    );
                  },
                ),
              ],
            ),
          ),
    );
  }

  /// 构建API令牌输入框
  Widget _buildApiTokenField(BuildContext context) {
    return TextField(
      controller: controller.apiTokenController,
      obscureText: true,
      decoration: InputDecoration(hintText: "输入API密钥"),
    );
  }

  /// 构建自定义API地址输入框
  Widget _buildCustomApiAddressField(BuildContext context) {
    return TextField(
      controller: controller.apiAddressController,
      onChanged: (value) => controller.customApiAddress.value = value,
      decoration: InputDecoration(hintText: "例如: https://api.yourservice.com"),
    );
  }
}
