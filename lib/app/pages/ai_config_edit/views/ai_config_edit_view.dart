import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import 'package:daily_satori/app/pages/ai_config_edit/providers/ai_config_edit_controller_provider.dart';
import 'package:daily_satori/app/pages/ai_config_edit/views/widgets/form_widgets.dart';
import 'package:daily_satori/app/pages/ai_config_edit/views/widgets/selection_bottom_sheet.dart';
import 'package:daily_satori/app/objectbox/ai_config.dart';

class AIConfigEditView extends ConsumerStatefulWidget {
  const AIConfigEditView({super.key});

  @override
  ConsumerState<AIConfigEditView> createState() => _AIConfigEditViewState();
}

class _AIConfigEditViewState extends ConsumerState<AIConfigEditView> {
  late final TextEditingController _nameController;
  late final TextEditingController _apiAddressController;
  late final TextEditingController _apiTokenController;
  late final TextEditingController _modelNameController;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController();
    _apiAddressController = TextEditingController();
    _apiTokenController = TextEditingController();
    _modelNameController = TextEditingController();

    // Initialize state from arguments
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final args = ModalRoute.of(context)?.settings.arguments as Map<String, dynamic>?;
      if (args != null) {
        final config = args['aiConfig'] as AIConfig?;
        final functionType = args['functionType'] as int?;

        if (config != null) {
          ref.read(aIConfigEditControllerProvider.notifier).loadConfig(config);
          _nameController.text = config.name;
          _apiAddressController.text = config.apiAddress;
          _apiTokenController.text = config.apiToken;
          _modelNameController.text = config.modelName;
        } else if (functionType != null) {
          ref.read(aIConfigEditControllerProvider.notifier).updateFunctionType(functionType);
        }
      }
    });
  }

  @override
  void dispose() {
    _nameController.dispose();
    _apiAddressController.dispose();
    _apiTokenController.dispose();
    _modelNameController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(aIConfigEditControllerProvider);
    final controller = ref.read(aIConfigEditControllerProvider.notifier);

    return Scaffold(
      backgroundColor: AppColors.getSurface(context),
      appBar: SAppBar(
        title: Text(controller.pageTitle, style: const TextStyle(color: Colors.white)),
        centerTitle: true,
        backgroundColorLight: AppColors.primary,
        backgroundColorDark: AppColors.backgroundDark,
        foregroundColor: Colors.white,
      ),
      body: SafeArea(
        child: ListView(
          padding: Dimensions.paddingPage,
          children: [
            if (!controller.isSystemConfig)
              _buildFormSection(
                context: context,
                title: "配置名称",
                icon: Icons.text_fields,
                child: FormTextField(controller: _nameController, hintText: "输入配置名称", onChanged: controller.updateName),
              ),

            if (controller.isSpecialConfig) _buildInheritOptionField(context, state, controller),

            _buildApiConfigFields(context, state, controller),
          ],
        ),
      ),
      bottomNavigationBar: _buildBottomActionBar(context, state, controller),
    );
  }

  Widget _buildInheritOptionField(
    BuildContext context,
    AIConfigEditControllerState state,
    AIConfigEditController controller,
  ) {
    return _buildFormSection(
      context: context,
      title: "使用通用配置",
      icon: Icons.settings_suggest,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            onTap: () => controller.setInheritFromGeneral(!state.inheritFromGeneral),
            borderRadius: BorderRadius.circular(Dimensions.radiusS),
            child: Container(
              padding: Dimensions.paddingS,
              decoration: BoxDecoration(
                color: state.inheritFromGeneral
                    ? AppColors.getPrimary(context).withValues(alpha: Opacities.extraLow)
                    : AppColors.getSurfaceContainerHighest(context).withValues(alpha: Opacities.extraLow),
                borderRadius: BorderRadius.circular(Dimensions.radiusS),
                border: Border.all(
                  color: state.inheritFromGeneral
                      ? AppColors.getPrimary(context).withValues(alpha: Opacities.low)
                      : AppColors.getOutline(context).withValues(alpha: Opacities.medium),
                  width: 1,
                ),
              ),
              child: Row(
                children: [
                  Icon(
                    state.inheritFromGeneral ? Icons.sync : Icons.tune,
                    size: Dimensions.iconSizeS,
                    color: state.inheritFromGeneral
                        ? AppColors.getPrimary(context)
                        : AppColors.getOnSurface(context).withValues(alpha: Opacities.medium),
                  ),
                  Dimensions.horizontalSpacerS,
                  Expanded(
                    child: Text(
                      state.inheritFromGeneral ? '继承通用配置' : '独立配置',
                      style: AppTypography.bodyMedium.copyWith(
                        color: state.inheritFromGeneral
                            ? AppColors.getPrimary(context)
                            : AppColors.getOnSurface(context),
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                  if (state.inheritFromGeneral)
                    Icon(Icons.check, size: Dimensions.iconSizeS, color: AppColors.getPrimary(context)),
                ],
              ),
            ),
          ),
          Dimensions.verticalSpacerS,
          Padding(
            padding: const EdgeInsets.only(left: Dimensions.spacingS),
            child: Text(
              state.inheritFromGeneral ? '将使用通用配置的AI设置' : '可以为此功能设置独立的AI配置',
              style: AppTypography.bodySmall.copyWith(color: AppColors.getOnSurface(context)),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildApiConfigFields(
    BuildContext context,
    AIConfigEditControllerState state,
    AIConfigEditController controller,
  ) {
    if (controller.isSpecialConfig && state.inheritFromGeneral) {
      return const SizedBox.shrink();
    }

    return Column(
      children: [
        _buildFormSection(
          context: context,
          title: "AI服务提供商",
          icon: Icons.cloud,
          child: SelectionField(
            value: state.apiAddress.isEmpty ? '选择提供商' : state.apiAddress,
            onTap: () => showSelectionBottomSheet(
              context: context,
              title: '选择AI服务提供商',
              items: controller.apiPresets,
              selectedValue: state.apiAddress,
              onSelected: (index) {
                final address = controller.apiPresets[index];
                controller.updateApiAddress(address);
                _apiAddressController.text = address;
              },
            ),
          ),
        ),
        _buildFormSection(
          context: context,
          title: "模型名称",
          icon: Icons.smart_toy,
          child: SelectionField(
            value: state.modelName.isEmpty ? '选择模型' : state.modelName,
            onTap: () => showSelectionBottomSheet(
              context: context,
              title: '选择模型',
              items: controller.availableModels,
              selectedValue: state.modelName,
              onSelected: (index) {
                final model = controller.availableModels[index];
                controller.updateModelName(model);
                _modelNameController.text = model;
              },
            ),
          ),
        ),
        _buildFormSection(
          context: context,
          title: "API令牌",
          icon: Icons.vpn_key,
          child: FormTextField(
            controller: _apiTokenController,
            hintText: "输入API密钥",
            isPassword: true,
            onChanged: controller.updateApiToken,
          ),
        ),
        if (controller.isCustomApiAddress)
          _buildFormSection(
            context: context,
            title: "自定义API地址",
            icon: Icons.link,
            child: FormTextField(
              controller: _apiAddressController,
              hintText: "例如: https://api.yourservice.com",
              onChanged: controller.updateApiAddress,
            ),
          ),
      ],
    );
  }

  Widget _buildFormSection({
    required BuildContext context,
    required String title,
    required IconData icon,
    required Widget child,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: Dimensions.spacingL),
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

  Widget _buildBottomActionBar(
    BuildContext context,
    AIConfigEditControllerState state,
    AIConfigEditController controller,
  ) {
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
                  controller.resetConfig();
                  if (state.config != null) {
                    _nameController.text = state.config!.name;
                    _apiAddressController.text = state.config!.apiAddress;
                    _apiTokenController.text = state.config!.apiToken;
                    _modelNameController.text = state.config!.modelName;
                  }
                },
                child: const Text('恢复'),
              ),
            ),
            Dimensions.horizontalSpacerM,
            Expanded(
              child: ElevatedButton(
                onPressed: controller.isFormValid && !state.isSaving ? () => controller.saveConfig() : null,
                child: state.isSaving
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : const Text('保存'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
