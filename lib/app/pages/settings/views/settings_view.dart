import 'package:daily_satori/app/components/common/feature_icon.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
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
      body: Obx(
        () => controller.isPageLoading.value ? StyleGuide.getLoadingState(context) : _buildSettingsList(context),
      ),
    );
  }

  // ==================== AppBar ====================

  /// 构建顶部导航栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      title: Text('title.settings'.t),
      centerTitle: true,
      elevation: 0,
      backgroundColor: AppTheme.getColorScheme(context).surface,
      actions: [
        IconButton(
          icon: const Icon(Icons.info_outline),
          tooltip: 'dialog.about'.t,
          onPressed: () => _showAboutDialog(context),
        ),
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
      title: 'dialog.functions'.t,
      icon: Icons.flash_on_rounded,
      items: [
        _buildSettingItem(
          context: context,
          title: 'title.ai_config'.t,
          subtitle: 'setting.ai_config_subtitle'.t,
          icon: Icons.smart_toy_rounded,
          color: Colors.blue,
          onTap: () => Get.toNamed('/ai-config'),
        ),
        _buildSettingItem(
          context: context,
          title: 'title.plugin_center'.t,
          subtitle: 'setting.plugin_center_subtitle'.t,
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
      title: 'dialog.system'.t,
      icon: Icons.settings_rounded,
      items: [
        _buildSettingItem(
          context: context,
          title: 'title.backup_restore'.t,
          subtitle: 'setting.backup_restore_subtitle'.t,
          icon: Icons.backup_rounded,
          color: Colors.green,
          onTap: () => Get.toNamed(Routes.backupSettings),
        ),
        _buildSettingItem(
          context: context,
          title: 'setting.web_server'.t,
          subtitle: 'setting.web_server_subtitle'.t,
          icon: Icons.language_rounded,
          color: Colors.teal,
          onTap: () => _showWebServerDialog(context),
        ),
        _buildSettingItem(
          context: context,
          title: 'dialog.check_update'.t,
          subtitle: 'setting.check_update_subtitle'.t,
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
      padding: const EdgeInsets.only(bottom: Dimensions.spacingM),
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
      padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
      child: Row(
        children: [
          Icon(icon, size: Dimensions.iconSizeXs, color: colorScheme.primary),
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
  Widget _buildSettingItem({
    required BuildContext context,
    required String title,
    required String subtitle,
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: Dimensions.paddingM,
        child: Row(
          children: [
            FeatureIcon(
              icon: icon,
              iconColor: color,
              containerSize: Dimensions.iconSizeXl,
              iconSize: Dimensions.iconSizeXs,
            ),
            Dimensions.horizontalSpacerM,
            Expanded(child: _buildSettingItemText(context, title, subtitle)),
            _buildSettingItemTrailingIcon(context),
          ],
        ),
      ),
    );
  }

  /// 构建设置项文本内容
  Widget _buildSettingItemText(BuildContext context, String title, String subtitle) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: textTheme.titleSmall),
        Text(
          subtitle,
          style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: Opacities.mediumHigh)),
        ),
      ],
    );
  }

  /// 构建设置项尾部图标
  Widget _buildSettingItemTrailingIcon(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    return Icon(
      Icons.chevron_right,
      color: colorScheme.onSurface.withValues(alpha: Opacities.medium),
      size: Dimensions.iconSizeS,
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
              '${'setting.version'.t} ${controller.appVersion.value}',
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
        width: Dimensions.iconSizeXxl - Dimensions.spacingS,
        height: Dimensions.iconSizeXxl - Dimensions.spacingS,
        decoration: BoxDecoration(
          color: colorScheme.primary,
          borderRadius: BorderRadius.circular(Dimensions.radiusS + 2),
        ),
        child: Icon(Icons.article, color: colorScheme.onPrimary),
      ),
      children: [
        Dimensions.verticalSpacerM,
        Text('dialog.about_description'.t, style: textTheme.bodyMedium),
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
                  padding: const EdgeInsets.fromLTRB(Dimensions.spacingL, 0, Dimensions.spacingL, Dimensions.spacingL),
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
      padding: const EdgeInsets.fromLTRB(
        Dimensions.spacingL,
        Dimensions.spacingM,
        Dimensions.spacingL,
        Dimensions.spacingM,
      ),
      child: Row(
        children: [
          FeatureIcon(
            icon: Icons.language_rounded,
            iconColor: primaryColor,
            containerSize: Dimensions.iconSizeXxl - Dimensions.spacingS,
            iconSize: Dimensions.iconSizeM + 2,
          ),
          Dimensions.horizontalSpacerM,
          Text('setting.web_server'.t, style: textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600)),
          const Spacer(),
          IconButton(
            icon: Icon(
              Icons.close_rounded,
              size: Dimensions.iconSizeM + 2,
              color: colorScheme.onSurface.withValues(alpha: Opacities.medium),
            ),
            onPressed: () => Navigator.pop(context),
            tooltip: 'button.close'.t,
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
        _buildSectionTitle(context, 'setting.server_info'.t, Icons.info_outline_rounded),
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
      title: 'setting.http_server_address',
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
          UIUtils.showSuccess('message.copy_success');
        },
        tooltip: 'button.copy_address'.t,
      ),
    );
  }

  /// 构建WebSocket地址卡片
  Widget _buildWebSocketAddressCard(BuildContext context, Color primaryColor, TextTheme textTheme) {
    return _buildInfoCard(
      context: context,
      title: 'setting.websocket_access',
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
          UIUtils.showSuccess('message.copy_success');
        },
        tooltip: 'button.copy_address'.t,
      ),
    );
  }

  /// 构建连接状态卡片
  Widget _buildConnectionStatusCard(BuildContext context, TextTheme textTheme) {
    return _buildInfoCard(
      context: context,
      title: 'setting.connection_status',
      content: Obx(() => _buildConnectionStatusIndicator(textTheme)),
      icon: Icons.network_check_rounded,
      iconColor: Colors.green,
    );
  }

  /// 构建连接状态指示器
  Widget _buildConnectionStatusIndicator(TextTheme textTheme) {
    final isConnected = controller.isWebSocketConnected.value;
    final statusColor = isConnected ? Colors.green : Colors.red;
    final statusText = isConnected ? 'status.connected'.t : 'status.disconnected'.t;

    return Row(
      children: [
        _buildStatusDot(statusColor),
        Dimensions.horizontalSpacerS,
        Text(
          statusText,
          style: textTheme.bodyMedium?.copyWith(color: statusColor, fontWeight: FontWeight.w600),
        ),
      ],
    );
  }

  /// 构建状态指示点
  Widget _buildStatusDot(Color color) {
    return Container(
      width: Dimensions.spacingS + 2,
      height: Dimensions.spacingS + 2,
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
        boxShadow: [BoxShadow(color: color.withValues(alpha: 0.3), blurRadius: 4, spreadRadius: 1)],
      ),
    );
  }

  /// 构建服务器管理分区
  Widget _buildServerManagementSection(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSectionTitle(context, 'setting.server_management'.t, Icons.settings_rounded),
        Dimensions.verticalSpacerM,
        // 密码设置
        _buildWebServerSetting(
          context: context,
          title: 'setting.server_password'.t,
          subtitle: 'setting.server_password_subtitle'.t,
          icon: Icons.password_rounded,
          color: Colors.orange,
          onTap: () => _showPasswordSettingDialog(context),
        ),
        Dimensions.verticalSpacerS,
        // 重启服务
        _buildWebServerSetting(
          context: context,
          title: 'setting.restart_service'.t,
          subtitle: 'setting.restart_service_subtitle'.t,
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
    const tipText = 'setting.network_tip';

    return Container(
      padding: Dimensions.paddingM,
      decoration: BoxDecoration(
        color: primaryColor.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        border: Border.all(color: primaryColor.withValues(alpha: 0.2), width: 1),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.lightbulb_outline_rounded, size: Dimensions.iconSizeS, color: primaryColor),
          Dimensions.horizontalSpacerS,
          Expanded(
            child: Text(
              tipText.t,
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
        contentPadding: const EdgeInsets.fromLTRB(Dimensions.spacingL, Dimensions.spacingM + 4, Dimensions.spacingL, 0),
        actionsPadding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingM),
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
        FeatureIcon(
          icon: Icons.password_rounded,
          iconColor: Colors.orange,
          containerSize: Dimensions.iconSizeXl + Dimensions.spacingXs,
          iconSize: Dimensions.iconSizeM,
        ),
        Dimensions.horizontalSpacerM,
        Text('setting.server_password'.t, style: textTheme?.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
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
      padding: Dimensions.paddingM,
      decoration: _buildTipCardDecoration(colorScheme),
      child: Row(
        children: [
          Icon(Icons.info_outline_rounded, size: Dimensions.iconSizeXs, color: colorScheme.primary),
          Dimensions.horizontalSpacerS,
          Expanded(
            child: Text(
              'setting.password_tip'.t,
              style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.7), height: 1.3),
            ),
          ),
        ],
      ),
    );
  }

  /// 构建提示卡片装饰
  BoxDecoration _buildTipCardDecoration(ColorScheme colorScheme) {
    return BoxDecoration(
      color: colorScheme.primary.withValues(alpha: 0.08),
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      border: Border.all(color: colorScheme.primary.withValues(alpha: 0.2), width: 1),
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
        decoration: _buildPasswordInputDecoration(isPasswordVisible, colorScheme),
      ),
    );
  }

  /// 构建密码输入框装饰
  InputDecoration _buildPasswordInputDecoration(RxBool isPasswordVisible, ColorScheme colorScheme) {
    return InputDecoration(
      labelText: 'label.password',
      hintText: 'hint.enter_server_password',
      prefixIcon: const Icon(Icons.lock_outline_rounded),
      suffixIcon: _buildPasswordVisibilityToggle(isPasswordVisible),
      border: _buildInputBorder(colorScheme.outline, 1),
      focusedBorder: _buildInputBorder(colorScheme.primary, 2),
      enabledBorder: _buildInputBorder(colorScheme.outline, 1),
      contentPadding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingM - 2),
    );
  }

  /// 构建密码可见性切换按钮
  Widget _buildPasswordVisibilityToggle(RxBool isPasswordVisible) {
    return IconButton(
      icon: Icon(
        isPasswordVisible.value ? Icons.visibility_off_rounded : Icons.visibility_rounded,
        size: Dimensions.iconSizeM,
      ),
      onPressed: () => isPasswordVisible.value = !isPasswordVisible.value,
      tooltip: isPasswordVisible.value ? 'button.hide_password'.t : 'button.show_password'.t,
    );
  }

  /// 构建输入框边框
  OutlineInputBorder _buildInputBorder(Color color, double width) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      borderSide: BorderSide(color: color, width: width),
    );
  }

  /// 构建密码对话框操作按钮
  List<Widget> _buildPasswordDialogActions(BuildContext context, TextEditingController passwordController) {
    return [
      TextButton(
        onPressed: () => Navigator.pop(context),
        style: ButtonStyles.getTextStyle(context),
        child: Text('button.cancel'.t),
      ),
      ElevatedButton(
        onPressed: () {
          Navigator.pop(context);
          controller.saveWebServerPassword(passwordController.text);
        },
        style: ButtonStyles.getPrimaryStyle(context),
        child: Text('button.save'.t),
      ),
    ];
  }

  // ==================== 通用组件 ====================

  /// 构建信息卡片
  /// 用于显示服务器地址、连接状态等信息
  Widget _buildInfoCard({
    required BuildContext context,
    required String title,
    required Widget content,
    required IconData icon,
    Color? color,
    Color? iconColor,
    Widget? action,
  }) {
    final colorScheme = AppTheme.getColorScheme(context);
    final cardIconColor = iconColor ?? color ?? colorScheme.primary;

    return Container(
      padding: Dimensions.paddingM,
      decoration: _buildInfoCardDecoration(colorScheme),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildInfoCardHeader(context, title, icon, cardIconColor, action),
          Dimensions.verticalSpacerS,
          content,
        ],
      ),
    );
  }

  /// 构建信息卡片装饰
  BoxDecoration _buildInfoCardDecoration(ColorScheme colorScheme) {
    return BoxDecoration(
      color: colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      border: Border.all(color: colorScheme.outline.withValues(alpha: 0.2)),
    );
  }

  /// 构建信息卡片头部
  Widget _buildInfoCardHeader(BuildContext context, String title, IconData icon, Color iconColor, Widget? action) {
    final textTheme = AppTheme.getTextTheme(context);

    return Row(
      children: [
        Icon(icon, size: Dimensions.iconSizeM, color: iconColor),
        Dimensions.horizontalSpacerS,
        Text(title, style: textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w600)),
        const Spacer(),
        if (action != null) action,
      ],
    );
  }

  /// 构建Web服务器设置项
  /// 用于服务器管理分区的设置按钮
  Widget _buildWebServerSetting({
    required BuildContext context,
    required String title,
    String? subtitle,
    required IconData icon,
    Color? color,
    required VoidCallback onTap,
  }) {
    final colorScheme = AppTheme.getColorScheme(context);
    final itemColor = color ?? colorScheme.primary;
    final decoration = _buildServerSettingDecoration(colorScheme);

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingM - 2),
        decoration: decoration,
        child: Row(
          children: [
            Icon(icon, size: Dimensions.iconSizeM + 2, color: itemColor),
            Dimensions.horizontalSpacerM,
            Expanded(child: _buildServerSettingText(context, title, subtitle)),
            _buildTrailingIcon(colorScheme),
          ],
        ),
      ),
    );
  }

  /// 构建服务器设置项装饰
  BoxDecoration _buildServerSettingDecoration(ColorScheme colorScheme) {
    return BoxDecoration(
      color: colorScheme.surfaceContainerHighest.withValues(alpha: 0.3),
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      border: Border.all(color: colorScheme.outline.withValues(alpha: 0.2)),
    );
  }

  /// 构建服务器设置项文本
  Widget _buildServerSettingText(BuildContext context, String title, String? subtitle) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600)),
        if (subtitle != null) ...[
          Dimensions.verticalSpacerXs,
          Text(
            subtitle,
            style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: Opacities.medium)),
          ),
        ],
      ],
    );
  }

  /// 构建尾部箭头图标
  Widget _buildTrailingIcon(ColorScheme colorScheme) {
    return Icon(
      Icons.chevron_right_rounded,
      color: colorScheme.onSurface.withValues(alpha: Opacities.low),
      size: Dimensions.iconSizeM,
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
        Icon(icon, size: Dimensions.iconSizeS, color: colorScheme.primary),
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
