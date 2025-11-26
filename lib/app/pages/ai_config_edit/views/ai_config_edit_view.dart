import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../controllers/ai_config_edit_controller.dart';

/// AI配置编辑页面
class AIConfigEditView extends GetView<AIConfigEditController> {
  const AIConfigEditView({super.key});

  @override
  Widget build(BuildContext context) {
    logger.i('构建AI配置编辑页面');
    return Scaffold(
      backgroundColor: AppColors.getSurface(context),
      appBar: _buildAppBar(context),
      body: _buildBody(context),
      bottomNavigationBar: _buildBottomActionBar(context),
    );
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    logger.d('构建应用栏');
    return PreferredSize(
      preferredSize: const Size.fromHeight(kToolbarHeight),
      child: GetBuilder<AIConfigEditController>(
        builder: (controller) => AppBar(
          title: Text(controller.pageTitle, style: AppTypography.titleLarge.copyWith(fontWeight: FontWeight.w600)),
          centerTitle: true,
          backgroundColor: AppColors.getSurface(context),
        ),
      ),
    );
  }

  /// 构建底部操作栏
  Widget _buildBottomActionBar(BuildContext context) {
    return Container(
      padding: Dimensions.paddingPage,
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        border: Border(
          top: BorderSide(color: AppColors.getOutline(context).withValues(alpha: Opacities.extraLow), width: 1),
        ),
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
            Dimensions.horizontalSpacerM,
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
        padding: Dimensions.paddingPage,
        children: [
          // 只有非系统配置才显示配置名称字段
          if (!controller.isSystemConfig) _buildNameField(context),
          // 对于特殊配置（文章总结、书本解读、日记总结），显示继承选项
          if (controller.isSpecialConfig) _buildInheritOptionField(context),
          // API配置字段 - 根据继承状态显示或隐藏
          // 显示逻辑：
          // 1. 通用配置（非特殊配置）：始终显示API配置字段
          // 2. 特殊配置 + 不继承通用配置：显示API配置字段
          // 3. 特殊配置 + 继承通用配置：隐藏API配置字段
          if (!controller.isSpecialConfig)
          // 通用配置直接显示
          ...[
            _buildApiProviderField(context),
            _buildModelNameField(context),
            _buildApiTokenField(context),
            _buildCustomApiAddressField(context),
          ] else
            // 特殊配置根据继承状态显示
            Obx(() {
              if (!controller.inheritFromGeneral) {
                return Column(
                  children: [
                    _buildApiProviderField(context),
                    _buildModelNameField(context),
                    _buildApiTokenField(context),
                    _buildCustomApiAddressField(context),
                  ],
                );
              } else {
                return const SizedBox.shrink();
              }
            }),
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

  /// 构建继承选项字段
  Widget _buildInheritOptionField(BuildContext context) {
    logger.d('构建继承选项字段');
    return _buildFormSection(
      context: context,
      title: "使用通用配置",
      icon: Icons.settings_suggest,
      child: Obx(
        () => Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 主行：状态说明在左，开关在右
            Row(
              children: [
                Expanded(
                  child: Text(
                    controller.inheritFromGeneral ? '继承通用配置' : '独立配置',
                    style: AppTypography.bodyMedium.copyWith(
                      color: AppColors.getOnSurface(context),
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
                Dimensions.horizontalSpacerM,
                // 小尺寸的开关
                Transform.scale(
                  scale: 0.75, // 缩小到原来的75%
                  child: Switch(
                    value: controller.inheritFromGeneral,
                    onChanged: (value) {
                      logger.i('切换继承模式: $value');
                      // 更新控制器中的继承状态
                      controller.setInheritFromGeneral(value);
                    },
                    activeThumbColor: AppColors.getPrimary(context),
                    materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  ),
                ),
              ],
            ),
            // 简化的说明信息
            Dimensions.verticalSpacerS,
            Container(
              padding: Dimensions.paddingS,
              decoration: BoxDecoration(
                color: controller.inheritFromGeneral
                    ? AppColors.getPrimary(context).withValues(alpha: Opacities.extraLow)
                    : AppColors.getSurfaceContainerHighest(context).withValues(alpha: Opacities.extraLow),
                borderRadius: BorderRadius.circular(Dimensions.radiusS),
                border: Border.all(
                  color: controller.inheritFromGeneral
                      ? AppColors.getPrimary(context).withValues(alpha: Opacities.low)
                      : AppColors.getOutline(context).withValues(alpha: Opacities.medium),
                  width: 1,
                ),
              ),
              child: Row(
                children: [
                  Icon(
                    controller.inheritFromGeneral ? Icons.sync : Icons.tune,
                    size: Dimensions.iconSizeS,
                    color: controller.inheritFromGeneral
                        ? AppColors.getPrimary(context)
                        : AppColors.getOnSurface(context).withValues(alpha: Opacities.medium),
                  ),
                  Dimensions.horizontalSpacerS,
                  Expanded(
                    child: Text(
                      controller.inheritFromGeneral ? '将使用通用配置的AI设置' : '可以为此功能设置独立的AI配置',
                      style: AppTypography.bodySmall.copyWith(color: AppColors.getOnSurface(context)),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
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
      padding: EdgeInsets.only(bottom: Dimensions.spacingL),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          FormSectionHeader(title: title, icon: icon),
          Dimensions.verticalSpacerM,
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
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.getSurface(context),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(Dimensions.radiusL))),
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
    return Row(
      children: [
        Icon(icon, size: Dimensions.iconSizeS, color: AppColors.getPrimary(context)),
        Dimensions.horizontalSpacerM,
        Text(title, style: AppTypography.titleMedium.copyWith(fontWeight: FontWeight.w500)),
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
    return TextField(
      controller: controller,
      obscureText: isPassword,
      style: AppTypography.bodyMedium,
      decoration: InputDecoration(
        hintText: hintText,
        hintStyle: AppTypography.bodyMedium.copyWith(
          color: AppColors.getOnSurface(context).withValues(alpha: Opacities.mediumLow),
        ),
        contentPadding: Dimensions.paddingInput,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: BorderSide(color: AppColors.getOutline(context), width: 1),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: BorderSide(color: AppColors.getOutline(context), width: 1),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          borderSide: BorderSide(color: AppColors.getPrimary(context), width: 2),
        ),
        filled: true,
        fillColor: AppColors.getSurfaceContainerHighest(context).withValues(alpha: Opacities.extraLow),
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
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      child: Container(
        width: double.infinity,
        padding: Dimensions.paddingInput,
        decoration: BoxDecoration(
          color: AppColors.getSurfaceContainerHighest(context).withValues(alpha: Opacities.extraLow),
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          border: Border.all(color: AppColors.getOutline(context), width: 1),
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(value, style: AppTypography.bodyMedium, overflow: TextOverflow.ellipsis),
            ),
            Icon(Icons.arrow_drop_down, color: AppColors.getOnSurface(context), size: Dimensions.iconSizeM),
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
    return SafeArea(
      child: Padding(
        padding: Dimensions.paddingPage,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Icon(Icons.list_alt, size: Dimensions.iconSizeM, color: AppColors.getPrimary(context)),
                Dimensions.horizontalSpacerM,
                Text(title, style: AppTypography.titleLarge),
              ],
            ),
            Dimensions.verticalSpacerM,
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
    return InkWell(
      onTap: onTap,
      child: Container(
        color: isSelected ? AppColors.getPrimary(context).withValues(alpha: Opacities.extraLow) : null,
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 16),
        child: Text(
          item,
          style: AppTypography.bodyMedium.copyWith(
            color: isSelected ? AppColors.getPrimary(context) : AppColors.getOnSurface(context),
            fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
          ),
        ),
      ),
    );
  }
}
