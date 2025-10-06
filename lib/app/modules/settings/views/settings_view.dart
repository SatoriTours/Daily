import 'package:daily_satori/app/components/common/feature_icon.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import '../controllers/settings_controller.dart';

/// 设置页面视图
///
/// 提供应用的主要设置功能，包括：
/// - 功能设置：AI配置、插件管理
/// - 系统管理：备份恢复、Web服务器、版本更新
class SettingsView extends GetView<SettingsController> {
  const SettingsView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.getColorScheme(context).surface,
      appBar: _buildAppBar(context),
      body: Obx(() => controller.isLoading.value ? StyleGuide.getLoadingState(context) : _buildSettingsList(context)),
    );
  }

  // ==================== AppBar ====================

  /// 构建顶部导航栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      title: const Text('设置'),
      centerTitle: true,
      elevation: 0,
      backgroundColor: AppTheme.getColorScheme(context).surface,
      actions: [
        IconButton(icon: const Icon(Icons.info_outline), tooltip: '关于', onPressed: () => _showAboutDialog(context)),
      ],
    );
  }

  // ==================== 主布局 ====================

  /// 构建设置列表主体
  Widget _buildSettingsList(BuildContext context) {
    return Column(
      children: [
        Expanded(
          child: ListView(
            physics: const BouncingScrollPhysics(),
            padding: Dimensions.paddingM,
            children: [_buildFunctionSection(context), _buildSystemSection(context), Dimensions.verticalSpacerL],
          ),
        ),
        _buildVersionInfo(context),
        Dimensions.verticalSpacerM,
      ],
    );
  }

  // ==================== 功能设置分区 ====================

  /// 构建功能设置分区
  /// 包含AI配置、插件中心等功能入口
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

  // ==================== 系统设置分区 ====================

  /// 构建系统设置分区
  /// 包含备份恢复、Web服务器、版本更新等系统功能
  Widget _buildSystemSection(BuildContext context) {
    return _buildSettingsSection(
      context: context,
      title: '系统',
      icon: Icons.settings_rounded,
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

  // ==================== 通用组件 ====================

  /// 构建设置分区卡片
  ///
  /// 参数：
  /// - [title] 分区标题
  /// - [icon] 分区图标
  /// - [items] 分区内的设置项列表
  Widget _buildSettingsSection({
    required BuildContext context,
    required String title,
    required IconData icon,
    required List<Widget> items,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 分区标题
          _buildSectionHeader(context, title, icon),
          // 分区内容卡片
          Card(
            margin: EdgeInsets.zero,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusM)),
            child: Column(children: items),
          ),
        ],
      ),
    );
  }

  /// 构建分区标题
  Widget _buildSectionHeader(BuildContext context, String title, IconData icon) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Padding(
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
    );
  }

  /// 构建单个设置项
  ///
  /// 参数：
  /// - [title] 设置项标题
  /// - [subtitle] 设置项描述
  /// - [icon] 设置项图标
  /// - [color] 图标颜色
  /// - [onTap] 点击回调
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

  // ==================== 对话框 ====================

  /// 显示关于应用对话框
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
      children: [
        Dimensions.verticalSpacerM,
        Text('您的个性化阅读助手，支持文章收藏、AI内容分析和日记管理。', style: textTheme.bodyMedium),
      ],
    );
  }

  /// 显示Web服务器配置对话框
  void _showWebServerDialog(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);
    final primaryColor = colorScheme.primary;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: Dimensions.borderRadiusTop),
      builder: (context) => DraggableScrollableSheet(
        initialChildSize: 0.9,
        minChildSize: 0.5,
        maxChildSize: 0.95,
        expand: false,
        builder: (context, scrollController) => SafeArea(
          child: Column(
            children: [
              // 标题栏
              _buildWebServerDialogHeader(context, primaryColor),
              // 内容区域
              Expanded(
                child: SingleChildScrollView(
                  controller: scrollController,
                  padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // 服务器信息
                      _buildServerInfoSection(context, primaryColor, textTheme),
                      Dimensions.verticalSpacerL,
                      // 服务器管理
                      _buildServerManagementSection(context),
                      Dimensions.verticalSpacerL,
                      // 提示信息
                      _buildServerTipCard(context, primaryColor, colorScheme),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// 构建Web服务器对话框标题栏
  Widget _buildWebServerDialogHeader(BuildContext context, Color primaryColor) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 16, 24, 16),
      child: Row(
        children: [
          FeatureIcon(icon: Icons.language_rounded, iconColor: primaryColor, containerSize: 40, iconSize: 22),
          Dimensions.horizontalSpacerM,
          Text('Web服务器', style: textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600)),
          const Spacer(),
          IconButton(
            icon: Icon(Icons.close_rounded, size: 22, color: colorScheme.onSurface.withValues(alpha: 0.6)),
            onPressed: () => Navigator.pop(context),
            tooltip: '关闭',
          ),
        ],
      ),
    );
  }

  /// 构建服务器信息分区
  Widget _buildServerInfoSection(BuildContext context, Color primaryColor, TextTheme textTheme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSectionTitle(context, '服务器信息', Icons.info_outline_rounded),
        Dimensions.verticalSpacerM,
        // HTTP服务器地址
        _buildHttpAddressCard(context, primaryColor, textTheme),
        Dimensions.verticalSpacerM,
        // WebSocket地址
        _buildWebSocketAddressCard(context, primaryColor, textTheme),
        Dimensions.verticalSpacerM,
        // 连接状态
        _buildConnectionStatusCard(context, textTheme),
      ],
    );
  }

  /// 构建HTTP服务器地址卡片
  Widget _buildHttpAddressCard(BuildContext context, Color primaryColor, TextTheme textTheme) {
    return _buildInfoCard(
      context: context,
      title: 'HTTP服务器地址',
      content: Obx(
        () => SelectableText(
          controller.webServiceAddress.value,
          style: textTheme.bodyMedium?.copyWith(color: primaryColor, fontWeight: FontWeight.w600),
        ),
      ),
      icon: Icons.language_rounded,
      iconColor: Colors.blue,
      action: IconButton(
        icon: Icon(Icons.copy_rounded, size: 20, color: primaryColor),
        onPressed: () {
          controller.copyWebServiceAddress();
          UIUtils.showSuccess('已复制到剪贴板');
        },
        tooltip: '复制地址',
      ),
    );
  }

  /// 构建WebSocket地址卡片
  Widget _buildWebSocketAddressCard(BuildContext context, Color primaryColor, TextTheme textTheme) {
    return _buildInfoCard(
      context: context,
      title: 'WebSocket远程访问',
      content: Obx(
        () => SelectableText(
          controller.webAccessUrl.value,
          style: textTheme.bodyMedium?.copyWith(color: primaryColor, fontWeight: FontWeight.w600),
        ),
      ),
      icon: Icons.wifi_rounded,
      iconColor: Colors.deepPurple,
      action: IconButton(
        icon: Icon(Icons.copy_rounded, size: 20, color: primaryColor),
        onPressed: () {
          controller.copyWebAccessUrl();
          UIUtils.showSuccess('已复制到剪贴板');
        },
        tooltip: '复制地址',
      ),
    );
  }

  /// 构建连接状态卡片
  Widget _buildConnectionStatusCard(BuildContext context, TextTheme textTheme) {
    return _buildInfoCard(
      context: context,
      title: '连接状态',
      content: Obx(() => _buildConnectionStatusIndicator(textTheme)),
      icon: Icons.network_check_rounded,
      iconColor: Colors.green,
    );
  }

  /// 构建连接状态指示器
  Widget _buildConnectionStatusIndicator(TextTheme textTheme) {
    final isConnected = controller.isWebSocketConnected.value;
    final statusColor = isConnected ? Colors.green : Colors.red;

    return Row(
      children: [
        Container(
          width: 10,
          height: 10,
          decoration: BoxDecoration(
            color: statusColor,
            shape: BoxShape.circle,
            boxShadow: [BoxShadow(color: statusColor.withValues(alpha: 0.3), blurRadius: 4, spreadRadius: 1)],
          ),
        ),
        Dimensions.horizontalSpacerS,
        Text(
          isConnected ? '已连接' : '未连接',
          style: textTheme.bodyMedium?.copyWith(color: statusColor, fontWeight: FontWeight.w600),
        ),
      ],
    );
  }

  /// 构建服务器管理分区
  Widget _buildServerManagementSection(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSectionTitle(context, '服务器管理', Icons.settings_rounded),
        Dimensions.verticalSpacerM,
        // 密码设置
        _buildWebServerSetting(
          context: context,
          title: '服务器密码',
          subtitle: '设置Web服务器访问密码',
          icon: Icons.password_rounded,
          color: Colors.orange,
          onTap: () => _showPasswordSettingDialog(context),
        ),
        Dimensions.verticalSpacerS,
        // 重启服务
        _buildWebServerSetting(
          context: context,
          title: '重启服务',
          subtitle: '重启Web和WebSocket服务',
          icon: Icons.refresh_rounded,
          color: Colors.green,
          onTap: () => controller.restartWebService(),
        ),
      ],
    );
  }

  /// 构建服务器提示卡片
  Widget _buildServerTipCard(BuildContext context, Color primaryColor, ColorScheme colorScheme) {
    final textTheme = AppTheme.getTextTheme(context);

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: primaryColor.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        border: Border.all(color: primaryColor.withValues(alpha: 0.2), width: 1),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.lightbulb_outline_rounded, size: 18, color: primaryColor),
          Dimensions.horizontalSpacerS,
          Expanded(
            child: Text(
              '确保设备在同一WiFi网络下才能访问HTTP服务器。WebSocket远程访问可在任何网络环境下使用。',
              style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.7), height: 1.4),
            ),
          ),
        ],
      ),
    );
  }

  /// 显示密码设置对话框
  void _showPasswordSettingDialog(BuildContext context) {
    final passwordController = TextEditingController(text: controller.getWebServerPassword());
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);
    final isPasswordVisible = false.obs;

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusL)),
        contentPadding: const EdgeInsets.fromLTRB(24, 20, 24, 0),
        actionsPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        title: _buildPasswordDialogTitle(),
        content: _buildPasswordDialogContent(context, passwordController, isPasswordVisible, colorScheme, textTheme),
        actions: _buildPasswordDialogActions(context, passwordController),
      ),
    );
  }

  /// 构建密码对话框标题
  Widget _buildPasswordDialogTitle() {
    final textTheme = Get.context != null ? AppTheme.getTextTheme(Get.context!) : null;

    return Row(
      children: [
        FeatureIcon(icon: Icons.password_rounded, iconColor: Colors.orange, containerSize: 36, iconSize: 20),
        Dimensions.horizontalSpacerM,
        Text('服务器密码', style: textTheme?.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
      ],
    );
  }

  /// 构建密码对话框内容
  Widget _buildPasswordDialogContent(
    BuildContext context,
    TextEditingController passwordController,
    RxBool isPasswordVisible,
    ColorScheme colorScheme,
    TextTheme textTheme,
  ) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 说明文字
        _buildPasswordTipCard(context, colorScheme, textTheme),
        Dimensions.verticalSpacerM,
        // 密码输入框
        _buildPasswordTextField(context, passwordController, isPasswordVisible, colorScheme, textTheme),
      ],
    );
  }

  /// 构建密码提示卡片
  Widget _buildPasswordTipCard(BuildContext context, ColorScheme colorScheme, TextTheme textTheme) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: colorScheme.primary.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        border: Border.all(color: colorScheme.primary.withValues(alpha: 0.2), width: 1),
      ),
      child: Row(
        children: [
          Icon(Icons.info_outline_rounded, size: 16, color: colorScheme.primary),
          Dimensions.horizontalSpacerS,
          Expanded(
            child: Text(
              '设置Web服务器访问密码，留空则无需密码验证',
              style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.7), height: 1.3),
            ),
          ),
        ],
      ),
    );
  }

  /// 构建密码输入框
  Widget _buildPasswordTextField(
    BuildContext context,
    TextEditingController passwordController,
    RxBool isPasswordVisible,
    ColorScheme colorScheme,
    TextTheme textTheme,
  ) {
    return Obx(
      () => TextField(
        controller: passwordController,
        obscureText: !isPasswordVisible.value,
        style: textTheme.bodyMedium,
        decoration: InputDecoration(
          labelText: '密码',
          hintText: '请输入服务器密码',
          prefixIcon: const Icon(Icons.lock_outline_rounded),
          suffixIcon: IconButton(
            icon: Icon(isPasswordVisible.value ? Icons.visibility_off_rounded : Icons.visibility_rounded, size: 20),
            onPressed: () => isPasswordVisible.value = !isPasswordVisible.value,
            tooltip: isPasswordVisible.value ? '隐藏密码' : '显示密码',
          ),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(Dimensions.radiusS),
            borderSide: BorderSide(color: colorScheme.outline, width: 1),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(Dimensions.radiusS),
            borderSide: BorderSide(color: colorScheme.primary, width: 2),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(Dimensions.radiusS),
            borderSide: BorderSide(color: colorScheme.outline, width: 1),
          ),
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        ),
      ),
    );
  }

  /// 构建密码对话框操作按钮
  List<Widget> _buildPasswordDialogActions(BuildContext context, TextEditingController passwordController) {
    return [
      TextButton(
        onPressed: () => Navigator.pop(context),
        style: ButtonStyles.getTextStyle(context),
        child: const Text('取消'),
      ),
      ElevatedButton(
        onPressed: () {
          Navigator.pop(context);
          controller.saveWebServerPassword(passwordController.text);
        },
        style: ButtonStyles.getPrimaryStyle(context),
        child: const Text('保存'),
      ),
    ];
  }

  // ==================== 通用组件 ====================

  /// 构建信息卡片
  ///
  /// 用于显示各类信息项，如服务器地址、连接状态等
  ///
  /// 参数：
  /// - [title] 卡片标题
  /// - [content] 卡片内容Widget
  /// - [icon] 图标
  /// - [iconColor] 图标颜色
  /// - [action] 可选的操作按钮（如复制按钮）
  Widget _buildInfoCard({
    required BuildContext context,
    required String title,
    required Widget content,
    required IconData icon,
    Color? color,
    Color? iconColor,
    Widget? action,
  }) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);
    final cardColor = color ?? colorScheme.primary;
    final cardIconColor = iconColor ?? cardColor;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        border: Border.all(color: colorScheme.outline.withValues(alpha: 0.2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 20, color: cardIconColor),
              Dimensions.horizontalSpacerS,
              Text(title, style: textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w600)),
              const Spacer(),
              if (action != null) action,
            ],
          ),
          Dimensions.verticalSpacerS,
          content,
        ],
      ),
    );
  }

  /// 构建Web服务器设置项
  ///
  /// 用于服务器管理分区的设置按钮
  ///
  /// 参数：
  /// - [title] 设置项标题
  /// - [subtitle] 设置项描述
  /// - [icon] 图标
  /// - [color] 图标颜色
  /// - [onTap] 点击回调
  Widget _buildWebServerSetting({
    required BuildContext context,
    required String title,
    String? subtitle,
    required IconData icon,
    Color? color,
    required VoidCallback onTap,
  }) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);
    final itemColor = color ?? colorScheme.primary;

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          color: colorScheme.surfaceContainerHighest.withValues(alpha: 0.3),
          borderRadius: BorderRadius.circular(Dimensions.radiusM),
          border: Border.all(color: colorScheme.outline.withValues(alpha: 0.2)),
        ),
        child: Row(
          children: [
            Icon(icon, size: 22, color: itemColor),
            Dimensions.horizontalSpacerM,
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600)),
                  if (subtitle != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.6)),
                    ),
                  ],
                ],
              ),
            ),
            Icon(Icons.chevron_right_rounded, color: colorScheme.onSurface.withValues(alpha: 0.4), size: 20),
          ],
        ),
      ),
    );
  }

  /// 构建区域标题
  ///
  /// 用于分区的小标题显示
  Widget _buildSectionTitle(BuildContext context, String title, IconData icon) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Row(
      children: [
        Icon(icon, size: 18, color: colorScheme.primary),
        Dimensions.horizontalSpacerS,
        Text(
          title,
          style: textTheme.titleSmall?.copyWith(
            fontWeight: FontWeight.w600,
            color: colorScheme.onSurface.withValues(alpha: 0.8),
          ),
        ),
      ],
    );
  }
}
