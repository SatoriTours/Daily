import 'package:daily_satori/app/components/common/feature_icon.dart';
import 'package:daily_satori/app/styles/app_styles.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/dimensions.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import '../controllers/settings_controller.dart';

/// 设置页面
/// 包含三个主要部分：
/// 1. 功能设置：AI配置、插件管理等
/// 2. 数据管理：备份恢复、清理维护等
/// 3. 系统设置：Web服务器、版本更新等
class SettingsView extends GetView<SettingsController> {
  const SettingsView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.getColorScheme(context).surface,
      appBar: AppBar(
        title: const Text('设置'),
        centerTitle: true,
        elevation: 0,
        backgroundColor: AppTheme.getColorScheme(context).surface,
        actions: [
          IconButton(icon: const Icon(Icons.info_outline), tooltip: '关于', onPressed: () => _showAboutDialog(context)),
        ],
      ),
      body: Obx(() => controller.isLoading.value ? AppStyles.loadingState(context) : _buildSettingsList(context)),
    );
  }

  /// 显示关于对话框
  void _showAboutDialog(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    showAboutDialog(
      context: context,
      applicationName: 'Daily Satori',
      applicationVersion: 'v${controller.appVersion.value}',
      applicationIcon: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(color: colorScheme.primary, borderRadius: BorderRadius.circular(10)),
        child: Icon(Icons.article, color: colorScheme.onPrimary),
      ),
      children: [Dimensions.verticalSpacerM, Text('您的个性化阅读助手，支持文章收藏、AI内容分析和日记管理。', style: textTheme.bodyMedium)],
    );
  }

  /// 构建设置列表
  Widget _buildSettingsList(BuildContext context) {
    return ListView(
      physics: const BouncingScrollPhysics(),
      padding: Dimensions.paddingM,
      children: [
        _buildFunctionSection(context),
        _buildDataSection(context),
        _buildSystemSection(context),
        Dimensions.verticalSpacerL,
        _buildVersionInfo(context),
        Dimensions.verticalSpacerL,
      ],
    );
  }

  /// 构建功能设置分区
  Widget _buildFunctionSection(BuildContext context) {
    return _buildSettingsSection(
      context: context,
      title: '功能',
      icon: Icons.flash_on_rounded,
      items: [
        _buildSettingItem(
          context: context,
          title: 'AI 配置',
          subtitle: '设置多个场景下的 AI 模型',
          icon: Icons.smart_toy_rounded,
          color: Colors.blue,
          onTap: () => Get.toNamed('/ai-config'),
        ),
        _buildSettingItem(
          context: context,
          title: '插件中心',
          subtitle: '管理扩展插件与订阅源',
          icon: Icons.extension_rounded,
          color: Colors.deepPurple,
          onTap: () => Get.toNamed(Routes.pluginCenter),
        ),
      ],
    );
  }

  /// 构建数据管理分区
  Widget _buildDataSection(BuildContext context) {
    return _buildSettingsSection(
      context: context,
      title: '数据',
      icon: Icons.storage_rounded,
      items: [
        _buildSettingItem(
          context: context,
          title: '备份与恢复',
          subtitle: '保护您的数据安全',
          icon: Icons.backup_rounded,
          color: Colors.green,
          onTap: () => Get.toNamed(Routes.backupSettings),
        ),
        _buildSettingItem(
          context: context,
          title: '清理与维护',
          subtitle: '优化应用性能与存储空间',
          icon: Icons.cleaning_services_rounded,
          color: Colors.orange,
          onTap: () => _showCleanupDialog(context),
        ),
      ],
    );
  }

  /// 构建系统设置分区
  Widget _buildSystemSection(BuildContext context) {
    return _buildSettingsSection(
      context: context,
      title: '系统',
      icon: Icons.settings_rounded,
      items: [
        _buildSettingItem(
          context: context,
          title: 'Web服务器',
          subtitle: '配置远程访问与代理设置',
          icon: Icons.language_rounded,
          color: Colors.teal,
          onTap: () => _showWebServerDialog(context),
        ),
        _buildSettingItem(
          context: context,
          title: '检查更新',
          subtitle: '获取最新版本',
          icon: Icons.system_update_rounded,
          color: Colors.indigo,
          onTap: () => AppUpgradeService.i.checkAndDownload(),
        ),
      ],
    );
  }

  /// 构建设置分区
  Widget _buildSettingsSection({
    required BuildContext context,
    required String title,
    required IconData icon,
    required List<Widget> items,
  }) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 分区标题
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: Row(
              children: [
                Icon(icon, size: 16, color: colorScheme.primary),
                Dimensions.horizontalSpacerS,
                Text(
                  title,
                  style: textTheme.labelMedium?.copyWith(
                    color: colorScheme.primary,
                    fontWeight: FontWeight.w600,
                    letterSpacing: 0.2,
                  ),
                ),
              ],
            ),
          ),
          // 分区内容
          Card(
            margin: EdgeInsets.zero,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusM)),
            child: Column(children: items),
          ),
        ],
      ),
    );
  }

  /// 构建设置项
  Widget _buildSettingItem({
    required BuildContext context,
    required String title,
    required String subtitle,
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        child: Row(
          children: [
            FeatureIcon(icon: icon, iconColor: color, containerSize: 32, iconSize: 16),
            Dimensions.horizontalSpacerM,
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: textTheme.titleSmall),
                  Text(
                    subtitle,
                    style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: 179)),
                  ),
                ],
              ),
            ),
            Icon(Icons.chevron_right, color: colorScheme.onSurface.withValues(alpha: 77), size: 18),
          ],
        ),
      ),
    );
  }

  /// 构建版本信息
  Widget _buildVersionInfo(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Center(
      child: Column(
        children: [
          Obx(
            () => Text(
              '版本 ${controller.appVersion.value}',
              style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.6)),
            ),
          ),
          Dimensions.verticalSpacerS,
          Text(
            '© 2023-2024 Satori Tours',
            style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.5), fontSize: 11),
          ),
        ],
      ),
    );
  }

  // 对话框显示方法
  void _showCleanupDialog(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder:
          (context) => Container(
            padding: const EdgeInsets.fromLTRB(24, 16, 24, 32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 标题
                Row(
                  children: [
                    FeatureIcon(
                      icon: Icons.cleaning_services_rounded,
                      iconColor: Colors.orange,
                      containerSize: 36,
                      iconSize: 20,
                    ),
                    Dimensions.horizontalSpacerM,
                    Text('清理与维护', style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
                    const Spacer(),
                    IconButton(icon: const Icon(Icons.close, size: 20), onPressed: () => Navigator.pop(context)),
                  ],
                ),
                Dimensions.verticalSpacerM,

                // 存储分析
                _buildCleanupSection(
                  context: context,
                  title: '存储分析',
                  subtitle: '分析应用占用的存储空间',
                  icon: Icons.storage_rounded,
                  color: Colors.blue,
                  onTap: () {
                    Navigator.pop(context);
                    controller.analyzeStorage();
                  },
                ),

                // 清除缓存
                _buildCleanupSection(
                  context: context,
                  title: '清除缓存',
                  subtitle: '清除应用临时文件和缓存数据',
                  icon: Icons.delete_sweep_rounded,
                  color: Colors.red,
                  onTap: () {
                    Navigator.pop(context);
                    controller.clearCache();
                  },
                ),

                // 数据库优化
                _buildCleanupSection(
                  context: context,
                  title: '数据库优化',
                  subtitle: '优化数据库结构，提升应用性能',
                  icon: Icons.build_rounded,
                  color: Colors.deepPurple,
                  onTap: () {
                    Navigator.pop(context);
                    controller.optimizeDatabase();
                  },
                ),

                // 恢复出厂设置
                _buildCleanupSection(
                  context: context,
                  title: '恢复出厂设置',
                  subtitle: '将应用恢复到初始状态（不会删除数据）',
                  icon: Icons.restart_alt_rounded,
                  color: Colors.deepOrange,
                  onTap: () {
                    Navigator.pop(context);
                    _showFactoryResetConfirmDialog(context);
                  },
                ),

                Dimensions.verticalSpacerM,
                Text(
                  '提示：定期清理缓存可以释放存储空间，数据库优化可以提高应用运行速度。',
                  style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.5)),
                ),
              ],
            ),
          ),
    );
  }

  void _showFactoryResetConfirmDialog(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    showDialog(
      context: context,
      builder:
          (context) => AlertDialog(
            title: const Text('确认恢复出厂设置'),
            content: const Text('这将重置所有应用设置，但不会删除您的数据。此操作不可撤销，是否继续？'),
            actions: [
              TextButton(
                child: Text('取消', style: TextStyle(color: colorScheme.onSurface)),
                onPressed: () => Navigator.pop(context),
              ),
              ElevatedButton(
                style: ElevatedButton.styleFrom(backgroundColor: Colors.red),
                child: const Text('确认重置'),
                onPressed: () {
                  Navigator.pop(context);
                  controller.factoryReset();
                },
              ),
            ],
          ),
    );
  }

  void _showWebServerDialog(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);
    final primaryColor = colorScheme.primary;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder:
          (context) => Container(
            padding: const EdgeInsets.fromLTRB(24, 16, 24, 32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 标题
                Row(
                  children: [
                    FeatureIcon(icon: Icons.language_rounded, iconColor: primaryColor, containerSize: 36, iconSize: 20),
                    Dimensions.horizontalSpacerM,
                    Text('Web服务器', style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
                    const Spacer(),
                    IconButton(icon: const Icon(Icons.close, size: 20), onPressed: () => Navigator.pop(context)),
                  ],
                ),
                Dimensions.verticalSpacerM,

                // 本地访问地址
                _buildInfoCard(
                  context: context,
                  title: 'HTTP服务器地址',
                  content: Obx(
                    () => Text(
                      controller.webServiceAddress.value,
                      style: textTheme.bodyMedium?.copyWith(color: primaryColor, fontWeight: FontWeight.w500),
                    ),
                  ),
                  icon: Icons.language_rounded,
                  action: IconButton(
                    icon: Icon(Icons.copy_rounded, size: 18, color: primaryColor),
                    onPressed: () {
                      controller.copyWebServiceAddress();
                      UIUtils.showSuccess('已复制到剪贴板');
                    },
                    tooltip: '复制',
                  ),
                ),
                Dimensions.verticalSpacerM,

                // WebSocket远程访问地址
                _buildInfoCard(
                  context: context,
                  title: 'WebSocket远程访问',
                  content: Obx(
                    () => Text(
                      controller.webAccessUrl.value,
                      style: textTheme.bodyMedium?.copyWith(color: primaryColor, fontWeight: FontWeight.w500),
                    ),
                  ),
                  icon: Icons.wifi_rounded,
                  action: IconButton(
                    icon: Icon(Icons.copy_rounded, size: 18, color: primaryColor),
                    onPressed: () {
                      controller.copyWebAccessUrl();
                      UIUtils.showSuccess('已复制到剪贴板');
                    },
                    tooltip: '复制',
                  ),
                ),
                Dimensions.verticalSpacerM,

                // WebSocket连接状态
                _buildInfoCard(
                  context: context,
                  title: 'WebSocket连接状态',
                  content: Obx(
                    () => Row(
                      children: [
                        Container(
                          width: 10,
                          height: 10,
                          decoration: BoxDecoration(
                            color: controller.isWebSocketConnected.value ? Colors.green : Colors.red,
                            shape: BoxShape.circle,
                          ),
                        ),
                        Dimensions.horizontalSpacerS,
                        Text(
                          controller.isWebSocketConnected.value ? '已连接' : '未连接',
                          style: textTheme.bodyMedium?.copyWith(
                            color: controller.isWebSocketConnected.value ? Colors.green : Colors.red,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                  ),
                  icon: Icons.network_check_rounded,
                ),
                Dimensions.verticalSpacerM,

                // 密码设置
                _buildWebServerSetting(
                  context: context,
                  title: '服务器密码',
                  icon: Icons.password_rounded,
                  onTap: () => _showPasswordSettingDialog(context),
                ),

                // 重启服务器
                _buildWebServerSetting(
                  context: context,
                  title: '重启服务',
                  icon: Icons.refresh_rounded,
                  onTap: () => controller.restartWebService(),
                ),

                Dimensions.verticalSpacerM,
                Text(
                  '提示：确保设备在同一WiFi网络下才能访问HTTP服务器地址。WebSocket远程访问允许在任何网络环境下连接到您的应用。',
                  style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.5)),
                ),
              ],
            ),
          ),
    );
  }

  void _showPasswordSettingDialog(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final controller = TextEditingController(text: this.controller.getWebServerPassword());

    showDialog(
      context: context,
      builder:
          (context) => AlertDialog(
            title: const Text('设置服务器密码'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text('设置Web服务器访问密码，为空则不需要密码'),
                Dimensions.verticalSpacerM,
                TextField(
                  controller: controller,
                  decoration: const InputDecoration(labelText: '密码', border: OutlineInputBorder()),
                ),
              ],
            ),
            actions: [
              TextButton(
                child: Text('取消', style: TextStyle(color: colorScheme.onSurface)),
                onPressed: () => Navigator.pop(context),
              ),
              ElevatedButton(
                child: const Text('保存'),
                onPressed: () {
                  Navigator.pop(context);
                  this.controller.saveWebServerPassword(controller.text);
                },
              ),
            ],
          ),
    );
  }

  /// 构建清理选项
  Widget _buildCleanupSection({
    required BuildContext context,
    required String title,
    required String subtitle,
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        child: Row(
          children: [
            FeatureIcon(icon: icon, iconColor: color, containerSize: 36, iconSize: 18),
            Dimensions.horizontalSpacerM,
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: textTheme.titleSmall),
                  Text(
                    subtitle,
                    style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: 179)),
                  ),
                ],
              ),
            ),
            Icon(Icons.chevron_right, color: colorScheme.onSurface.withValues(alpha: 77), size: 18),
          ],
        ),
      ),
    );
  }

  /// 构建信息卡片
  Widget _buildInfoCard({
    required BuildContext context,
    required String title,
    required Widget content,
    required IconData icon,
    Color? color,
    Widget? action,
  }) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);
    final cardColor = color ?? colorScheme.primary;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colorScheme.surfaceVariant.withValues(alpha: 0.5),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: colorScheme.outline.withValues(alpha: 0.2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 18, color: cardColor),
              Dimensions.horizontalSpacerS,
              Text(title, style: textTheme.labelMedium?.copyWith(color: cardColor, fontWeight: FontWeight.w600)),
              const Spacer(),
              if (action != null) action,
            ],
          ),
          Dimensions.verticalSpacerM,
          content,
        ],
      ),
    );
  }

  /// 构建Web服务器设置项
  Widget _buildWebServerSetting({
    required BuildContext context,
    required String title,
    required IconData icon,
    Color? color,
    required VoidCallback onTap,
  }) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);
    final itemColor = color ?? colorScheme.primary;

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: colorScheme.surfaceVariant.withValues(alpha: 0.5),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: colorScheme.outline.withValues(alpha: 0.2)),
        ),
        child: Row(
          children: [
            Icon(icon, size: 20, color: itemColor),
            Dimensions.horizontalSpacerM,
            Text(title, style: textTheme.titleSmall?.copyWith(color: itemColor, fontWeight: FontWeight.w500)),
            const Spacer(),
            Icon(Icons.chevron_right, color: colorScheme.onSurface.withValues(alpha: 0.3), size: 18),
          ],
        ),
      ),
    );
  }
}
