import 'dart:io';
import 'package:daily_satori/app/config/app_config.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/service_base.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:dio/dio.dart' show Options;
import 'package:open_file/open_file.dart';
import 'package:permission_handler/permission_handler.dart';

/// 应用升级服务
class AppUpgradeService extends AppService {
  AppUpgradeService._();
  static final AppUpgradeService i = AppUpgradeService._();

  @override
  final ServicePriority priority = ServicePriority.low;

  String _currentVersion = '';
  String _latestVersion = '';
  String _downloadURL = '';

  String get downloadURL => _downloadURL;
  bool get needUpgrade => _currentVersion != _latestVersion;

  @override
  Future<void> init() async {}

  /// 检查并下载更新（带 UI 提示）
  Future<void> checkAndDownload() async {
    DialogUtils.showLoading(tips: '正在检查更新...');
    final hasUpdate = await check();
    DialogUtils.hideLoading();

    if (hasUpdate) {
      await _downloadAndInstall();
    } else {
      UIUtils.showSuccess('当前已是最新版本');
    }
  }

  /// 后台检查更新（仅生产环境）
  Future<void> checkAndDownloadInBackground() async {
    if (!AppInfoUtils.isProduction) return;
    if (await check()) await _downloadAndInstall();
  }

  /// 检查版本更新
  Future<bool> check() async {
    try {
      await Future.wait([_fetchCurrentVersion(), _fetchLatestVersion()]);
      logger.i('当前版本: $_currentVersion, 最新版本: $_latestVersion, 需要更新: $needUpgrade');

      // 开发模式强制显示有更新
      if (!AppInfoUtils.isProduction) return true;
      return needUpgrade;
    } catch (e) {
      logger.e('检查版本失败: $e');
      return false;
    }
  }

  // ========== 内部方法 ==========

  Future<void> _fetchCurrentVersion() async {
    _currentVersion = _normalizeVersion(await AppInfoUtils.getVersion());
  }

  Future<void> _fetchLatestVersion() async {
    final data = await _fetchReleaseJson();
    _latestVersion = _normalizeVersion(data['tag_name'] as String);
    _downloadURL = _findApkUrl(data['assets'] as List?);
  }

  String _findApkUrl(List? assets) {
    if (assets == null || assets.isEmpty) {
      throw Exception('发布中未找到可下载资源');
    }
    // 优先返回 APK 下载链接
    for (final a in assets) {
      final url = a['browser_download_url'] as String?;
      if (url != null && url.toLowerCase().endsWith('.apk')) {
        return url;
      }
    }
    // 回退到第一个资源
    return assets.first['browser_download_url'] as String;
  }

  Future<Map<String, dynamic>> _fetchReleaseJson() async {
    final headers = _buildHeaders();

    Future<Map<String, dynamic>> fetch(String url) async {
      final resp = await HttpService.i.get(url, options: Options(headers: headers));
      if (resp.statusCode == 200 && resp.data is Map<String, dynamic>) {
        return Map<String, dynamic>.from(resp.data);
      }
      throw Exception('获取版本信息失败: ${resp.statusCode}');
    }

    // 优先主站，失败则回退到镜像
    try {
      return await fetch(UrlConfig.githubReleaseApi);
    } catch (e) {
      logger.w('GitHub 主站获取失败，尝试镜像: $e');
      return await fetch(UrlConfig.githubReleaseApiMirror);
    }
  }

  Map<String, String> _buildHeaders() {
    final headers = <String, String>{
      'User-Agent': 'DailyApp/UpgradeChecker (+https://github.com/SatoriTours/Daily)',
      'Accept': 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
    };

    // 支持通过环境变量提供 GitHub Token
    final token = Platform.environment['GITHUB_TOKEN'] ?? Platform.environment['DS_GITHUB_TOKEN'];
    if (token != null && token.trim().isNotEmpty) {
      headers['Authorization'] = 'Bearer ${token.trim()}';
    }
    return headers;
  }

  Future<void> _downloadAndInstall() async {
    if (AppInfoUtils.isProduction && !needUpgrade) return;

    await DialogUtils.showConfirm(
      title: '检测到新版本',
      message: '当前版本 [$_currentVersion], 最新版本 [$_latestVersion]\n请确认是否下载更新',
      onConfirm: _startDownload,
    );
  }

  Future<void> _startDownload() async {
    // Android 生产环境需要安装权限
    if (AppInfoUtils.isProduction && Platform.isAndroid) {
      if (!await _requestInstallPermission()) {
        UIUtils.showError('需要安装权限才能继续');
        return;
      }
    }

    DialogUtils.showDownloadProgress(title: '正在下载更新', initialText: '准备下载...');

    try {
      final filePath = await HttpService.i.downloadFileWithProgress(
        _downloadURL,
        onProgress: (received, total) => DialogUtils.updateDownloadProgress(received, total),
      );

      if (filePath.isEmpty) {
        DialogUtils.hideDownloadProgress();
        UIUtils.showError('下载失败，请检查网络后重试');
        return;
      }

      // 开发模式只下载不安装
      if (!AppInfoUtils.isProduction) {
        DialogUtils.hideDownloadProgress();
        UIUtils.showSuccess('安装包已下载到: $filePath\n调试版本请手动安装');
        return;
      }

      // 生产模式下载后自动打开安装
      final isApk = filePath.toLowerCase().endsWith('.apk');
      final result = await OpenFile.open(
        filePath,
        type: isApk ? 'application/vnd.android.package-archive' : null,
      );
      DialogUtils.hideDownloadProgress();

      if (result.type != ResultType.done) {
        UIUtils.showError('打开安装文件失败: ${result.message}');
      } else {
        UIUtils.showSuccess('安装包已准备就绪，请按提示完成安装');
      }
    } catch (e) {
      DialogUtils.hideDownloadProgress();
      logger.e('下载或安装失败: $e');
      UIUtils.showError('更新失败，请稍后重试');
    }
  }

  Future<bool> _requestInstallPermission() async {
    final status = await Permission.requestInstallPackages.status;
    if (status.isDenied) {
      final result = await Permission.requestInstallPackages.request();
      return result.isGranted;
    }
    return true;
  }

  String _normalizeVersion(String v) {
    if (v.isEmpty) return v;
    final trimmed = v.trim();
    if (trimmed.startsWith('v') || trimmed.startsWith('V')) {
      return trimmed.substring(1);
    }
    return trimmed;
  }
}
