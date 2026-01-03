import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:daily_satori/app/components/common/feature_icon.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:daily_satori/app/utils/dialog_utils.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/pages/settings/providers/settings_controller_provider.dart';
import 'package:daily_satori/app/pages/settings/widgets/first_setup_guide.dart';
import 'package:daily_satori/app/providers/first_launch_provider.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/routes/app_routes.dart';
import 'package:daily_satori/app/data/setting/setting_repository.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';

/// 设置页面视图
///
/// 提供应用的主要设置功能,包括：
/// - 功能设置：AI配置、插件管理
/// - 系统管理：备份恢复、Web服务器、版本更新
class SettingsView extends ConsumerStatefulWidget {
  const SettingsView({super.key});

  @override
  ConsumerState<SettingsView> createState() => _SettingsViewState();
}

class _SettingsViewState extends ConsumerState<SettingsView> {
  @override
  void initState() {
    super.initState();
    // 延迟刷新状态，确保页面完全加载后检查
    Future.microtask(() {
      ref.invalidate(firstLaunchControllerProvider);
    });
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(settingsControllerProvider);
    final firstLaunchState = ref.watch(firstLaunchControllerProvider);

    return PopScope(
      // 禁止返回键（在首次配置期间）
      canPop: firstLaunchState.isSetupComplete,
      child: Scaffold(
        backgroundColor: AppTheme.getColorScheme(context).surface,
        appBar: _buildAppBar(context, ref),
        body: state.isPageLoading
            ? StyleGuide.getLoadingState(context)
            : SingleChildScrollView(
                physics: const BouncingScrollPhysics(),
                child: Column(
                  children: [
                    // 首次设置引导
                    const FirstSetupGuide(),
                    // 设置列表（首次设置未完成时隐藏）
                    if (firstLaunchState.isSetupComplete) _buildSettingsList(context, ref),
                  ],
                ),
              ),
      ),
    );
  }

  // ==================== AppBar ====================

  /// 构建顶部导航栏
  PreferredSizeWidget _buildAppBar(BuildContext context, WidgetRef ref) {
    return SAppBar(
      title: Text('title.settings'.t, style: TextStyle(color: AppColors.getOnPrimary(context))),
      centerTitle: true,
      elevation: 0,
      backgroundColorLight: AppColors.primary,
      backgroundColorDark: AppColors.backgroundDark,
      foregroundColor: AppColors.getOnPrimary(context),
      actions: [
        IconButton(
          icon: Icon(Icons.info_outline, color: AppColors.getOnPrimary(context)),
          tooltip: 'dialog.about'.t,
          onPressed: () => _showAboutDialog(context, ref),
        ),
      ],
    );
  }

  // ==================== 主布局 ====================

  /// 构建设置列表主体
  Widget _buildSettingsList(BuildContext context, WidgetRef ref) {
    final state = ref.watch(settingsControllerProvider);
    return Column(
      children: [
        _buildFunctionSection(context, ref),
        _buildSystemSection(context, ref, state),
        Dimensions.verticalSpacerL,
        _buildVersionInfo(context, state),
        Dimensions.verticalSpacerM,
      ],
    );
  }

  // ==================== 功能设置分区 ====================

