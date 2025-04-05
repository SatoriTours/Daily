import 'package:daily_satori/app/models/models.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../controllers/ai_config_controller.dart';

/// AI配置页面
/// 用于管理不同功能的AI配置，包括：
/// - 通用配置：所有AI功能的基础配置
/// - 文章总结：生成文章摘要和关键点
/// - 书本解读：解析书籍内容和笔记
/// - 日记总结：分析和生成日记内容
class AIConfigView extends GetView<AIConfigController> {
  const AIConfigView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.getColorScheme(context).surface,
      appBar: _buildAppBar(context),
      body: Column(
        children: [_FunctionTypeSelector(controller: controller), Expanded(child: _ConfigList(controller: controller))],
      ),
      floatingActionButton: _buildFloatingActionButton(context),
    );
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      title: const Text('AI 配置管理'),
      centerTitle: true,
      elevation: 0,
      backgroundColor: AppTheme.getColorScheme(context).surface,
      actions: [
        IconButton(icon: const Icon(Icons.info_outline), tooltip: 'AI配置说明', onPressed: () => _showInfoDialog(context)),
      ],
    );
  }

  /// 构建悬浮按钮
  Widget _buildFloatingActionButton(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    return FloatingActionButton(
      onPressed: controller.createNewConfig,
      backgroundColor: colorScheme.primary,
      foregroundColor: colorScheme.onPrimary,
      child: const Icon(Icons.add),
    );
  }

  /// 显示AI配置信息对话框
  void _showInfoDialog(BuildContext context) {
    showDialog(context: context, builder: (context) => _InfoDialog());
  }
}

/// 功能类型选择器组件
class _FunctionTypeSelector extends StatelessWidget {
  final AIConfigController controller;

  const _FunctionTypeSelector({required this.controller});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 4),
      decoration: BoxDecoration(
        color: AppTheme.getColorScheme(context).surface,
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(13), blurRadius: 4, offset: const Offset(0, 2))],
      ),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        child: Row(
          children: const [
            _TypeChip(type: 0, label: '通用配置', icon: Icons.settings),
            SizedBox(width: 12),
            _TypeChip(type: 1, label: '文章总结', icon: Icons.article),
            SizedBox(width: 12),
            _TypeChip(type: 2, label: '书本解读', icon: Icons.book),
            SizedBox(width: 12),
            _TypeChip(type: 3, label: '日记总结', icon: Icons.edit_note),
          ],
        ),
      ),
    );
  }
}

/// 功能类型选择片段组件
class _TypeChip extends GetView<AIConfigController> {
  final int type;
  final String label;
  final IconData icon;

