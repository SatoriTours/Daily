import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../controllers/ai_config_edit_controller.dart';
import 'widgets/form_widgets.dart';
import 'widgets/selection_bottom_sheet.dart';

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

  // ========================================================================
  // AppBar
  // ========================================================================

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return PreferredSize(
      preferredSize: const Size.fromHeight(kToolbarHeight),
      child: GetBuilder<AIConfigEditController>(
        builder: (controller) => SAppBar(
          title: Text(controller.pageTitle, style: const TextStyle(color: Colors.white)),
          centerTitle: true,
          backgroundColorLight: AppColors.primary,
          backgroundColorDark: AppColors.backgroundDark,
          foregroundColor: Colors.white,
        ),
      ),
    );
  }

  // ========================================================================
  // 底部操作栏
  // ========================================================================

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
            Expanded(child: _buildResetButton()),
            Dimensions.horizontalSpacerM,
            Expanded(child: _buildSaveButton()),
          ],
        ),
      ),
    );
  }

  /// 构建重置按钮
  Widget _buildResetButton() {
    return TextButton(
      onPressed: () {
        logger.i('点击恢复按钮');
        controller.resetConfig();
      },
      child: const Text('恢复'),
    );
  }

  /// 构建保存按钮
  Widget _buildSaveButton() {
    return Obx(
      () => ElevatedButton(
        onPressed: controller.isFormValid
            ? () {
                logger.i('点击保存按钮');
                controller.saveConfig();
              }
            : null,
        child: const Text('保存'),
      ),
    );
  }

  // ========================================================================
  // 主体内容
  // ========================================================================

  /// 构建主体内容
  Widget _buildBody(BuildContext context) {
    return SafeArea(
      child: ListView(
        padding: Dimensions.paddingPage,
        children: [
          if (!controller.isSystemConfig) _buildNameField(context),
          if (controller.isSpecialConfig) _buildInheritOptionField(context),
          _buildApiConfigFields(context),
        ],
      ),
    );
  }

  /// 构建 API 配置字段区域
  Widget _buildApiConfigFields(BuildContext context) {
    // 通用配置直接显示
    if (!controller.isSpecialConfig) {
      return Column(
        children: [
          _buildApiProviderField(context),
          _buildModelNameField(context),
          _buildApiTokenField(context),
          _buildCustomApiAddressField(context),
        ],
      );
    }

    // 特殊配置根据继承状态显示
    return Obx(() {
      if (!controller.inheritFromGeneral) {
        return Column(
          children: [
            _buildApiProviderField(context),
            _buildModelNameField(context),
            _buildApiTokenField(context),
            _buildCustomApiAddressField(context),
          ],
        );
      }
      return const SizedBox.shrink();
    });
  }

  // ========================================================================
  // 表单字段
  // ========================================================================

  /// 构建配置名称字段
  Widget _buildNameField(BuildContext context) {
    return _buildFormSection(
      context: context,
      title: "配置名称",
      icon: Icons.text_fields,
      child: FormTextField(controller: controller.nameController, hintText: "输入配置名称"),
    );
  }

  /// 构建继承选项字段
  Widget _buildInheritOptionField(BuildContext context) {
    return _buildFormSection(
      context: context,
      title: "使用通用配置",
      icon: Icons.settings_suggest,
      child: Obx(() => _buildInheritOptionContent(context)),
    );
  }

  /// 构建继承选项内容
  Widget _buildInheritOptionContent(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [_buildInheritOptionRow(context), Dimensions.verticalSpacerS, _buildInheritOptionHint(context)],
    );
  }

  /// 构建继承选项主行
  Widget _buildInheritOptionRow(BuildContext context) {
    return Row(
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
        Transform.scale(
          scale: 0.75,
          child: Switch(
            value: controller.inheritFromGeneral,
            onChanged: (value) {
              logger.i('切换继承模式: $value');
              controller.setInheritFromGeneral(value);
            },
            activeThumbColor: AppColors.getPrimary(context),
            materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
          ),
        ),
      ],
    );
  }

  /// 构建继承选项提示信息
  Widget _buildInheritOptionHint(BuildContext context) {
    final isInheriting = controller.inheritFromGeneral;
    return Container(
      padding: Dimensions.paddingS,
      decoration: BoxDecoration(
        color: isInheriting
            ? AppColors.getPrimary(context).withValues(alpha: Opacities.extraLow)
            : AppColors.getSurfaceContainerHighest(context).withValues(alpha: Opacities.extraLow),
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        border: Border.all(
          color: isInheriting
              ? AppColors.getPrimary(context).withValues(alpha: Opacities.low)
              : AppColors.getOutline(context).withValues(alpha: Opacities.medium),
          width: 1,
        ),
      ),
      child: Row(
        children: [
          Icon(
            isInheriting ? Icons.sync : Icons.tune,
            size: Dimensions.iconSizeS,
            color: isInheriting
                ? AppColors.getPrimary(context)
                : AppColors.getOnSurface(context).withValues(alpha: Opacities.medium),
          ),
          Dimensions.horizontalSpacerS,
          Expanded(
            child: Text(
              isInheriting ? '将使用通用配置的AI设置' : '可以为此功能设置独立的AI配置',
              style: AppTypography.bodySmall.copyWith(color: AppColors.getOnSurface(context)),
            ),
          ),
        ],
      ),
    );
  }

  /// 构建API提供商字段
  Widget _buildApiProviderField(BuildContext context) {
    return _buildFormSection(
      context: context,
      title: "AI服务提供商",
      icon: Icons.cloud,
      child: Obx(
        () => SelectionField(
          value: controller.apiPresets[controller.selectedApiPresetIndex].name,
          onTap: () => showSelectionBottomSheet(
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
    return _buildFormSection(
      context: context,
      title: "API令牌",
      icon: Icons.vpn_key,
      child: FormTextField(controller: controller.apiTokenController, hintText: "输入API密钥", isPassword: true),
    );
  }

  /// 构建自定义API地址字段
  Widget _buildCustomApiAddressField(BuildContext context) {
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

  // ========================================================================
  // 辅助方法
  // ========================================================================

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
    final models = controller.availableModels;
    if (models.isEmpty) {
      logger.w('当前API提供商没有可用模型');
      UIUtils.showError('当前API提供商没有可用模型');
      return;
    }

    showSelectionBottomSheet(
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
}
