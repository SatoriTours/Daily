import 'dart:io';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:dio/dio.dart' show DioException, Options;
import 'package:open_file/open_file.dart';
import 'package:permission_handler/permission_handler.dart';

import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/config/app_config.dart';
import 'package:daily_satori/app/services/service_base.dart';

class AppUpgradeService extends AppService {
  @override
  ServicePriority get priority => ServicePriority.low;
  // 单例模式
  AppUpgradeService._();
  static final AppUpgradeService _instance = AppUpgradeService._();
  static AppUpgradeService get i => _instance;

  // 常量已迁移至 UrlConfig
  static String get _githubReleaseApi => UrlConfig.githubReleaseApi;
  static String get _githubReleaseApiMirror => UrlConfig.githubReleaseApiMirror;

  // 版本信息
  late String _currentVersion;
  late String _latestVersion;
  late String _downloadURL;

  String get downloadURL => _downloadURL;
  bool get needUpgrade => _currentVersion != _latestVersion;

  @override
  Future<void> init() async {}

  // 检查并下载新版本(带UI提示)
  Future<void> checkAndDownload() async {
    DialogUtils.showLoading(tips: '正在检查更新...');
    final hasUpdate = await check();
    DialogUtils.hideLoading();

    if (hasUpdate) {
      await _downAndInstallApp();
    } else {
      UIUtils.showSuccess('当前已是最新版本');
    }
  }

  // 后台检查并下载新版本
  Future<void> checkAndDownloadInBackground() async {
    if (!AppInfoUtils.isProduction) return;
    if (await check()) {
      await _downAndInstallApp();
    }
  }

  // 请求安装权限
  Future<bool> _requestInstallPermission() async {
    final status = await Permission.requestInstallPackages.status;
    if (status.isDenied) {
      final result = await Permission.requestInstallPackages.request();
      return result.isGranted;
    }
    return true;
  }

  // 检查版本
  Future<bool> check() async {
    try {
      await Future.wait([_getCurrentVersion(), _getLatestVersionFromGithub()]);
      logger.i(
        "当前版本: $_currentVersion, 最新版本: $_latestVersion, 需要更新: $needUpgrade",
      );

      // 开发模式下永远显示有更新，便于测试
      if (!AppInfoUtils.isProduction) {
        logger.i("[开发模式] 强制显示有新版本");
        return true;
      }

      return needUpgrade;
    } catch (e) {
      logger.e("检查版本失败: $e");

      return false;
    }
  }

  // 下载并安装新版本
  Future<void> _downAndInstallApp() async {
    // 生产模式下检查是否需要更新，开发模式下跳过此检查
    if (AppInfoUtils.isProduction && !needUpgrade) return;

    await DialogUtils.showConfirm(
      title: '检测到新版本',
      message: '当前版本 [$_currentVersion], 最新版本 [$_latestVersion]\n请确认是否下载更新',
      onConfirm: () async {
        // 仅生产环境的 Android 需要安装未知来源权限
        if (AppInfoUtils.isProduction && Platform.isAndroid) {
          if (!await _requestInstallPermission()) {
            UIUtils.showError('需要安装权限才能继续');
            return;
          }
        }

        DialogUtils.showDownloadProgress(
          title: '正在下载更新',
          initialText: '准备下载...',
        );
        try {
          final appFilePath = await HttpService.i.downloadFileWithProgress(
            _downloadURL,
            onProgress: (received, total) {
              DialogUtils.updateDownloadProgress(received, total);
            },
          );
          logger.i("下载文件到: $appFilePath");

          if (appFilePath.isEmpty) {
            DialogUtils.hideDownloadProgress();
            UIUtils.showError('下载失败，请检查网络后重试');
            return;
          }

          // 非生产环境：只下载不安装
          if (!AppInfoUtils.isProduction) {
            DialogUtils.hideDownloadProgress();
            UIUtils.showSuccess('安装包已下载到: $appFilePath\n调试版本请手动安装');
            return;
          }

          // 生产环境：下载后自动打开安装
          final isApk = appFilePath.toLowerCase().endsWith('.apk');
          final result = await OpenFile.open(
            appFilePath,
            type: isApk ? 'application/vnd.android.package-archive' : null,
          );
          DialogUtils.hideDownloadProgress();

          logger.i("打开文件结果: ${result.type} ${result.message}");
          if (result.type != ResultType.done) {
            UIUtils.showError('打开安装文件失败: ${result.message}');
          } else {
            UIUtils.showSuccess('安装包已准备就绪，请按提示完成安装');
          }
        } catch (e) {
          DialogUtils.hideDownloadProgress();
          logger.e("下载或安装失败: $e");
          UIUtils.showError("更新失败，请稍后重试");
        }
      },
    );
  }

