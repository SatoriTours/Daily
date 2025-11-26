import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:daily_satori/app/services/web_service/web_service.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:package_info_plus/package_info_plus.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:daily_satori/app/controllers/base_controller.dart';

class SettingsController extends BaseGetXController {
  // ========================================================================
  // 构造函数
  // ========================================================================

  SettingsController(super._appStateService);

  // ========================================================================
  // 属性
  // ========================================================================

  final webServiceAddress = ''.obs;
  final webAccessUrl = ''.obs;
  final isPageLoading = true.obs;
  final appVersion = ''.obs;

  // WebSocket连接状态
  final isWebSocketConnected = false.obs;

  @override
  void onInit() {
    super.onInit();
    _updateWebServerAddress();
    _updateWebAccessUrl();
    _loadAppVersion();
    _updateWebSocketStatus();
  }

  @override
  void onReady() {
    super.onReady();
    // 监听WebSocket连接状态变化
    ever(WebService.i.webSocketTunnel.isConnected, (connected) {
      isWebSocketConnected.value = connected;
    });
  }

  Future<void> _loadAppVersion() async {
    isPageLoading.value = true;
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      appVersion.value = '${packageInfo.version}+${packageInfo.buildNumber}';
    } catch (e) {
      logger.e('加载应用版本信息失败: $e');
      appVersion.value = '未知版本';
    } finally {
      isPageLoading.value = false;
    }
  }

  void _updateWebServerAddress() async {
    webServiceAddress.value = await WebService.i.getAppAddress();
  }

  void _updateWebAccessUrl() {
    webAccessUrl.value = WebService.i.webSocketTunnel.getWebAccessUrl();
  }

  void _updateWebSocketStatus() {
    isWebSocketConnected.value = WebService.i.webSocketTunnel.isConnected.value;
  }

  /// 选择备份目录
  void selectBackupDirectory() async {
    // 检查是否有权限
    final manageExternalStoragePermission = await Permission.manageExternalStorage.request();
    if (!manageExternalStoragePermission.isGranted) {
      UIUtils.showError('请授予应用管理外部存储的权限');
      return;
    }

    final String? selectedDirectory = await FilePicker.platform.getDirectoryPath(
      dialogTitle: '选择备份文件夹',
      initialDirectory: SettingRepository.i.getSetting(SettingService.backupDirKey),
    );

    if (selectedDirectory != null) {
      final backupDir = selectedDirectory;
      logger.i('[SettingsController] 选择备份目录: $backupDir');
      SettingRepository.i.saveSetting(SettingService.backupDirKey, backupDir);
    }
  }

  /// 复制Web服务地址到剪贴板
  void copyWebServiceAddress() {
    logger.i('[SettingsController] 复制Web服务地址: ${webServiceAddress.value}');
    Clipboard.setData(ClipboardData(text: webServiceAddress.value));
  }

  /// 复制Web访问URL到剪贴板
  void copyWebAccessUrl() {
    logger.i('[SettingsController] 复制Web访问URL: ${webAccessUrl.value}');
    Clipboard.setData(ClipboardData(text: webAccessUrl.value));
  }

  /// 重新用AI分析所有网页
  void reAnalyzeAllWebpages() {
    logger.i('[SettingsController] 重新分析所有网页');
    // 获取所有文章
    ArticleRepository.i.updateEmptyStatusToPending();
    // ArticleRepository.i.updateAllStatusToCompleted();
  }

  // ==================== Web服务器设置 ====================

  /// 获取Web服务器密码
  String getWebServerPassword() {
    return SettingRepository.i.getSetting(SettingService.webServerPasswordKey);
  }

  /// 保存Web服务器密码
  Future<void> saveWebServerPassword(String password) async {
    try {
      SettingRepository.i.saveSetting(SettingService.webServerPasswordKey, password);
      UIUtils.showSuccess('密码设置成功');
    } catch (e) {
      logger.e('保存密码失败: $e');
      UIUtils.showError('保存密码失败: $e');
    }
  }

  /// 重启Web服务
  Future<void> restartWebService() async {
    try {
      DialogUtils.showLoading(tips: '正在重启Web服务...');

      // 关闭现有WebSocket连接
      await WebService.i.webSocketTunnel.disconnect();

      // 重新初始化WebService
      await WebService.i.init();

      // 刷新地址
      _updateWebServerAddress();
      _updateWebAccessUrl();

      DialogUtils.hideLoading();
      UIUtils.showSuccess('Web服务已重启');
    } catch (e) {
      logger.e('重启Web服务失败: $e');
      DialogUtils.hideLoading();
      UIUtils.showError('重启Web服务失败: $e');
    }
  }
}
