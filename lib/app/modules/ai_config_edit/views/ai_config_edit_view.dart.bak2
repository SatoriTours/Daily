import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart' as base_dim;

import '../controllers/ai_config_edit_controller.dart';

/// AI配置编辑页面
class AIConfigEditView extends GetView<AIConfigEditController> {
  const AIConfigEditView({super.key});

  @override
  Widget build(BuildContext context) {
    logger.i('构建AI配置编辑页面');
    return Scaffold(
      backgroundColor: AppTheme.getColorScheme(context).surface,
      appBar: _buildAppBar(context),
      body: _buildBody(context),
      bottomNavigationBar: _buildBottomActionBar(context),
    );
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    logger.d('构建应用栏');
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return AppBar(
      title: Text(
        controller.isEditMode ? '编辑配置' : '新建配置',
        style: textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600),
      ),
      centerTitle: true,
      backgroundColor: colorScheme.surface,
    );
  }

  /// 构建底部操作栏
  Widget _buildBottomActionBar(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      decoration: BoxDecoration(
        color: colorScheme.surface,
        border: Border(top: BorderSide(color: colorScheme.outline.withValues(alpha: 0.2), width: 1)),
      ),
      child: SafeArea(
        child: Row(
          children: [
            Expanded(
              child: TextButton(
                onPressed: () {
                  logger.i('点击恢复按钮');
                  controller.resetConfig();
                },
                child: const Text('恢复'),
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Obx(
                () => ElevatedButton(
                  onPressed: controller.isFormValid
                      ? () {
                          logger.i('点击保存按钮');
                          controller.saveConfig();
                        }
                      : null,
                  child: const Text('保存'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// 构建主体内容
  Widget _buildBody(BuildContext context) {
    logger.d('构建主体内容');
    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
        children: [
          _buildNameField(context),
          _buildApiProviderField(context),
          _buildModelNameField(context),
          _buildApiTokenField(context),
          _buildCustomApiAddressField(context),
        ],
      ),
    );
  }

  /// 构建配置名称字段
  Widget _buildNameField(BuildContext context) {
    logger.d('构建配置名称字段');
    return _buildFormSection(
      context: context,
      title: "配置名称",
      icon: Icons.text_fields,
      child: FormTextField(controller: controller.nameController, hintText: "输入配置名称"),
    );
  }

  /// 构建API提供商字段
  Widget _buildApiProviderField(BuildContext context) {
    logger.d('构建API提供商字段');
    return _buildFormSection(
      context: context,
      title: "AI服务提供商",
      icon: Icons.cloud,
      child: Obx(
        () => SelectionField(
          value: controller.apiPresets[controller.selectedApiPresetIndex].name,
          onTap: () => _showSelectionBottomSheet(
            context: context,
            title: '选择AI服务提供商',
            items: controller.apiPresets.map((e) => e.name).toList(),
            selectedValue: controller.apiPresets[controller.selectedApiPresetIndex].name,
            onSelected: (index) {
              logger.i('选择API提供商: ${controller.apiPresets[index].name}');
              controller.updateApiAddress(index);
            },
          ),
        ),
      ),
    );
  }

  /// 构建模型名称字段
  Widget _buildModelNameField(BuildContext context) {
    logger.d('构建模型名称字段');
    return _buildFormSection(
      context: context,
      title: "模型名称",
      icon: Icons.smart_toy,
      child: Obx(
        () => controller.isCustomApiAddress
            ? FormTextField(controller: controller.modelNameController, hintText: "输入模型名称")
            : SelectionField(value: controller.modelName, onTap: () => _showModelSelectionBottomSheet(context)),
      ),
    );
  }

  /// 构建API令牌字段
  Widget _buildApiTokenField(BuildContext context) {
    logger.d('构建API令牌字段');
    return _buildFormSection(
      context: context,
      title: "API令牌",
      icon: Icons.vpn_key,
      child: FormTextField(controller: controller.apiTokenController, hintText: "输入API密钥", isPassword: true),
    );
  }

  /// 构建自定义API地址字段
  Widget _buildCustomApiAddressField(BuildContext context) {
    logger.d('构建自定义API地址字段');
    return Obx(() {
      if (controller.isCustomApiAddress) {
        return _buildFormSection(
          context: context,
          title: "自定义API地址",
          icon: Icons.link,
          child: FormTextField(
            controller: controller.apiAddressController,
            hintText: "例如: https://api.yourservice.com",
          ),
        );
      }
      return const SizedBox.shrink();
    });
  }

  /// 构建表单部分
  Widget _buildFormSection({
    required BuildContext context,
    required String title,
    required IconData icon,
    required Widget child,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          FormSectionHeader(title: title, icon: icon),
          const SizedBox(height: 12),
          child,
        ],
      ),
    );
  }

  /// 显示模型选择底部弹出窗口
  void _showModelSelectionBottomSheet(BuildContext context) {
    logger.d('显示模型选择底部弹出窗口');
    final models = controller.availableModels;
    if (models.isEmpty) {
      logger.w('当前API提供商没有可用模型');
      UIUtils.showError('当前API提供商没有可用模型');
      return;
    }

    _showSelectionBottomSheet(
      context: context,
      title: '选择模型',
      items: models,
      selectedValue: controller.modelNameController.text,
      onSelected: (index) {
        logger.i('选择模型: ${models[index]}');
        controller.updateModelName(models[index]);
      },
    );
  }

  /// 显示选择底部弹出窗口
  void _showSelectionBottomSheet({
    required BuildContext context,
    required String title,
    required List<String> items,
    required String selectedValue,
    required Function(int) onSelected,
  }) {
    logger.d('显示选择底部弹出窗口: $title');
    final colorScheme = AppTheme.getColorScheme(context);

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: colorScheme.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (context) =>
          SelectionBottomSheet(title: title, items: items, selectedValue: selectedValue, onSelected: onSelected),
    );
  }
}

/// 表单部分标题
class FormSectionHeader extends StatelessWidget {
  final String title;
  final IconData icon;

  const FormSectionHeader({super.key, required this.title, required this.icon});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Row(
      children: [
        Icon(icon, size: 20, color: colorScheme.primary),
        const SizedBox(width: 10),
        Text(title, style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w500)),
      ],
    );
  }
}

/// 表单文本输入字段
class FormTextField extends StatelessWidget {
  final TextEditingController controller;
  final String hintText;
  final bool isPassword;
  final Function(String)? onChanged;

  const FormTextField({
    super.key,
    required this.controller,
    required this.hintText,
    this.isPassword = false,
    this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return TextField(
      controller: controller,
      obscureText: isPassword,
      style: textTheme.bodyLarge,
      decoration: InputDecoration(
        hintText: hintText,
        hintStyle: textTheme.bodyLarge?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.5)),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide.none),
        filled: true,
        fillColor: colorScheme.surfaceContainerHighest.withValues(alpha: 0.3),
      ),
      onChanged: onChanged,
    );
  }
}

