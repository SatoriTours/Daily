/// Settings Controller Provider
///
/// 设置页控制器，管理应用设置、Web服务、图片下载等功能。

library;

import 'dart:io';

import 'package:flutter/services.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/index.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:file_picker/file_picker.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:permission_handler/permission_handler.dart';

part 'settings_controller_provider.freezed.dart';
part 'settings_controller_provider.g.dart';

/// SettingsController 状态
///
/// 包含设置页的所有状态数据
@freezed
abstract class SettingsControllerState with _$SettingsControllerState {
  /// 构造函数
  const factory SettingsControllerState({
    /// Web服务地址
    @Default('') String webServiceAddress,

    /// Web访问URL
    @Default('') String webAccessUrl,

    /// 是否正在加载页面
    @Default(true) bool isPageLoading,

    /// 应用版本号
    @Default('') String appVersion,

    /// WebSocket连接状态
    @Default(false) bool isWebSocketConnected,

    /// 是否正在下载图片
    @Default(false) bool isDownloadingImages,

    /// 下载进度：当前已处理的文章数量
    @Default(0) int downloadProgress,

    /// 下载进度：总文章数量
    @Default(0) int downloadTotal,

    /// 下载成功的数量
    @Default(0) int downloadSuccessCount,

    /// 下载失败的数量
    @Default(0) int downloadFailCount,
  }) = _SettingsControllerState;
}

/// SettingsController Provider
///
/// 管理设置页的状态和逻辑
@riverpod
class SettingsController extends _$SettingsController {
  // ========================================================================
  // 状态管理
  // ========================================================================

  @override
  SettingsControllerState build() {
    // 初始化时加载数据
    Future.microtask(() => _initialize());
    return const SettingsControllerState();
  }

  /// 初始化设置页数据
  Future<void> _initialize() async {
    await _updateWebServerAddress();
    _updateWebAccessUrl();
    await _loadAppVersion();
    _updateWebSocketStatus();

    // 监听WebSocket连接状态变化
    void onConnectionChanged() {
      state = state.copyWith(isWebSocketConnected: WebService.i.webSocketTunnel.isConnected.value);
    }

    WebService.i.webSocketTunnel.isConnected.addListener(onConnectionChanged);
    ref.onDispose(() {
      WebService.i.webSocketTunnel.isConnected.removeListener(onConnectionChanged);
    });
  }

  // ========================================================================
  // 数据加载
  // ========================================================================

