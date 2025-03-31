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
    final colorScheme = AppTheme.getColorScheme(context);

    return Scaffold(
      backgroundColor: colorScheme.background,
      appBar: AppBar(
        title: const Text('AI 配置管理'),
        centerTitle: true,
        elevation: 0,
        backgroundColor: colorScheme.surface,
        actions: [
          IconButton(
            icon: const Icon(Icons.info_outline),
            tooltip: 'AI配置说明',
            onPressed: () => _showInfoDialog(context),
          ),
        ],
      ),
      body: Column(
        children: [
          _buildFunctionTypeSelector(context),
          Expanded(
            child: Obx(() {
              if (controller.isLoading.value) {
                return Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      CircularProgressIndicator(color: colorScheme.primary),
                      const SizedBox(height: 16),
                      Text('加载配置中...', style: TextStyle(color: colorScheme.onBackground.withOpacity(0.6))),
                    ],
                  ),
                );
              } else {
                final configsOfType = controller.getConfigsByType(controller.selectedFunctionType.value);
                if (configsOfType.isEmpty) {
                  return Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.settings_outlined, size: 64, color: colorScheme.onBackground.withOpacity(0.2)),
                        const SizedBox(height: 16),
                        Text(
                          '没有配置',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                            color: colorScheme.onBackground.withOpacity(0.7),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          '点击下方按钮添加新配置',
                          style: TextStyle(fontSize: 14, color: colorScheme.onBackground.withOpacity(0.5)),
                        ),
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
                } else {
                  return _buildConfigList(context, configsOfType);
                }
              }
            }),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: controller.createNewConfig,
        backgroundColor: colorScheme.primary,
        foregroundColor: colorScheme.onPrimary,
        child: const Icon(Icons.add),
      ),
    );
  }

  /// 显示AI配置信息对话框
  void _showInfoDialog(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    showDialog(
      context: context,
      builder:
          (context) => AlertDialog(
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
                  Text('通过不同功能类型的配置，您可以针对特定任务优化AI性能。', style: TextStyle(color: colorScheme.onSurface.withOpacity(0.7))),
                ],
              ),
            ),
            actions: [TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('了解了'))],
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          ),
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

  /// 构建功能类型选择器
  Widget _buildFunctionTypeSelector(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Container(
      padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 4),
      decoration: BoxDecoration(
        color: colorScheme.surface,
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 4, offset: const Offset(0, 2))],
      ),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        child: Row(
          children: [
            _buildFunctionTypeChip(context, 0, '通用配置', Icons.settings),
            const SizedBox(width: 12),
            _buildFunctionTypeChip(context, 1, '文章总结', Icons.article),
            const SizedBox(width: 12),
            _buildFunctionTypeChip(context, 2, '书本解读', Icons.book),
            const SizedBox(width: 12),
            _buildFunctionTypeChip(context, 3, '日记总结', Icons.edit_note),
          ],
        ),
      ),
    );
  }

  /// 构建功能类型筹码
  Widget _buildFunctionTypeChip(BuildContext context, int type, String label, IconData icon) {
    final colorScheme = AppTheme.getColorScheme(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Obx(() {
      final isSelected = controller.selectedFunctionType.value == type;
      return AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: () => controller.selectedFunctionType.value = type,
            borderRadius: BorderRadius.circular(20),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              decoration: BoxDecoration(
                color:
                    isSelected
                        ? colorScheme.primary
                        : (isDark ? colorScheme.surface.withOpacity(0.3) : colorScheme.surface),
                borderRadius: BorderRadius.circular(20),
                border: Border.all(
                  color:
                      isSelected
                          ? colorScheme.primary
                          : (isDark ? colorScheme.onSurface.withOpacity(0.3) : colorScheme.outline.withOpacity(0.3)),
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
                            : (isDark
                                ? colorScheme.onSurface.withOpacity(0.8)
                                : colorScheme.onSurface.withOpacity(0.6)),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    label,
                    style: TextStyle(
                      color:
                          isSelected
                              ? Colors.white
                              : (isDark
                                  ? colorScheme.onSurface.withOpacity(0.9)
                                  : colorScheme.onSurface.withOpacity(0.7)),
                      fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
    });
  }

  /// 构建配置列表
  Widget _buildConfigList(BuildContext context, List<AIConfigModel> configs) {
    return ListView.builder(
      physics: const BouncingScrollPhysics(),
      padding: const EdgeInsets.all(16),
      itemCount: configs.length,
      itemBuilder: (context, index) => _buildConfigCard(context, configs[index]),
    );
  }

  /// 构建配置卡片
  Widget _buildConfigCard(BuildContext context, AIConfigModel config) {
    final colorScheme = AppTheme.getColorScheme(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      decoration: BoxDecoration(
        color: isDark ? colorScheme.surfaceVariant.withOpacity(0.4) : colorScheme.surface,
        borderRadius: BorderRadius.circular(16),
        boxShadow:
            isDark
                ? [BoxShadow(color: Colors.black.withOpacity(0.2), blurRadius: 8, offset: const Offset(0, 2))]
                : [BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 标题栏
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
            decoration: BoxDecoration(
              color:
                  config.isDefault
                      ? colorScheme.primary
                      : (isDark
                          ? colorScheme.surfaceVariant.withOpacity(0.2)
                          : colorScheme.surfaceVariant.withOpacity(0.5)),
              borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Row(
                    children: [
                      Container(
                        width: 40,
                        height: 40,
                        decoration: BoxDecoration(
                          color:
                              config.isDefault
                                  ? Colors.white.withOpacity(0.2)
                                  : colorScheme.onSurface.withOpacity(0.05),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Icon(
                          _getConfigIcon(config.functionType),
                          color: config.isDefault ? Colors.white : colorScheme.onSurface.withOpacity(0.6),
                          size: 22,
                        ),
                      ),
                      const SizedBox(width: 16),
                      Flexible(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              config.name,
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.w600,
                                color: config.isDefault ? Colors.white : colorScheme.onSurface,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            if (config.inheritFromGeneral && config.functionType != 0)
                              Padding(
                                padding: const EdgeInsets.only(top: 4),
                                child: Text(
                                  '继承自通用配置',
                                  style: TextStyle(
                                    fontSize: 13,
                                    color:
                                        config.isDefault
                                            ? Colors.white.withOpacity(0.8)
                                            : colorScheme.onSurface.withOpacity(0.6),
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                if (config.isDefault)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                    decoration: BoxDecoration(
                      color: Colors.white.withOpacity(0.2),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.check_circle, size: 16, color: Colors.white),
                        const SizedBox(width: 4),
                        const Text(
                          '默认',
                          style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.white),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
          // 内容
          Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildConfigDetailItem(context, 'API 地址', config.apiAddress, Icons.link_rounded),
                const SizedBox(height: 14),
                _buildConfigDetailItem(
                  context,
                  'API 令牌',
                  config.apiToken.isNotEmpty ? '•••••••••••••••' : '未设置',
                  Icons.vpn_key_rounded,
                ),
                const SizedBox(height: 14),
                _buildConfigDetailItem(context, '模型名称', config.modelName, Icons.smart_toy_rounded),
                const SizedBox(height: 20),
                _buildActionsRow(context, config),
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// 获取配置图标
  IconData _getConfigIcon(int functionType) {
    switch (functionType) {
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

  /// 构建配置详情项
  Widget _buildConfigDetailItem(BuildContext context, String label, String value, IconData icon) {
    final colorScheme = AppTheme.getColorScheme(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final isEmpty = value.isEmpty || value == '未设置';

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: isDark ? colorScheme.primary.withOpacity(0.2) : colorScheme.primaryContainer.withOpacity(0.5),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Icon(icon, color: colorScheme.primary, size: 18),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: isDark ? colorScheme.onSurface.withOpacity(0.85) : colorScheme.onSurface.withOpacity(0.7),
                ),
              ),
              const SizedBox(height: 4),
              Text(
                isEmpty ? '未设置' : value,
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: isEmpty ? FontWeight.normal : FontWeight.w500,
                  color:
                      isEmpty
                          ? colorScheme.error.withOpacity(isDark ? 0.85 : 0.7)
                          : (isDark ? colorScheme.onSurface : colorScheme.onSurface.withOpacity(0.9)),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  /// 构建操作按钮行
  Widget _buildActionsRow(BuildContext context, AIConfigModel config) {
    final colorScheme = AppTheme.getColorScheme(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Row(
      children: [
        if (!config.isDefault)
          _buildActionButton(
            context,
            label: '设为默认',
            icon: Icons.check_circle_outline,
            color: colorScheme.primary,
            onTap: () => controller.setAsDefault(config),
          ),
        const Spacer(),
        _buildActionButton(
          context,
          label: '克隆',
          icon: Icons.copy,
          color: isDark ? colorScheme.onSurface.withOpacity(0.9) : colorScheme.onSurface.withOpacity(0.6),
          onTap: () => controller.cloneConfig(config),
        ),
        const SizedBox(width: 8),
        _buildActionButton(
          context,
          label: '编辑',
          icon: Icons.edit,
          color: isDark ? colorScheme.onSurface.withOpacity(0.9) : colorScheme.onSurface.withOpacity(0.6),
          onTap: () => controller.editConfig(config),
        ),
        if (!config.isDefault)
          _buildActionButton(
            context,
            label: '删除',
            icon: Icons.delete_outline,
            color: isDark ? colorScheme.error.withOpacity(0.9) : colorScheme.error,
            onTap: () => _confirmDelete(context, config),
          ),
      ],
    );
  }

  /// 构建操作按钮
  Widget _buildActionButton(
    BuildContext context, {
    required String label,
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    return TextButton.icon(
      onPressed: onTap,
      icon: Icon(icon, size: 16, color: color),
      label: Text(label, style: TextStyle(color: color)),
      style: TextButton.styleFrom(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      ),
    );
  }

  /// 确认删除对话框
  void _confirmDelete(BuildContext context, AIConfigModel config) {
    final colorScheme = AppTheme.getColorScheme(context);

    showDialog(
      context: context,
      builder:
          (context) => AlertDialog(
            title: Row(
              children: [
                Icon(Icons.warning_amber_rounded, color: colorScheme.error),
                const SizedBox(width: 8),
                const Text('确认删除'),
              ],
            ),
            content: Text('确定要删除配置"${config.name}"吗？此操作不可撤销。'),
            actions: [
              TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('取消')),
              ElevatedButton.icon(
                onPressed: () {
                  Navigator.of(context).pop();
                  controller.deleteConfig(config);
                },
                icon: const Icon(Icons.delete_outline, size: 16),
                label: const Text('删除'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: colorScheme.error,
                  foregroundColor: colorScheme.onError,
                ),
              ),
            ],
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          ),
    );
  }
}