/// 选择字段
class SelectionField extends StatelessWidget {
  final String value;
  final VoidCallback onTap;

  const SelectionField({super.key, required this.value, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: colorScheme.surfaceContainerHighest.withValues(alpha: 0.3),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(value, style: textTheme.bodyLarge, overflow: TextOverflow.ellipsis),
            ),
            Icon(Icons.arrow_drop_down, color: colorScheme.onSurface.withValues(alpha: 0.5)),
          ],
        ),
      ),
    );
  }
}

/// 选择底部弹出窗口
class SelectionBottomSheet extends StatelessWidget {
  final String title;
  final List<String> items;
  final String selectedValue;
  final Function(int) onSelected;

  const SelectionBottomSheet({
    super.key,
    required this.title,
    required this.items,
    required this.selectedValue,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Icon(Icons.list_alt, size: 22, color: colorScheme.primary),
                const SizedBox(width: 12),
                Text(title, style: textTheme.titleLarge),
              ],
            ),
            const SizedBox(height: 16),
            const Divider(height: 1),
            ConstrainedBox(
              constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.4),
              child: ListView.builder(
                shrinkWrap: true,
                physics: const ClampingScrollPhysics(),
                padding: EdgeInsets.zero,
                itemCount: items.length,
                itemBuilder: (context, index) =>
                    _buildSelectionItem(context, items[index], items[index] == selectedValue, () {
                      onSelected(index);
                      Navigator.pop(context);
                    }),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSelectionItem(BuildContext context, String item, bool isSelected, VoidCallback onTap) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return InkWell(
      onTap: onTap,
      child: Container(
        color: isSelected ? colorScheme.primaryContainer.withValues(alpha: 0.2) : null,
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 16),
        child: Text(
          item,
          style: textTheme.bodyLarge?.copyWith(
            color: isSelected ? colorScheme.primary : colorScheme.onSurface,
            fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
          ),
        ),
      ),
    );
  }
}