  // 获取当前版本号
  Future<void> _getCurrentVersion() async {
    _currentVersion = _normalizeVersion(await AppInfoUtils.getVersion());
  }

  // 从Github获取最新版本信息
  Future<void> _getLatestVersionFromGithub() async {
    try {
      final data = await _fetchLatestReleaseJsonWithFallback();
      _latestVersion = _normalizeVersion(data['tag_name'] as String);

      // 优先选择 APK 资源，其次回退到第一个资源
      final assets = (data['assets'] as List?) ?? const [];
      if (assets.isEmpty) {
        throw Exception('发布中未找到可下载资源');
      }
      String? apkUrl;
      for (final a in assets) {
        final url = a['browser_download_url'] as String?;
        if (url != null && url.toLowerCase().endsWith('.apk')) {
          apkUrl = url;
          break;
        }
      }
      // 直接使用 GitHub 原始下载链接
      _downloadURL = apkUrl ?? (assets.first['browser_download_url'] as String);
    } catch (e) {
      logger.e("获取Github版本信息失败: $e");
      rethrow;
    }
  }

  // 依次尝试官方与镜像 API，返回 release JSON
  Future<Map<String, dynamic>> _fetchLatestReleaseJsonWithFallback() async {
    Map<String, String> buildHeaders() {
      final base = <String, String>{
        'User-Agent':
            'DailyApp/UpgradeChecker (+https://github.com/SatoriTours/Daily)',
        'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
      };

      // 可选：支持通过环境变量提供 GitHub Token，以避免 403（未认证限流）。
      // Windows/macOS/Linux 桌面端可生效；移动端通常无此环境变量。
      final token =
          Platform.environment['GITHUB_TOKEN'] ??
          Platform.environment['DS_GITHUB_TOKEN'];
      if (token != null && token.trim().isNotEmpty) {
        base['Authorization'] = 'Bearer ${token.trim()}';
      }
      return base;
    }

    Future<Map<String, dynamic>> doGet(String url) async {
      final resp = await HttpService.i.get(
        url,
        options: Options(headers: buildHeaders()),
      );
      if (resp.statusCode == 200 && resp.data is Map<String, dynamic>) {
        return Map<String, dynamic>.from(resp.data);
      }
      throw Exception('获取版本信息失败: ${resp.statusCode}');
    }

    // 尝试主站
    try {
      return await doGet(_githubReleaseApi);
    } catch (e) {
      // 403 多见于未携带 Token 导致的未认证限流；此时降级到镜像，不再用告警级别噪声提示。
      if (e is DioException) {
        final code = e.response?.statusCode;
        if (code == 403) {
          logger.i("GitHub 主站返回 403（可能触发未认证限流），已自动切换镜像获取版本信息。");
        } else {
          logger.w("GitHub 主站获取失败（HTTP $code），尝试镜像: ${e.message}");
        }
      } else {
        logger.w("GitHub 主站获取失败，尝试镜像: $e");
      }
      // 尝试镜像
      return await doGet(_githubReleaseApiMirror);
    }
  }

  // 版本标准化（去掉前缀 v/V）
  String _normalizeVersion(String v) {
    if (v.isEmpty) return v;
    final trimmed = v.trim();
    if (trimmed.startsWith('v') || trimmed.startsWith('V')) {
      return trimmed.substring(1);
    }
    return trimmed;
  }
}
