import 'package:daily_satori/app/models/models.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/styles/dimensions.dart';
import 'package:daily_satori/app/styles/font_style.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../controllers/ai_config_controller.dart';

/// AI配置页面
class AIConfigView extends GetView<AIConfigController> {
  const AIConfigView({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('AI 配置管理'), centerTitle: true, elevation: 0),
      body: Column(
        children: [
          _buildFunctionTypeSelector(context),
          Expanded(
            child: Obx(() {
              if (controller.isLoading.value) {
                return const Center(child: CircularProgressIndicator());
              } else {
                final configsOfType = controller.getConfigsByType(controller.selectedFunctionType.value);
                if (configsOfType.isEmpty) {
                  return const Center(child: Text('没有配置，请添加新配置'));
                } else {
                  return _buildConfigList(context, configsOfType);
                }
              }
            }),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(onPressed: controller.createNewConfig, child: const Icon(Icons.add)),
    );
  }

  /// 构建功能类型选择器
  Widget _buildFunctionTypeSelector(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Container(
      padding: const EdgeInsets.symmetric(vertical: 8),
      color: colorScheme.surface,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Row(
          children: [
            _buildFunctionTypeChip(context, 0, '通用配置'),
            const SizedBox(width: 8),
            _buildFunctionTypeChip(context, 1, '文章总结'),
            const SizedBox(width: 8),
            _buildFunctionTypeChip(context, 2, '书本解读'),
            const SizedBox(width: 8),
            _buildFunctionTypeChip(context, 3, '日记总结'),
          ],
        ),
      ),
    );
  }

  /// 构建功能类型筹码
  Widget _buildFunctionTypeChip(BuildContext context, int type, String label) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Obx(() {
      final isSelected = controller.selectedFunctionType.value == type;
      return FilterChip(
        label: Text(label),
        selected: isSelected,
        onSelected: (value) {
          if (value) controller.selectedFunctionType.value = type;
        },
        backgroundColor: colorScheme.surface,
        selectedColor: colorScheme.primaryContainer,
        checkmarkColor: colorScheme.onPrimaryContainer,
        labelStyle: TextStyle(color: isSelected ? colorScheme.onPrimaryContainer : colorScheme.onSurface),
      );
    });
  }

  /// 构建配置列表
  Widget _buildConfigList(BuildContext context, List<AIConfigModel> configs) {
    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: configs.length,
      itemBuilder: (context, index) => _buildConfigCard(context, configs[index]),
    );
  }

  /// 构建配置卡片
  Widget _buildConfigCard(BuildContext context, AIConfigModel config) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Card(
      margin: const EdgeInsets.only(bottom: 16),
      elevation: 1,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 标题栏
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: config.isDefault ? colorScheme.primaryContainer : null,
              borderRadius: const BorderRadius.vertical(top: Radius.circular(8)),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    config.name,
                    style: MyFontStyle.listTitleStyleThemed(
                      context,
                    ).copyWith(color: config.isDefault ? colorScheme.onPrimaryContainer : null),
                  ),
                ),
                if (config.isDefault)
                  Chip(
                    label: const Text('默认'),
                    backgroundColor: colorScheme.primary,
                    labelStyle: TextStyle(color: colorScheme.onPrimary, fontSize: 12),
                    padding: EdgeInsets.zero,
                  ),
              ],
            ),
          ),
          // 内容
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (config.inheritFromGeneral && config.functionType != 0)
                  Text('继承自通用配置', style: MyFontStyle.cardSubtitleStyleThemed(context)),
                Dimensions.verticalSpacerS,
                _buildConfigInfoRow(context, 'API 地址:', config.apiAddress),
                _buildConfigInfoRow(context, 'API 令牌:', config.apiToken.isNotEmpty ? '**********' : ''),
                _buildConfigInfoRow(context, '模型名称:', config.modelName),
                Dimensions.verticalSpacerS,
                _buildActions(context, config),
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// 构建配置信息行
  Widget _buildConfigInfoRow(BuildContext context, String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 80, child: Text(label, style: MyFontStyle.cardSubtitleStyleThemed(context))),
          Expanded(
            child: Text(
              value.isEmpty ? '未设置' : value,
              style:
                  value.isEmpty
                      ? MyFontStyle.cardSubtitleStyleThemed(context).copyWith(color: Colors.grey)
                      : MyFontStyle.cardSubtitleStyleThemed(context),
            ),
          ),
        ],
      ),
    );
  }

  /// 构建操作按钮
  Widget _buildActions(BuildContext context, AIConfigModel config) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        if (!config.isDefault)
          TextButton.icon(
            onPressed: () => controller.setAsDefault(config),
            icon: const Icon(Icons.check_circle_outline, size: 16),
            label: const Text('设为默认'),
          ),
        TextButton.icon(
          onPressed: () => controller.cloneConfig(config),
          icon: const Icon(Icons.copy, size: 16),
          label: const Text('克隆'),
        ),
        TextButton.icon(
          onPressed: () => controller.editConfig(config),
          icon: const Icon(Icons.edit, size: 16),
          label: const Text('编辑'),
        ),
        if (!config.isDefault)
          TextButton.icon(
            onPressed: () => _confirmDelete(context, config),
            icon: const Icon(Icons.delete_outline, size: 16),
            label: const Text('删除'),
            style: TextButton.styleFrom(foregroundColor: colorScheme.error),
          ),
      ],
    );
  }

  /// 确认删除对话框
  void _confirmDelete(BuildContext context, AIConfigModel config) {
    final colorScheme = AppTheme.getColorScheme(context);

    showDialog(
      context: context,
      builder:
          (context) => AlertDialog(
            title: const Text('确认删除'),
            content: Text('确定要删除配置"${config.name}"吗？此操作不可撤销。'),
            actions: [
              TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('取消')),
              TextButton(
                onPressed: () {
                  Navigator.of(context).pop();
                  controller.deleteConfig(config);
                },
                child: const Text('删除'),
                style: TextButton.styleFrom(foregroundColor: colorScheme.error),
              ),
            ],
          ),
    );
  }
}
