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
@freezed
abstract class SettingsControllerState with _$SettingsControllerState {
  const factory SettingsControllerState({
    @Default('') String webServiceAddress,
    @Default('') String webAccessUrl,
    @Default(true) bool isPageLoading,
    @Default('') String appVersion,
    @Default(false) bool isWebSocketConnected,
    @Default(false) bool isDownloadingImages,
    @Default(0) int downloadProgress,
    @Default(0) int downloadTotal,
    @Default(0) int downloadSuccessCount,
    @Default(0) int downloadFailCount,
  }) = _SettingsControllerState;
}

/// SettingsController Provider
@riverpod
class SettingsController extends _$SettingsController {
  @override
  SettingsControllerState build() {
    Future.microtask(() => _initialize());
    return const SettingsControllerState();
  }

  Future<void> _initialize() async {
    await _updateWebServerAddress();
    _updateWebAccessUrl();
    await _loadAppVersion();
    _updateWebSocketStatus();
    _setupWebSocketListener();
  }

  void _setupWebSocketListener() {
    void onConnectionChanged() {
      state = state.copyWith(isWebSocketConnected: WebService.i.webSocketTunnel.isConnected.value);
    }

    WebService.i.webSocketTunnel.isConnected.addListener(onConnectionChanged);
    ref.onDispose(() => WebService.i.webSocketTunnel.isConnected.removeListener(onConnectionChanged));
  }

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

  Future<void> _updateWebServerAddress() async {
    final address = await WebService.i.getAppAddress();
    state = state.copyWith(webServiceAddress: address);
  }

  void _updateWebAccessUrl() {
    state = state.copyWith(webAccessUrl: WebService.i.webSocketTunnel.getWebAccessUrl());
  }

  void _updateWebSocketStatus() {
    state = state.copyWith(isWebSocketConnected: WebService.i.webSocketTunnel.isConnected.value);
  }

  Future<void> selectBackupDirectory() async {
    final permission = await Permission.manageExternalStorage.request();
    if (!permission.isGranted) {
      UIUtils.showError('请授予应用管理外部存储的权限');
      return;
    }

    final selectedDirectory = await FilePicker.platform.getDirectoryPath(
      dialogTitle: '选择备份文件夹',
      initialDirectory: SettingRepository.i.getSetting(SettingService.backupDirKey),
    );

    if (selectedDirectory != null) {
      logger.i('[SettingsController] 选择备份目录: $selectedDirectory');
      SettingRepository.i.saveSetting(SettingService.backupDirKey, selectedDirectory);
    }
  }

  void copyWebServiceAddress() => Clipboard.setData(ClipboardData(text: state.webServiceAddress));

  void copyWebAccessUrl() => Clipboard.setData(ClipboardData(text: state.webAccessUrl));

  void reAnalyzeAllWebpages() {
    logger.i('[SettingsController] 重新分析所有网页');
    ArticleRepository.i.updateEmptyStatusToPending();
  }

  String getWebServerPassword() => SettingRepository.i.getSetting(SettingService.webServerPasswordKey);

  Future<void> saveWebServerPassword(String password) async {
    try {
      SettingRepository.i.saveSetting(SettingService.webServerPasswordKey, password);
      UIUtils.showSuccess('密码设置成功');
    } catch (e) {
      logger.e('保存密码失败: $e');
      UIUtils.showError('保存密码失败: $e');
    }
  }

  Future<void> restartWebService() async {
    try {
      DialogUtils.showLoading(tips: '正在重启Web服务...');
      await WebService.i.webSocketTunnel.disconnect();
      await WebService.i.init();
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

  Future<void> downloadMissingArticleImages() async {
    if (state.isDownloadingImages) {
      UIUtils.showError('正在下载中，请稍候...');
      return;
    }

    logger.i('[SettingsController] 开始下载缺失的文章封面图片');
    state = state.copyWith(
      isDownloadingImages: true,
      downloadProgress: 0,
      downloadTotal: 0,
      downloadSuccessCount: 0,
      downloadFailCount: 0,
    );

    try {
      final articles = ArticleRepository.i.all();
      state = state.copyWith(downloadTotal: articles.length);

      final articlesToDownload = _filterArticlesNeedingDownload(articles);

      if (articlesToDownload.isEmpty) {
        UIUtils.showSuccess('所有图片都已下载完成');
        state = state.copyWith(isDownloadingImages: false);
        return;
      }

      logger.i('[SettingsController] 发现 ${articlesToDownload.length} 篇文章需要下载封面图片');
      await _downloadArticleImages(articlesToDownload);
      _showDownloadResult();
    } catch (e) {
      logger.e('[SettingsController] 下载文章图片失败: $e');
      UIUtils.showError('下载失败: $e');
    } finally {
      state = state.copyWith(isDownloadingImages: false);
    }
  }

  List<ArticleModel> _filterArticlesNeedingDownload(List<ArticleModel> articles) {
    final result = <ArticleModel>[];

    for (final article in articles) {
      state = state.copyWith(downloadProgress: state.downloadProgress + 1);

      final coverImageUrl = article.coverImageUrl;
      if (coverImageUrl == null || coverImageUrl.isEmpty) continue;

      final coverImage = article.coverImage;
      if (coverImage != null && coverImage.isNotEmpty) {
        final resolvedPath = FileService.i.resolveLocalMediaPath(coverImage);
        if (File(resolvedPath).existsSync()) continue;
      }

      result.add(article);
    }

    state = state.copyWith(downloadProgress: 0, downloadTotal: result.length);
    return result;
  }

  Future<void> _downloadArticleImages(List<ArticleModel> articles) async {
    for (final article in articles) {
      state = state.copyWith(downloadProgress: state.downloadProgress + 1);

      try {
        final imagePath = await HttpService.i.downloadImage(article.coverImageUrl!);

        if (imagePath.isNotEmpty) {
          article.coverImage = imagePath;
          ArticleRepository.i.updateModel(article);
          state = state.copyWith(downloadSuccessCount: state.downloadSuccessCount + 1);
        } else {
          state = state.copyWith(downloadFailCount: state.downloadFailCount + 1);
        }
      } catch (e) {
        state = state.copyWith(downloadFailCount: state.downloadFailCount + 1);
        logger.w('[SettingsController] 下载图片异常: ${article.id}, 错误: $e');
      }
    }
  }

  void _showDownloadResult() {
    final message = '下载完成: 成功 ${state.downloadSuccessCount} 张, 失败 ${state.downloadFailCount} 张';
    logger.i('[SettingsController] $message');
    UIUtils.showSuccess(message);
  }
}
