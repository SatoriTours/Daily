import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/web_service/web_service.dart';
import 'package:daily_satori/global.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path_provider/path_provider.dart';
import 'dart:io';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:sqflite/sqflite.dart';

class SettingsController extends GetxController {
  final webServiceAddress = ''.obs;
  final webAccessUrl = ''.obs;
  final isLoading = true.obs;
  final appVersion = ''.obs;

  // WebSocket连接状态
  final isWebSocketConnected = false.obs;

  // 清理相关
  final isCleaningCache = false.obs;
  final isAnalyzingStorage = false.obs;
  final isOptimizingDatabase = false.obs;
  final storageInfo = <String, dynamic>{}.obs;

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
    isLoading.value = true;
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      appVersion.value = '${packageInfo.version}+${packageInfo.buildNumber}';
    } catch (e) {
      logger.e('加载应用版本信息失败: $e');
      appVersion.value = '未知版本';
    } finally {
      isLoading.value = false;
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
      initialDirectory: SettingRepository.getSetting(SettingService.backupDirKey),
    );

    if (selectedDirectory != null) {
      final backupDir = selectedDirectory;
      logger.i('选择备份目录: $backupDir');
      SettingRepository.saveSetting(SettingService.backupDirKey, backupDir);
    }
  }

  void copyWebServiceAddress() {
    Clipboard.setData(ClipboardData(text: webServiceAddress.value));
  }

  void copyWebAccessUrl() {
    Clipboard.setData(ClipboardData(text: webAccessUrl.value));
  }

  /// 重新用AI分析所有网页
  void reAnalyzeAllWebpages() {
    // 获取所有文章
    ArticleRepository.updateEmptyStatusToPending();
    // ArticleRepository.updateAllStatusToCompleted();
  }

  // ==================== 清理与维护功能 ====================

  /// 分析存储空间使用情况
  Future<void> analyzeStorage() async {
    isAnalyzingStorage.value = true;
    try {
      // 获取应用文档目录
      final appDir = await getApplicationDocumentsDirectory();
      final appDirSize = await _calculateDirSize(appDir);

      // 获取应用临时目录
      final tempDir = await getTemporaryDirectory();
      final tempDirSize = await _calculateDirSize(tempDir);

      // 获取缓存目录
      final cacheDir = await getApplicationCacheDirectory();
      final cacheDirSize = await _calculateDirSize(cacheDir);

      // 获取数据库大小
      final dbSize = await _getDatabaseSize();

      // 获取图片目录大小
      final imagesSize = await _calculateDirSize(Directory(FileService.i.imagesBasePath));

      // 更新存储信息
      storageInfo.value = {
        'appDirSize': _formatSize(appDirSize),
        'tempDirSize': _formatSize(tempDirSize),
        'cacheDirSize': _formatSize(cacheDirSize),
        'dbSize': _formatSize(dbSize),
        'imagesSize': _formatSize(imagesSize),
        'totalSize': _formatSize(appDirSize + tempDirSize + cacheDirSize + dbSize + imagesSize),
      };

      // 显示结果
      UIUtils.showDialog(
        title: '存储分析结果',
        message:
            '应用数据: ${storageInfo['appDirSize']}\n'
            '临时文件: ${storageInfo['tempDirSize']}\n'
            '缓存数据: ${storageInfo['cacheDirSize']}\n'
            '数据库: ${storageInfo['dbSize']}\n'
            '图片文件: ${storageInfo['imagesSize']}\n\n'
            '总计: ${storageInfo['totalSize']}',
      );
    } catch (e) {
      logger.e('存储分析失败: $e');
      UIUtils.showError('存储分析失败: $e');
    } finally {
      isAnalyzingStorage.value = false;
    }
  }

  /// 计算目录大小
  Future<int> _calculateDirSize(Directory dir) async {
    int totalSize = 0;
    try {
      if (await dir.exists()) {
        await for (var entity in dir.list(recursive: true, followLinks: false)) {
          if (entity is File) {
            totalSize += await entity.length();
          }
        }
      }
    } catch (e) {
      logger.e('计算目录大小失败: $e');
    }
    return totalSize;
  }

  /// 获取数据库大小
  Future<int> _getDatabaseSize() async {
    try {
      final dbPath = await getDatabasesPath();
      final dbDir = Directory(dbPath);
      return await _calculateDirSize(dbDir);
    } catch (e) {
      logger.e('获取数据库大小失败: $e');
      return 0;
    }
  }

  /// 格式化文件大小
  String _formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(2)} KB';
    if (bytes < 1024 * 1024 * 1024) return '${(bytes / (1024 * 1024)).toStringAsFixed(2)} MB';
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

  /// 清除缓存
  Future<void> clearCache() async {
    isCleaningCache.value = true;
    try {
      // 清除临时目录
      final tempDir = await getTemporaryDirectory();
      await _clearDirectory(tempDir);

      // 清除缓存目录
      final cacheDir = await getApplicationCacheDirectory();
      await _clearDirectory(cacheDir);

      UIUtils.showSuccess('缓存清理完成');
    } catch (e) {
      logger.e('清理缓存失败: $e');
      UIUtils.showError('清理缓存失败: $e');
    } finally {
      isCleaningCache.value = false;
    }
  }

  /// 清空目录内容
  Future<void> _clearDirectory(Directory dir) async {
    try {
      if (await dir.exists()) {
        await for (var entity in dir.list()) {
          if (entity is Directory) {
            await entity.delete(recursive: true);
          } else if (entity is File) {
            await entity.delete();
          }
        }
      }
    } catch (e) {
      logger.e('清空目录失败: ${dir.path} - $e');
    }
  }

  /// 数据库优化
  Future<void> optimizeDatabase() async {
    isOptimizingDatabase.value = true;
    try {
      // 获取数据库路径
      final dbPath = await getDatabasesPath();
      final dbFile = '$dbPath/daily_satori.db';

      // 使用VACUUM优化数据库
      final db = await openDatabase(dbFile);
      await db.execute('VACUUM');
      await db.close();

      UIUtils.showSuccess('数据库优化完成');
    } catch (e) {
      logger.e('数据库优化失败: $e');
      UIUtils.showError('数据库优化失败: $e');
    } finally {
      isOptimizingDatabase.value = false;
    }
  }

  /// 恢复出厂设置
  Future<void> factoryReset() async {
    try {
      // 重置设置
      await SettingRepository.resetAllSettings();

      // 重新初始化WebService
      await WebService.i.init();

      // 刷新Web服务地址
      _updateWebServerAddress();
      _updateWebAccessUrl();

      UIUtils.showSuccess('设置已重置');
      Get.offAllNamed('/home'); // 返回首页
    } catch (e) {
      logger.e('恢复出厂设置失败: $e');
      UIUtils.showError('恢复出厂设置失败: $e');
    }
  }

  // ==================== Web服务器设置 ====================

  /// 获取Web服务器密码
  String getWebServerPassword() {
    return SettingRepository.getSetting(SettingService.webServerPasswordKey);
  }

  /// 保存Web服务器密码
  Future<void> saveWebServerPassword(String password) async {
    try {
      await SettingRepository.saveSetting(SettingService.webServerPasswordKey, password);
      UIUtils.showSuccess('密码设置成功');
    } catch (e) {
      logger.e('保存密码失败: $e');
      UIUtils.showError('保存密码失败: $e');
    }
  }

  /// 重启Web服务
  Future<void> restartWebService() async {
    try {
      UIUtils.showLoading(tips: '正在重启Web服务...');

      // 关闭现有WebSocket连接
      await WebService.i.webSocketTunnel.disconnect();

      // 重新初始化WebService
      await WebService.i.init();

      // 刷新地址
      _updateWebServerAddress();
      _updateWebAccessUrl();

      UIUtils.hideLoading();
      UIUtils.showSuccess('Web服务已重启');
    } catch (e) {
      logger.e('重启Web服务失败: $e');
      UIUtils.hideLoading();
      UIUtils.showError('重启Web服务失败: $e');
    }
  }
}