  /// 构建功能设置分区
  /// 包含AI配置、插件中心等功能入口
  Widget _buildFunctionSection(BuildContext context, WidgetRef ref) {
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
          color: AppColors.getPrimary(context),
          onTap: () => AppNavigation.toNamed('/ai-config'),
        ),
        _buildSettingItem(
          context: context,
          title: 'title.plugin_center'.t,
          subtitle: 'setting.plugin_center_subtitle'.t,
          icon: Icons.extension_rounded,
          color: AppColors.getSecondary(context),
          onTap: () => AppNavigation.toNamed(Routes.pluginCenter),
        ),
        _buildSettingItem(
          context: context,
          title: 'setting.google_books_api_key'.t,
          subtitle: 'setting.google_books_api_key_subtitle'.t,
          icon: Icons.menu_book_rounded,
          color: AppColors.getInfo(context),
          onTap: () => _showGoogleBooksApiKeyDialog(context, ref),
        ),
      ],
    );
  }

  // ==================== 系统设置分区 ====================

  /// 构建系统设置分区
  /// 包含备份恢复、Web服务器、版本更新等系统功能
  Widget _buildSystemSection(BuildContext context, WidgetRef ref, SettingsControllerState state) {
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
          color: AppColors.getSuccess(context),
          onTap: () => AppNavigation.toNamed(Routes.backupSettings),
        ),
        _buildDownloadImagesItem(context, ref, state),
        _buildSettingItem(
          context: context,
          title: 'setting.web_server'.t,
          subtitle: 'setting.web_server_subtitle'.t,
          icon: Icons.language_rounded,
          color: AppColors.getSuccess(context),
          onTap: () => _showWebServerDialog(context, ref),
        ),
        _buildSettingItem(
          context: context,
          title: 'dialog.check_update'.t,
          subtitle: 'setting.check_update_subtitle'.t,
          icon: Icons.system_update_rounded,
          color: AppColors.getPrimary(context).withValues(alpha: 0.7),
          onTap: () => AppUpgradeService.i.checkAndDownload(),
        ),
      ],
    );
  }

  /// 构建下载文章图片设置项
  Widget _buildDownloadImagesItem(BuildContext context, WidgetRef ref, SettingsControllerState state) {
    final isDownloading = state.isDownloadingImages;
    final progress = state.downloadProgress;
    final total = state.downloadTotal;

    return _buildSettingItemWithProgress(
      context: context,
      title: 'setting.download_images'.t,
      subtitle: isDownloading
          ? 'setting.download_images_progress'.t.replaceAll('{current}', '$progress').replaceAll('{total}', '$total')
          : 'setting.download_images_subtitle'.t,
      icon: Icons.image_rounded,
      color: AppColors.getWarning(context),
      isLoading: isDownloading,
      onTap: isDownloading ? null : () => ref.read(settingsControllerProvider.notifier).downloadMissingArticleImages(),
    );
  }

  /// 构建带进度的设置项
  Widget _buildSettingItemWithProgress({
    required BuildContext context,
    required String title,
    required String subtitle,
    required IconData icon,
    required Color color,
    required bool isLoading,
    VoidCallback? onTap,
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
            isLoading
                ? SizedBox(
                    width: Dimensions.iconSizeS,
                    height: Dimensions.iconSizeS,
                    child: CircularProgressIndicator(strokeWidth: 2, color: AppTheme.getColorScheme(context).primary),
                  )
                : _buildSettingItemTrailingIcon(context),
          ],
        ),
      ),
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
  Widget _buildVersionInfo(BuildContext context, SettingsControllerState state) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Center(
      child: Column(
        children: [
          Text(
            '${'setting.version'.t} ${state.appVersion}',
            style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.6)),
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
  void _showAboutDialog(BuildContext context, WidgetRef ref) {
    final state = ref.read(settingsControllerProvider);
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    showAboutDialog(
      context: context,
      applicationName: 'Daily Satori',
      applicationVersion: 'v${state.appVersion}',
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
  void _showWebServerDialog(BuildContext context, WidgetRef ref) {
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
                      Builder(
                        builder: (context) {
                          final state = ref.watch(settingsControllerProvider);
                          return _buildServerInfoSection(context, ref, primaryColor, textTheme, state);
                        },
                      ),
                      Dimensions.verticalSpacerL,
                      // 服务器管理
                      _buildServerManagementSection(context, ref),
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
            onPressed: () => AppNavigation.back(),
            tooltip: 'button.close'.t,
          ),
        ],
      ),
    );
  }

  /// 构建服务器信息分区
  Widget _buildServerInfoSection(
    BuildContext context,
    WidgetRef ref,
    Color primaryColor,
    TextTheme textTheme,
    SettingsControllerState state,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSectionTitle(context, 'setting.server_info'.t, Icons.info_outline_rounded),
        Dimensions.verticalSpacerM,
        // HTTP服务器地址
        _buildHttpAddressCard(context, ref, primaryColor, textTheme, state),
        Dimensions.verticalSpacerM,
        // WebSocket地址
        _buildWebSocketAddressCard(context, ref, primaryColor, textTheme, state),
        Dimensions.verticalSpacerM,
        // 连接状态
        _buildConnectionStatusCard(context, ref, textTheme, state),
      ],
    );
  }

  /// 构建HTTP服务器地址卡片
  Widget _buildHttpAddressCard(
    BuildContext context,
    WidgetRef ref,
    Color primaryColor,
    TextTheme textTheme,
    SettingsControllerState state,
  ) {
    return _buildInfoCard(
      context: context,
      title: 'setting.http_server_address'.t,
      content: SelectableText(
        state.webServiceAddress,
        style: textTheme.bodyMedium?.copyWith(color: primaryColor, fontWeight: FontWeight.w600),
      ),
      icon: Icons.language_rounded,
      iconColor: AppColors.getPrimary(context),
      action: IconButton(
        icon: Icon(Icons.copy_rounded, size: 20, color: primaryColor),
        onPressed: () {
          ref.read(settingsControllerProvider.notifier).copyWebServiceAddress();
          UIUtils.showSuccess('message.copy_success'.t);
        },
        tooltip: 'button.copy_address'.t,
      ),
    );
  }

  /// 构建WebSocket地址卡片
  Widget _buildWebSocketAddressCard(
    BuildContext context,
    WidgetRef ref,
    Color primaryColor,
    TextTheme textTheme,
    SettingsControllerState state,
  ) {
    return _buildInfoCard(
      context: context,
      title: 'setting.websocket_access'.t,
      content: SelectableText(
        state.webAccessUrl,
        style: textTheme.bodyMedium?.copyWith(color: primaryColor, fontWeight: FontWeight.w600),
      ),
      icon: Icons.wifi_rounded,
      iconColor: AppColors.getSecondary(context),
      action: IconButton(
        icon: Icon(Icons.copy_rounded, size: 20, color: primaryColor),
        onPressed: () {
          ref.read(settingsControllerProvider.notifier).copyWebAccessUrl();
          UIUtils.showSuccess('message.copy_success'.t);
        },
        tooltip: 'button.copy_address'.t,
      ),
    );
  }

  /// 构建连接状态卡片
  Widget _buildConnectionStatusCard(
    BuildContext context,
    WidgetRef ref,
    TextTheme textTheme,
    SettingsControllerState state,
  ) {
    return _buildInfoCard(
      context: context,
      title: 'setting.connection_status'.t,
      content: _buildConnectionStatusIndicator(context, ref, textTheme, state),
      icon: Icons.network_check_rounded,
      iconColor: AppColors.getSuccess(context),
    );
  }

  /// 构建连接状态指示器
  Widget _buildConnectionStatusIndicator(
    BuildContext context,
    WidgetRef ref,
    TextTheme textTheme,
    SettingsControllerState state,
  ) {
    final isConnected = state.isWebSocketConnected;
    final statusColor = isConnected ? AppColors.getSuccess(context) : AppColors.getError(context);
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
  Widget _buildServerManagementSection(BuildContext context, WidgetRef ref) {
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
          color: AppColors.getWarning(context),
          onTap: () => _showPasswordSettingDialog(context, ref),
        ),
        Dimensions.verticalSpacerS,
        // 重启服务
        _buildWebServerSetting(
          context: context,
          title: 'setting.restart_service'.t,
          subtitle: 'setting.restart_service_subtitle'.t,
          icon: Icons.refresh_rounded,
          color: AppColors.getSuccess(context),
          onTap: () => ref.read(settingsControllerProvider.notifier).restartWebService(),
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
  void _showPasswordSettingDialog(BuildContext context, WidgetRef ref) {
    final passwordController = TextEditingController(text: ref.read(webServerPasswordProvider));
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusL)),
        contentPadding: const EdgeInsets.fromLTRB(Dimensions.spacingL, Dimensions.spacingM + 4, Dimensions.spacingL, 0),
        actionsPadding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingM),
        actionsAlignment: MainAxisAlignment.end,
        title: _buildPasswordDialogTitle(dialogContext, textTheme),
        content: _PasswordDialogContent(
          passwordController: passwordController,
          colorScheme: colorScheme,
          textTheme: textTheme,
        ),
        actions: _buildPasswordDialogActions(dialogContext, ref, passwordController),
      ),
    );
  }

  /// 构建密码对话框标题
  Widget _buildPasswordDialogTitle(BuildContext context, TextTheme textTheme) {
    return Row(
      children: [
        FeatureIcon(
          icon: Icons.password_rounded,
          iconColor: AppColors.getWarning(context),
          containerSize: Dimensions.iconSizeXl + Dimensions.spacingXs,
          iconSize: Dimensions.iconSizeM,
        ),
        Dimensions.horizontalSpacerM,
        Text('setting.server_password'.t, style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
      ],
    );
  }

  /// 构建密码对话框操作按钮
  List<Widget> _buildPasswordDialogActions(
    BuildContext context,
    WidgetRef ref,
    TextEditingController passwordController,
  ) {
    return [
      SizedBox(
        width: double.infinity,
        child: Row(
          children: [
            // 取消按钮占 30%
            Expanded(
              flex: 3,
              child: TextButton(
                onPressed: () => AppNavigation.back(),
                style: ButtonStyles.getOutlinedStyle(context),
                child: Text('button.cancel'.t),
              ),
            ),
            Dimensions.horizontalSpacerS,
            // 保存按钮占 70%
            Expanded(
              flex: 7,
              child: ElevatedButton(
                onPressed: () {
                  AppNavigation.back();
                  ref.read(settingsControllerProvider.notifier).saveWebServerPassword(passwordController.text);
                },
                style: ButtonStyles.getPrimaryStyle(context),
                child: Text('button.save'.t),
              ),
            ),
          ],
        ),
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

  /// 显示 Google Books API Key 配置对话框
  void _showGoogleBooksApiKeyDialog(BuildContext context, WidgetRef ref) {
    DialogUtils.showInputDialog(
      title: 'dialog.google_books_api_key'.t,
      initialValue: SettingRepository.i.getSetting(SettingService.googleCloudApiKeyKey),
      confirmText: 'dialog.save'.t,
      cancelText: 'dialog.cancel'.t,
      onConfirm: (value) {
        final apiKey = value.trim();
        SettingRepository.i.saveSetting(SettingService.googleCloudApiKeyKey, apiKey);
        UIUtils.showSuccess('保存成功');
      },
    );
  }
}

/// 密码对话框内容组件
class _PasswordDialogContent extends StatefulWidget {
  final TextEditingController passwordController;
  final ColorScheme colorScheme;
  final TextTheme textTheme;

  const _PasswordDialogContent({required this.passwordController, required this.colorScheme, required this.textTheme});

  @override
  State<_PasswordDialogContent> createState() => _PasswordDialogContentState();
}

class _PasswordDialogContentState extends State<_PasswordDialogContent> {
  bool isPasswordVisible = false;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 说明文字
        _buildPasswordTipCard(context),
        Dimensions.verticalSpacerM,
        // 密码输入框
        _buildPasswordTextField(context),
      ],
    );
  }

  /// 构建密码提示卡片
  Widget _buildPasswordTipCard(BuildContext context) {
    return Container(
      padding: Dimensions.paddingM,
      decoration: _buildTipCardDecoration(),
      child: Row(
        children: [
          Icon(Icons.info_outline_rounded, size: Dimensions.iconSizeXs, color: widget.colorScheme.primary),
          Dimensions.horizontalSpacerS,
          Expanded(
            child: Text(
              'setting.password_tip'.t,
              style: widget.textTheme.bodySmall?.copyWith(
                color: widget.colorScheme.onSurface.withValues(alpha: 0.7),
                height: 1.3,
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// 构建提示卡片装饰
  BoxDecoration _buildTipCardDecoration() {
    return BoxDecoration(
      color: widget.colorScheme.primary.withValues(alpha: 0.08),
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      border: Border.all(color: widget.colorScheme.primary.withValues(alpha: 0.2), width: 1),
    );
  }

  /// 构建密码输入框
  Widget _buildPasswordTextField(BuildContext context) {
    return TextField(
      controller: widget.passwordController,
      obscureText: !isPasswordVisible,
      style: widget.textTheme.bodyMedium,
      decoration: _buildPasswordInputDecoration(),
    );
  }

  /// 构建密码输入框装饰
  InputDecoration _buildPasswordInputDecoration() {
    return InputDecoration(
      hintText: 'hint.enter_server_password'.t,
      prefixIcon: const Icon(Icons.lock_outline_rounded),
      suffixIcon: _buildPasswordVisibilityToggle(),
      border: _buildInputBorder(widget.colorScheme.outline, 1),
      focusedBorder: _buildInputBorder(widget.colorScheme.primary, 2),
      enabledBorder: _buildInputBorder(widget.colorScheme.outline, 1),
      contentPadding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingM - 2),
    );
  }

  /// 构建密码可见性切换按钮
  Widget _buildPasswordVisibilityToggle() {
    return IconButton(
      icon: Icon(
        isPasswordVisible ? Icons.visibility_off_rounded : Icons.visibility_rounded,
        size: Dimensions.iconSizeM,
      ),
      onPressed: () => setState(() => isPasswordVisible = !isPasswordVisible),
      tooltip: isPasswordVisible ? 'button.hide_password'.t : 'button.show_password'.t,
    );
  }

  /// 构建输入框边框
  OutlineInputBorder _buildInputBorder(Color color, double width) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      borderSide: BorderSide(color: color, width: width),
    );
  }
}