  /// 加载应用版本信息
  Future<void> _loadAppVersion() async {
    state = state.copyWith(isPageLoading: true);
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      state = state.copyWith(appVersion: '${packageInfo.version}+${packageInfo.buildNumber}', isPageLoading: false);
    } catch (e) {
      logger.e('加载应用版本信息失败: $e');
      state = state.copyWith(appVersion: '未知版本', isPageLoading: false);
    }
  }

  /// 更新Web服务地址
  Future<void> _updateWebServerAddress() async {
    final address = await WebService.i.getAppAddress();
    state = state.copyWith(webServiceAddress: address);
  }

  /// 更新Web访问URL
  void _updateWebAccessUrl() {
    final url = WebService.i.webSocketTunnel.getWebAccessUrl();
    state = state.copyWith(webAccessUrl: url);
  }

  /// 更新WebSocket连接状态
  void _updateWebSocketStatus() {
    final connected = WebService.i.webSocketTunnel.isConnected.value;
    state = state.copyWith(isWebSocketConnected: connected);
  }

  // ========================================================================
  // 设置操作
  // ========================================================================

  /// 选择备份目录
  Future<void> selectBackupDirectory() async {
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
    logger.i('[SettingsController] 复制Web服务地址: ${state.webServiceAddress}');
    Clipboard.setData(ClipboardData(text: state.webServiceAddress));
  }

  /// 复制Web访问URL到剪贴板
  void copyWebAccessUrl() {
    logger.i('[SettingsController] 复制Web访问URL: ${state.webAccessUrl}');
    Clipboard.setData(ClipboardData(text: state.webAccessUrl));
  }

  /// 重新用AI分析所有网页
  void reAnalyzeAllWebpages() {
    logger.i('[SettingsController] 重新分析所有网页');
    // 获取所有文章
    ArticleRepository.i.updateEmptyStatusToPending();
    // ArticleRepository.i.updateAllStatusToCompleted();
  }

  // ========================================================================
  // Web服务器设置
  // ========================================================================

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
      await _updateWebServerAddress();
      _updateWebAccessUrl();

      DialogUtils.hideLoading();
      UIUtils.showSuccess('Web服务已重启');
    } catch (e) {
      logger.e('重启Web服务失败: $e');
      DialogUtils.hideLoading();
      UIUtils.showError('重启Web服务失败: $e');
    }
  }

  // ========================================================================
  // 文章图片下载
  // ========================================================================

  /// 下载缺失的文章封面图片
  Future<void> downloadMissingArticleImages() async {
    if (state.isDownloadingImages) {
      UIUtils.showError('正在下载中，请稍候...');
      return;
    }

    logger.i('[SettingsController] 开始下载缺失的文章封面图片');

    // 重置状态
    _resetDownloadState();
    state = state.copyWith(isDownloadingImages: true);

    try {
      // 获取所有文章
      final articles = ArticleRepository.i.all();
      state = state.copyWith(downloadTotal: articles.length);

      logger.i('[SettingsController] 共找到 ${articles.length} 篇文章，开始检查封面图片');

      // 筛选需要下载的文章
      final articlesToDownload = _filterArticlesNeedingDownload(articles);

      if (articlesToDownload.isEmpty) {
        logger.i('[SettingsController] 所有文章封面图片都已存在，无需下载');
        UIUtils.showSuccess('所有图片都已下载完成');
        state = state.copyWith(isDownloadingImages: false);
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
      state = state.copyWith(isDownloadingImages: false);
    }
  }

  /// 重置下载状态
  void _resetDownloadState() {
    state = state.copyWith(downloadProgress: 0, downloadTotal: 0, downloadSuccessCount: 0, downloadFailCount: 0);
  }

  /// 筛选需要下载的文章
  List<ArticleModel> _filterArticlesNeedingDownload(List<ArticleModel> articles) {
    final articlesToDownload = <ArticleModel>[];

    for (final article in articles) {
      final newProgress = state.downloadProgress + 1;
      state = state.copyWith(downloadProgress: newProgress);

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
    state = state.copyWith(downloadProgress: 0, downloadTotal: articlesToDownload.length);

    return articlesToDownload;
  }

  /// 下载文章封面图片
  Future<void> _downloadArticleImages(List<ArticleModel> articles) async {
    for (final article in articles) {
      final newProgress = state.downloadProgress + 1;
      state = state.copyWith(downloadProgress: newProgress);

      try {
        final imageUrl = article.coverImageUrl!;
        logger.d('[SettingsController] 下载图片: $imageUrl');

        final imagePath = await HttpService.i.downloadImage(imageUrl);

        if (imagePath.isNotEmpty) {
          article.coverImage = imagePath;
          ArticleRepository.i.updateModel(article);
          state = state.copyWith(downloadSuccessCount: state.downloadSuccessCount + 1);
          logger.d('[SettingsController] 图片下载成功: ${article.id}');
        } else {
          state = state.copyWith(downloadFailCount: state.downloadFailCount + 1);
          logger.w('[SettingsController] 图片下载失败: ${article.id}');
        }
      } catch (e) {
        state = state.copyWith(downloadFailCount: state.downloadFailCount + 1);
        logger.w('[SettingsController] 下载图片异常: ${article.id}, 错误: $e');
      }
    }
  }

  /// 显示下载结果
  void _showDownloadResult() {
    final message = '下载完成: 成功 ${state.downloadSuccessCount} 张, 失败 ${state.downloadFailCount} 张';
    logger.i('[SettingsController] $message');
    UIUtils.showSuccess(message);
  }
}
