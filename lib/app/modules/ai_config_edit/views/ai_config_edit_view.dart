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
    final textTheme = AppTheme.getTextTheme(context);

    return Scaffold(
      backgroundColor: colorScheme.surface,
      appBar: AppBar(
        title: Text(
          controller.isEditMode ? '编辑配置' : '新建配置',
          style: textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600),
        ),
        centerTitle: true,
        backgroundColor: colorScheme.surface,
        actions: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: TextButton(
              onPressed: () async {
                final success = await controller.saveConfig();
                if (success) Get.back(result: true);
              },
              child: Text(
                '保存',
                style: textTheme.labelLarge?.copyWith(color: colorScheme.primary, fontWeight: FontWeight.w600),
              ),
            ),
          ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
          children: [
            _buildFormSection(
              context: context,
              title: "配置名称",
              icon: Icons.text_fields,
              child: _buildTextField(controller: controller.nameController, hintText: "输入配置名称", context: context),
            ),
            _buildFormSection(
              context: context,
              title: "AI服务提供商",
              icon: Icons.cloud,
              child: _buildApiProviderSelection(context),
            ),
            _buildFormSection(
              context: context,
              title: "模型名称",
              icon: Icons.smart_toy,
              child: Obx(
                () =>
                    controller.isCustomApiAddress
                        ? _buildTextField(
                          controller: controller.modelNameController,
                          hintText: "输入模型名称",
                          context: context,
                        )
                        : _buildModelNameDropdown(context),
              ),
            ),
            _buildFormSection(
              context: context,
              title: "API令牌",
              icon: Icons.vpn_key,
              child: _buildTextField(
                controller: controller.apiTokenController,
                hintText: "输入API密钥",
                isPassword: true,
                context: context,
              ),
            ),
            Obx(() {
              if (controller.isCustomApiAddress) {
                return _buildFormSection(
                  context: context,
                  title: "自定义API地址",
                  icon: Icons.link,
                  child: _buildTextField(
                    controller: controller.apiAddressController,
                    hintText: "例如: https://api.yourservice.com",
                    context: context,
                  ),
                );
              }
              return const SizedBox.shrink();
            }),
          ],
        ),
      ),
    );
  }

  Widget _buildFormSection({
    required BuildContext context,
    required String title,
    required IconData icon,
    required Widget child,
  }) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Padding(
      padding: const EdgeInsets.only(bottom: 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 20, color: colorScheme.primary),
              const SizedBox(width: 8),
              Text(title, style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w500)),
            ],
          ),
          const SizedBox(height: 12),
          child,
        ],
      ),
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String hintText,
    required BuildContext context,
    bool isPassword = false,
  }) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return TextField(
      controller: controller,
      obscureText: isPassword,
      style: textTheme.bodyLarge,
      decoration: InputDecoration(
        hintText: hintText,
        hintStyle: textTheme.bodyLarge?.copyWith(color: colorScheme.onSurface.withOpacity(0.5)),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: colorScheme.outline.withOpacity(0.3)),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: colorScheme.outline.withOpacity(0.3)),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: colorScheme.primary),
        ),
      ),
    );
  }

  Widget _buildApiProviderSelection(BuildContext context) {
    return Obx(
      () => _buildSelectionField(
        context: context,
        value: controller.apiPresets[controller.selectedApiPresetIndex]['name'],
        onTap: () => _showApiProviderBottomSheet(context),
      ),
    );
  }

  Widget _buildModelNameDropdown(BuildContext context) {
    return _buildSelectionField(
      context: context,
      value: controller.modelNameController.text.isEmpty ? '请选择模型' : controller.modelNameController.text,
      onTap: () => _showModelSelectionBottomSheet(context),
    );
  }

  Widget _buildSelectionField({required BuildContext context, required String value, required VoidCallback onTap}) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: colorScheme.outline.withOpacity(0.3)),
        ),
        child: Row(
          children: [
            Expanded(child: Text(value, style: textTheme.bodyLarge)),
            Icon(Icons.arrow_drop_down, color: colorScheme.onSurface.withOpacity(0.5)),
          ],
        ),
      ),
    );
  }

  void _showApiProviderBottomSheet(BuildContext context) {
    _showSelectionBottomSheet(
      context: context,
      title: '选择AI服务提供商',
      items: controller.apiPresets.map((e) => e['name'] as String).toList(),
      selectedValue: controller.apiPresets[controller.selectedApiPresetIndex]['name'],
      onSelected: (index) => controller.updateApiAddress(index),
    );
  }

  void _showModelSelectionBottomSheet(BuildContext context) {
    final models = controller.availableModels;
    if (models.isEmpty) {
      Get.snackbar(
        '提示',
        '当前API提供商没有可用模型',
        snackPosition: SnackPosition.top,
        backgroundColor: Colors.orange.withAlpha(200),
      );
      return;
    }

    _showSelectionBottomSheet(
      context: context,
      title: '选择模型',
      items: models,
      selectedValue: controller.modelNameController.text,
      onSelected: (index) => controller.updateModelName(models[index]),
    );
  }

  void _showSelectionBottomSheet({
    required BuildContext context,
    required String title,
    required List<String> items,
    required String selectedValue,
    required Function(int) onSelected,
  }) {
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
                  child: Text(title, style: textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600)),
                ),
                const Divider(),
                ListView.builder(
                  shrinkWrap: true,
                  physics: const ClampingScrollPhysics(),
                  itemCount: items.length,
                  itemBuilder: (context, index) {
                    final item = items[index];
                    return ListTile(
                      title: Text(item, style: textTheme.bodyLarge),
                      leading: Radio<String>(
                        value: item,
                        groupValue: selectedValue,
                        onChanged: (_) {
                          onSelected(index);
                          Navigator.pop(context);
                        },
                        activeColor: colorScheme.primary,
                      ),
                      onTap: () {
                        onSelected(index);
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
}