  const _TypeChip({required this.type, required this.label, required this.icon});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Obx(() {
      final isSelected = controller.selectedFunctionType.value == type;
      return Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: () => controller.selectedFunctionType.value = type,
          borderRadius: BorderRadius.circular(20),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            decoration: BoxDecoration(
              color:
                  isSelected ? colorScheme.primary : (isDark ? colorScheme.surface.withAlpha(77) : colorScheme.surface),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(
                color:
                    isSelected
                        ? colorScheme.primary
                        : (isDark ? colorScheme.onSurface.withAlpha(77) : colorScheme.outline.withAlpha(77)),
                width: isSelected ? 1.5 : 1,
              ),
            ),
            child: Row(
              children: [
                Icon(
                  icon,
                  size: 18,
                  color:
                      isSelected
                          ? Colors.white
                          : (isDark ? colorScheme.onSurface.withAlpha(204) : colorScheme.onSurface.withAlpha(153)),
                ),
                const SizedBox(width: 8),
                Text(
                  label,
                  style: TextStyle(
                    color:
                        isSelected
                            ? Colors.white
                            : (isDark ? colorScheme.onSurface.withAlpha(230) : colorScheme.onSurface.withAlpha(179)),
                    fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    });
  }
}

/// 配置列表组件
class _ConfigList extends StatelessWidget {
  final AIConfigController controller;

  const _ConfigList({required this.controller});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Obx(() {
      if (controller.isLoading.value) {
        return _buildLoadingState(colorScheme);
      }

      final configs = controller.getConfigsByType(controller.selectedFunctionType.value);
      if (configs.isEmpty) {
        return _buildEmptyState(context);
      }

      return _buildConfigListView(configs);
    });
  }

  /// 构建加载状态
  Widget _buildLoadingState(ColorScheme colorScheme) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          CircularProgressIndicator(color: colorScheme.primary),
          const SizedBox(height: 16),
          Text('加载配置中...', style: TextStyle(color: colorScheme.onSurface.withAlpha(153))),
        ],
      ),
    );
  }

  /// 构建空状态
  Widget _buildEmptyState(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.settings_outlined, size: 64, color: colorScheme.onSurface.withAlpha(51)),
          const SizedBox(height: 16),
          Text(
            '没有配置',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: colorScheme.onSurface.withAlpha(179)),
          ),
          const SizedBox(height: 8),
          Text('点击下方按钮添加新配置', style: TextStyle(fontSize: 14, color: colorScheme.onSurface.withAlpha(128))),
          const SizedBox(height: 24),
          ElevatedButton.icon(
            onPressed: controller.createNewConfig,
            icon: const Icon(Icons.add),
            label: const Text('添加配置'),
            style: ElevatedButton.styleFrom(
              backgroundColor: colorScheme.primary,
              foregroundColor: colorScheme.onPrimary,
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
          ),
        ],
      ),
    );
  }

  /// 构建配置列表视图
  Widget _buildConfigListView(List<AIConfigModel> configs) {
    return ListView.builder(
      physics: const BouncingScrollPhysics(),
      padding: const EdgeInsets.all(16),
      itemCount: configs.length,
      itemBuilder: (context, index) => _ConfigCard(config: configs[index], controller: controller),
    );
  }
}

/// 配置卡片组件
class _ConfigCard extends StatelessWidget {
  final AIConfigModel config;
  final AIConfigController controller;

  const _ConfigCard({required this.config, required this.controller});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: isDark ? colorScheme.outline.withAlpha(51) : colorScheme.outline.withAlpha(26)),
      ),
      child: InkWell(
        onTap: () => controller.editConfig(config),
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      config.name,
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: colorScheme.onSurface),
                    ),
                  ),
                  _buildStatusChip(config.isDefault, colorScheme),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                config.apiAddress.isEmpty ? '未设置API地址' : config.apiAddress,
                style: TextStyle(
                  fontSize: 14,
                  color:
                      config.apiAddress.isEmpty
                          ? colorScheme.error.withAlpha(204)
                          : colorScheme.onSurface.withAlpha(153),
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// 构建状态标签
  Widget _buildStatusChip(bool isDefault, ColorScheme colorScheme) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: isDefault ? colorScheme.primary.withAlpha(26) : colorScheme.error.withAlpha(26),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        isDefault ? '默认' : '未设为默认',
        style: TextStyle(fontSize: 12, color: isDefault ? colorScheme.primary : colorScheme.error),
      ),
    );
  }
}

/// 信息对话框组件
class _InfoDialog extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return AlertDialog(
      title: Row(
        children: [
          Icon(Icons.help_outline, color: colorScheme.primary),
          const SizedBox(width: 8),
          const Text('AI配置说明'),
        ],
      ),
      content: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            _buildInfoItem('通用配置', '用于所有AI功能的基础配置，可被其他类型配置继承'),
            const SizedBox(height: 12),
            _buildInfoItem('文章总结', '用于生成文章摘要和关键点提取'),
            const SizedBox(height: 12),
            _buildInfoItem('书本解读', '用于解析书籍内容和生成阅读笔记'),
            const SizedBox(height: 12),
            _buildInfoItem('日记总结', '用于分析和生成日记内容'),
            const SizedBox(height: 16),
            const Divider(),
            const SizedBox(height: 8),
            Text('通过不同功能类型的配置，您可以针对特定任务优化AI性能。', style: TextStyle(color: colorScheme.onSurface.withAlpha(179))),
          ],
        ),
      ),
      actions: [TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('了解了'))],
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    );
  }

  Widget _buildInfoItem(String title, String description) {
    final textTheme = Get.textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
        const SizedBox(height: 4),
        Text(description, style: textTheme.bodyMedium?.copyWith(color: Colors.grey[600])),
      ],
    );
  }
}
