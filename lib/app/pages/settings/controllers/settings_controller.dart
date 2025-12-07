import 'dart:io';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:daily_satori/app/services/web_service/web_service.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:package_info_plus/package_info_plus.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:permission_handler/permission_handler.dart';

class SettingsController extends BaseController {
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

  // ==================== 文章图片下载 ====================

  /// 是否正在下载图片
  final isDownloadingImages = false.obs;

  /// 下载进度：当前已处理的文章数量
  final downloadProgress = 0.obs;

  /// 下载进度：总文章数量
  final downloadTotal = 0.obs;

  /// 下载成功的数量
  final downloadSuccessCount = 0.obs;

  /// 下载失败的数量
  final downloadFailCount = 0.obs;

  /// 下载缺失的文章封面图片
  Future<void> downloadMissingArticleImages() async {
    if (isDownloadingImages.value) {
      UIUtils.showError('正在下载中，请稍候...');
      return;
    }

    logger.i('[SettingsController] 开始下载缺失的文章封面图片');

    // 重置状态
    _resetDownloadState();
    isDownloadingImages.value = true;

    try {
      // 获取所有文章
      final articles = ArticleRepository.i.all();
      downloadTotal.value = articles.length;

      logger.i('[SettingsController] 共找到 ${articles.length} 篇文章，开始检查封面图片');

      // 筛选需要下载的文章
      final articlesToDownload = _filterArticlesNeedingDownload(articles);

      if (articlesToDownload.isEmpty) {
        logger.i('[SettingsController] 所有文章封面图片都已存在，无需下载');
        UIUtils.showSuccess('所有图片都已下载完成');
        isDownloadingImages.value = false;
        return;
      }

      logger.i('[SettingsController] 发现 ${articlesToDownload.length} 篇文章需要下载封面图片');

      // 逐个下载
      await _downloadArticleImages(articlesToDownload);

      _showDownloadResult();
    } catch (e) {
      logger.e('[SettingsController] 下载文章图片失败: $e');
      UIUtils.showError('下载失败: $e');
    } finally {
      isDownloadingImages.value = false;
    }
  }

  /// 重置下载状态
  void _resetDownloadState() {
    downloadProgress.value = 0;
    downloadTotal.value = 0;
    downloadSuccessCount.value = 0;
    downloadFailCount.value = 0;
  }

  /// 筛选需要下载的文章
  List<ArticleModel> _filterArticlesNeedingDownload(List<ArticleModel> articles) {
    final articlesToDownload = <ArticleModel>[];

    for (final article in articles) {
      downloadProgress.value++;

      // 检查是否有封面图片URL
      final coverImageUrl = article.coverImageUrl;
      if (coverImageUrl == null || coverImageUrl.isEmpty) {
        continue;
      }

      // 检查本地文件是否存在
      final coverImage = article.coverImage;
      if (coverImage != null && coverImage.isNotEmpty) {
        final resolvedPath = FileService.i.resolveLocalMediaPath(coverImage);
        if (File(resolvedPath).existsSync()) {
          continue;
        }
      }

      articlesToDownload.add(article);
    }

    // 重置进度用于下载阶段
    downloadProgress.value = 0;
    downloadTotal.value = articlesToDownload.length;

    return articlesToDownload;
  }

  /// 下载文章封面图片
  Future<void> _downloadArticleImages(List<ArticleModel> articles) async {
    for (final article in articles) {
      downloadProgress.value++;

      try {
        final imageUrl = article.coverImageUrl!;
        logger.d('[SettingsController] 下载图片: $imageUrl');

        final imagePath = await HttpService.i.downloadImage(imageUrl);

        if (imagePath.isNotEmpty) {
          article.coverImage = imagePath;
          ArticleRepository.i.updateModel(article);
          downloadSuccessCount.value++;
          logger.d('[SettingsController] 图片下载成功: ${article.id}');
        } else {
          downloadFailCount.value++;
          logger.w('[SettingsController] 图片下载失败: ${article.id}');
        }
      } catch (e) {
        downloadFailCount.value++;
        logger.w('[SettingsController] 下载图片异常: ${article.id}, 错误: $e');
      }
    }
  }

  /// 显示下载结果
  void _showDownloadResult() {
    final message = '下载完成: 成功 ${downloadSuccessCount.value} 张, 失败 ${downloadFailCount.value} 张';
    logger.i('[SettingsController] $message');
    UIUtils.showSuccess(message);
  }
}
